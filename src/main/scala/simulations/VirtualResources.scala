import com.excilys.ebi.gatling.core.Predef._
import com.excilys.ebi.gatling.http.Headers.Names._
import com.excilys.ebi.gatling.http.Predef._
import AbiquoAPI._
import AdminLogin._
import com.excilys.ebi.gatling.http.check.HttpCheck
import org.glassfish.grizzly.http.util.HttpStatus._
import com.excilys.ebi.gatling.http.request.builder.AbstractHttpRequestWithBodyBuilder
import com.excilys.ebi.gatling.core.structure.ChainBuilder
import jodd.util.StringUtil
import com.ning.http.client._

import bootstrap._
import akka.util.duration._

class VirtualResources extends Simulation {

    // configure

    val baseUrl  = System.getProperty("baseUrl","http://localhost:80")
    val numUsers = Integer.getInteger("numUsers", 1)
    val rampTime = Integer.getInteger("rampTime", 1).toLong
    val userLoop = Integer.getInteger("userLoop", 1)
    val checkPollingPause = 5

    // end configure

    val createVapp = exec(http("POST_VAPP")
            post(POST_VAPP) header(ACCEPT, MT_VAPP) header(CONTENT_TYPE, MT_VAPP)
            fileBody("vapp.xml", vappContent)
            basicAuth("${loginuser}","${loginpassword}")
            check( status is(CREATED), captureErrors("POST_VAPP"), captureVirtualapplianceId)
        )
        //.doIf( (s:Session) => exitIfNoDefined(s, "virtualapplianceId"), chain.pause(0,1))

    val deleteVapp = exec(http("DEL_VAPP")
            delete(DEL_VAPP) header(ACCEPT, MT_XML)
            basicAuth("${loginuser}","${loginpassword}")
            check( status is(NO_CONTENT) )
            //captureErrors("DEL_VAPP") SAXParseException: Premature end of file.
        )

    val updateVmState = exec(http("GET_VM_STATE")
            get(GET_VM_STATE) header(ACCEPT, MT_VMSTATE)
            basicAuth("${loginuser}","${loginpassword}")
            check( status is(OK), captureCurrentVmState )
        )

    val undeployVm = exec(http("ACTION_VM_UNDEPLOY")
            post(ACTION_VM_UNDEPLOY) header(ACCEPT, MT_ACCEPTED) header(CONTENT_TYPE, MT_VMTASK)
            fileBody("vmtask.xml", vmtaskContent)
            basicAuth("${loginuser}","${loginpassword}")
            check( status is(ACCEPTED) )
        )
        // don't wait (undeplyVapp)

    val deleteVm = exec(http("DEL_VM")
            delete(DEL_VM)
            basicAuth("${loginuser}","${loginpassword}")
            check( status is(NO_CONTENT) )
        )

    val deployVm = exec(http("ACTION_VM_DEPLOY")
            post(ACTION_VM_DEPLOY) header(ACCEPT, MT_ACCEPTED) header(CONTENT_TYPE, MT_VMTASK)
            fileBody("vmtask.xml", vmtaskContent)
            basicAuth("${loginuser}","${loginpassword}")
            check( status is(ACCEPTED) )
        )
        // don't wait (deplyVapp)

    val checkCurrentVmNeedUndeploy = 
        exec( (s:Session) => clearCurrentVmState(s) )
        .exec(updateVmState)
        .doIf( (s:Session) => isCurrentVirtualMachineState(s, Set("ON", "OFF", "CONFIGURED"))) {
            exec( (s:Session) => actionRetry("forceUndeployVm", s))
            .exec(undeployVm)
        }    
        // .pause(1)
        // .exec(updateVmState)
        // .doIf( (s:Session) => isCurrentVirtualMachineState(s, Set("NOT_ALLOCATED"))) {
        //     exec( (s:Session) => actionRetry("delVmUndeploy", s))
        //     .exec(deleteVm)
        // }

    val checkCurrentVmNeedDeploy = 
        exec( (s:Session) => clearCurrentVmState(s) )
        .exec(updateVmState)
        .doIf( (s:Session) => isCurrentVirtualMachineState(s, Set("NOT_ALLOCATED", "OFF"))) {
            exec( (s:Session) => actionRetry("forceDeployVm", s))
            .exec(deployVm)
        }
//        .doIf( (s:Session) => isCurrentVirtualMachineState(s, Set("UNKNOWN"))) {
//            exec( (s:Session) => actionRetry("delVmDeploy", s))
//            .exec(deleteVm)
//        }

    val deployAllVms = repeat("${numVirtualMachinesInVapp}", "numVirtualmachine") {
            exec( (s:Session) => setCurrentVmId(s) )
            .exec(checkCurrentVmNeedDeploy)
        }

    val undeployAllVms = repeat("${numVirtualMachinesInVapp}" ,"numVirtualmachine") {
            exec( (s:Session) => setCurrentVmId(s) )
            .exec(checkCurrentVmNeedUndeploy)
        }

    val createVm = exec(http("POST_VM")
            post(POST_VM) header(ACCEPT, MT_VM) header(CONTENT_TYPE, MT_VM_NODE)
            fileBody("vm.xml", vmContent)
            basicAuth("${loginuser}","${loginpassword}")
            check( status is(CREATED) saveAs("currentVmCreated"), captureErrors("POST_VM"), captureCurrentVirtualmachineId )
        )
        .exec( (s:Session) =>  saveCurrentVirtualmachineId(s))

