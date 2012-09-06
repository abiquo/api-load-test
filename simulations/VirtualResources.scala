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

class VirtualResources extends Simulation {

    // configure

    val baseUrl  = System.getProperty("baseUrl","http://localhost:80")
    val numUsers = Integer.getInteger("numUsers", 1)
    val rampTime = Integer.getInteger("rampTime", 1)
    val userLoop = Integer.getInteger("userLoop", 1)
    val checkPollingPause = 5

    // end configure

    val createVapp = chain.exec(http("POST_VAPP")
            post(POST_VAPP) header(ACCEPT, MT_VAPP) header(CONTENT_TYPE, MT_VAPP)
            fileBody("vapp.xml", vappContent)
            check( status is(CREATED), captureErrors("POST_VAPP"), captureVirtualapplianceId)
        )
        //.doIf( (s:Session) => exitIfNoDefined(s, "virtualapplianceId"), chain.pause(0,1))

    val deleteVapp = chain.exec(http("DEL_VAPP")
            delete(DEL_VAPP) header(ACCEPT, MT_XML)
            check( status is(NO_CONTENT) )
            //captureErrors("DEL_VAPP") SAXParseException: Premature end of file.
        )

    val updateVmState = chain.exec(http("GET_VM_STATE")
            get(GET_VM_STATE) header(ACCEPT, MT_VMSTATE)
            check( status is(OK), captureCurrentVmState )
        )

    val undeployVm = chain.exec(http("ACTION_VM_UNDEPLOY")
            post(ACTION_VM_UNDEPLOY) header(ACCEPT, MT_ACCEPTED) header(CONTENT_TYPE, MT_VMTASK)
            fileBody("vmtask.xml", vmtaskContent)
            check( status is(ACCEPTED) )
        )
        // don't wait (undeplyVapp)

    val deleteVm = chain.exec(http("DEL_VM")
            delete(DEL_VM)
            check( status is(NO_CONTENT) )
        )

    val deployVm = chain.exec(http("ACTION_VM_DEPLOY")
            post(ACTION_VM_DEPLOY) header(ACCEPT, MT_ACCEPTED) header(CONTENT_TYPE, MT_VMTASK)
            fileBody("vmtask.xml", vmtaskContent)
            check( status is(ACCEPTED) )
        )
        // don't wait (deplyVapp)

    val checkCurrentVmNeedUndeploy = chain
        .exec( (s:Session) => clearCurrentVmState(s) )
        .insertChain(updateVmState)
        .doIf( (s:Session) => isCurrentVirtualMachineState(s, Set("ON", "OFF", "CONFIGURED")) ,
            chain.exec( (s:Session) => actionRetry("forceUndeployVm", s))
            .insertChain(undeployVm)
        )
        .pause(1)
        .insertChain(updateVmState)
        .doIf( (s:Session) => isCurrentVirtualMachineState(s, Set("UNKNOWN", "NOT_ALLOCATED")),
            chain.exec( (s:Session) => actionRetry("delVmUndeploy", s))
            .insertChain(deleteVm)
        )

    val checkCurrentVmNeedDeploy = chain
        .exec( (s:Session) => clearCurrentVmState(s) )
        .insertChain(updateVmState)
        .doIf( (s:Session) => isCurrentVirtualMachineState(s, Set("NOT_ALLOCATED", "OFF")),
            chain.exec( (s:Session) => actionRetry("forceDeployVm", s))
            .insertChain(deployVm)
        )
        .doIf( (s:Session) => isCurrentVirtualMachineState(s, Set("UNKNOWN")),
            chain.exec( (s:Session) => actionRetry("delVmDeploy", s))
            .insertChain(deleteVm)
        )

    val deployAllVms = chain
        .loop(
            chain.exec( (s:Session) => setCurrentVmId(s) )
            .insertChain(checkCurrentVmNeedDeploy)
        ).counterName("numVirtualmachine").times("${numVirtualMachinesInVapp}")

    val undeployAllVms = chain
        .loop(
            chain.exec( (s:Session) => setCurrentVmId(s) )
            .insertChain(checkCurrentVmNeedUndeploy)
        ).counterName("numVirtualmachine").times("${numVirtualMachinesInVapp}")

    val createVm = chain.exec(http("POST_VM")
            post(POST_VM) header(ACCEPT, MT_VM) header(CONTENT_TYPE, MT_VM_NODE)
            fileBody("vm.xml", vmContent)
            check( status is(CREATED) saveAs("currentVmCreated"), captureErrors("POST_VM"), captureCurrentVirtualmachineId )
        )
        .exec( (s:Session) =>  saveCurrentVirtualmachineId(s))

    val createVmWithRetry = chain
        .exec( (s:Session) =>  clearCurrentVmCreation(s))
        .loop(
            chain.pause(1) // before create
            .insertChain(createVm)
            .exec( (s:Session) => actionRetry("createVm", s))
        ).asLongAs( (s:Session) => needCreateVirtualMachineRetry(s) )

    val updateVappState = chain.exec(http("GET_VAPP_STATE")
            get(GET_VAPP_STATE) header(ACCEPT, MT_VAPPSTATE)
            check( status is(OK), captureVirtualapplianceState )
        )

    val deployVapp = chain.exec(http("ACTION_VAPP_DEPLOY")
            post(ACTION_VAPP_DEPLOY) header(ACCEPT, MT_ACCEPTED) header(CONTENT_TYPE, MT_VMTASK)
            fileBody("vmtask.xml", vmtaskContent)
            check( status is(ACCEPTED) saveAs("acceptedDeply"), captureErrors("ACTION_VAPP_DEPLOY") )
        )

    val deployVappHard = chain
        .exec( (s:Session) =>  deployStartTime(s))
        .exec( (s:Session) => s.removeAttribute("acceptedDeply"))
        .loop(
            chain.pause(0, 3)
            .exec( (s:Session) => actionRetry("deploy", s) )
            .insertChain(deployVapp)
        ).asLongAs( (s:Session) => needAcceptedDeplyRetry(s))
        .insertChain(updateVappState)
        .loop(
            chain.pause(checkPollingPause)
            .insertChain(updateVappState)
            .doIf( (s:Session) => isVirtualApplianceState(s, Set("NEEDS_SYNC", "UNKNOWN")),
                chain.exec( (s:Session) => actionRetry("deploy", s))
                .exec( (s:Session) => logVirtualApplianceState("check-D",s) )
                .insertChain(deployAllVms)
                .insertChain(updateVappState)
            )
        ).asLongAs((s:Session) => !isVirtualApplianceState(s, Set("DEPLOYED"))) //, "UNKNOWN"
        .exec( (s:Session) => deployStopTime(s) )
        .exec( (s:Session) => logVirtualApplianceState("deploy",s) )

    val undeployVapp = chain.exec(http("ACTION_VAPP_UNDEPLOY")
            post(ACTION_VAPP_UNDEPLOY) header(ACCEPT, MT_ACCEPTED) header(CONTENT_TYPE, MT_VMTASK)
            fileBody("vmtask.xml", vmtaskContent)
            check( status is(ACCEPTED) saveAs("acceptedUndeply"), captureErrors("ACTION_VAPP_UNDEPLOY") )
        )

    val undeployVappHard = chain
        .exec( (s:Session) =>  undeployStartTime(s))
        .exec( (s:Session) => s.removeAttribute("acceptedUndeply"))
        .loop(
            chain.pause(0, 3)
            .exec( (s:Session) => actionRetry("undeploy", s) )
            .insertChain(undeployVapp)

        ).asLongAs( (s:Session) => needAcceptedUndeplyRetry(s))
        .insertChain(updateVappState)
        .loop(
            chain.pause(checkPollingPause)
            .insertChain(updateVappState)
            .doIf( (s:Session) => isVirtualApplianceState(s, Set("NEEDS_SYNC", "UNKNOWN", "DEPLOYED")),
                chain.exec( (s:Session) => actionRetry("undeploy", s))
                .exec( (s:Session) => logVirtualApplianceState("check-U",s) )
                .insertChain(undeployAllVms)
                .insertChain(updateVappState)
            )
        ).asLongAs((s:Session) => !isVirtualApplianceState(s, Set("NOT_DEPLOYED", "NOT_ALLOCATED"))) //, "UNKNOWN"
        .exec( (s:Session) => undeployStopTime(s) )
        .exec( (s:Session) => logVirtualApplianceState("undeploy",s) )

    val deployVirtualApplianceChain = chain
        .insertChain(loginChain)
        .insertChain(createVapp)
        .loop(
            chain.insertChain(createVmWithRetry)
            .pause(0, 5) // slow down create vm conflicts
        ).counterName("numVirtualmachine").times("${numVirtualMachinesInVapp}")
        .pause(0,1)
        .insertChain(deployVappHard)
        .pause(1, 30)
        .insertChain(undeployVappHard)
        .insertChain(deleteVapp)
        .exec( (s:Session) => reportUserLoop(s) )
        .pause(0, 5) // wait before next loop in deployVirtualApplianceChain


    val virtualResourcesScenario = scenario("deployVirtualAppliance")
            .feed(csv("virtualdatacenter.csv").circular)
            .loop(deployVirtualApplianceChain) times(userLoop)

    def apply = {
        val httpConf = httpConfig.baseURL(baseUrl).disableAutomaticReferer

        List(
            virtualResourcesScenario.configure    users(numUsers)    ramp(rampTime)    protocolConfig    httpConfig.baseURL(baseUrl)
        )
    }
}