    val createVmWithRetry = 
        exec( (s:Session) =>  clearCurrentVmCreation(s))
        .asLongAs( (s:Session) => needCreateVirtualMachineRetry(s)) {
            pause(1) // before create
            .exec(createVm)
            .exec( (s:Session) => actionRetry("createVm", s))
        }

    val updateVappState = exec(http("GET_VAPP_STATE")
            get(GET_VAPP_STATE) header(ACCEPT, MT_VAPPSTATE)
            basicAuth("${loginuser}","${loginpassword}")
            check( status is(OK), captureVirtualapplianceState )
        )

    val deployVapp = exec(http("ACTION_VAPP_DEPLOY")
            post(ACTION_VAPP_DEPLOY) header(ACCEPT, MT_ACCEPTED) header(CONTENT_TYPE, MT_VMTASK)
            fileBody("vmtask.xml", vmtaskContent)
            basicAuth("${loginuser}","${loginpassword}")
            check( status is(ACCEPTED) saveAs("acceptedDeply"), captureErrors("ACTION_VAPP_DEPLOY") )
        )

    val deployVappHard = 
        exec( (s:Session) =>  deployStartTime(s))
        .exec( (s:Session) => s.removeAttribute("acceptedDeply"))
        .asLongAs( (s:Session) => needAcceptedDeplyRetry(s)) {
            pause(0, 3)
            .exec( (s:Session) => actionRetry("deploy", s) )
            .exec(deployVapp)
        }
        .exec(updateVappState)
        .asLongAs((s:Session) => !isVirtualApplianceState(s, Set("DEPLOYED"))) { //, "UNKNOWN"(
            pause(checkPollingPause)
            .exec(updateVappState)
            .doIf( (s:Session) => isVirtualApplianceState(s, Set("NEEDS_SYNC", "UNKNOWN"))){
                exec( (s:Session) => actionRetry("deploy", s))
                .exec( (s:Session) => logVirtualApplianceState("check-D",s) )
                .exec(deployAllVms)
                .exec(updateVappState)
            }
        }
        .exec( (s:Session) => deployStopTime(s) )
        .exec( (s:Session) => logVirtualApplianceState("deploy",s) )

    val undeployVapp = exec(http("ACTION_VAPP_UNDEPLOY")
            post(ACTION_VAPP_UNDEPLOY) header(ACCEPT, MT_ACCEPTED) header(CONTENT_TYPE, MT_VMTASK)
            fileBody("vmtask.xml", vmtaskContent)
            basicAuth("${loginuser}","${loginpassword}")
            check( status is(ACCEPTED) saveAs("acceptedUndeply"), captureErrors("ACTION_VAPP_UNDEPLOY") )
        )

    val undeployVappHard = 
        exec( (s:Session) =>  undeployStartTime(s))
        .exec( (s:Session) => s.removeAttribute("acceptedUndeply"))
        .asLongAs( (s:Session) => needAcceptedUndeplyRetry(s)){
            pause(0, 3)
            .exec( (s:Session) => actionRetry("undeploy", s) )
            .exec(undeployVapp)
        }
        .exec(updateVappState)
        .asLongAs((s:Session) => !isVirtualApplianceState(s, Set("NOT_DEPLOYED", "NOT_ALLOCATED"))) {//, "UNKNOWN"(
            pause(checkPollingPause)
            .exec(updateVappState)
            .doIf( (s:Session) => isVirtualApplianceState(s, Set("NEEDS_SYNC", "UNKNOWN", "DEPLOYED"))){
                exec( (s:Session) => actionRetry("undeploy", s))
                .exec( (s:Session) => logVirtualApplianceState("check-U",s) )
                .exec(undeployAllVms)
                .exec(updateVappState)
            }
        }
        .exec( (s:Session) => undeployStopTime(s) )
        .exec( (s:Session) => logVirtualApplianceState("undeploy",s) )

    val deployVirtualApplianceChain = 
        exec(loginChain)
        .tryMax(3, "createvapp")
        {
         createVapp
        }
        //.exec(createVapp)
        .repeat("${numVirtualMachinesInVapp}", "numVirtualmachine") {
            exec(createVmWithRetry)
            .pause(0, 5) // slow down create vm conflicts
        }
        .pause(0,1)
        .exec(deployVappHard)
        .pause(1, 10)
        .exec(undeployVappHard)
        .exec(deleteVapp)
        .exec( (s:Session) => reportUserLoop(s) )
        .pause(0, 5) // wait before next loop in deployVirtualApplianceChain


    val virtualResourcesScenario = scenario("deployVirtualAppliance")
            .feed(csv("virtualdatacenter.csv").circular)
            .repeat(userLoop) { deployVirtualApplianceChain }

    def apply = {
        val httpConf = httpConfig.baseURL(baseUrl).disableAutomaticReferer

        List(
            virtualResourcesScenario.configure    users(numUsers)    ramp(rampTime seconds)    protocolConfig    httpConfig.baseURL(baseUrl)
        )
    }
}
