import com.excilys.ebi.gatling.core.Predef._
import com.excilys.ebi.gatling.http.Headers.Names._
import com.excilys.ebi.gatling.http.Predef._
import com.excilys.ebi.gatling.http.check.HttpCheck
import com.excilys.ebi.gatling.http.request.builder.AbstractHttpRequestWithBodyBuilder
import com.excilys.ebi.gatling.core.structure.ChainBuilder
import jodd.util.StringUtil
import com.ning.http.client._

import API._
import AbiquoAPI._

import bootstrap._
import akka.util.duration._

class VirtualResources extends Simulation {

    val retry    = 5
    val pollPause= 5

    val createVmWithRetry = tryMax(retry, "postVMRetry") {
            createVm
        }
        .exec(s => saveCurrentVirtualmachine(s))

    val createVappAndAddVms = tryMax(retry, "createvapp") {
            createVapp
        }
        .repeat("${numVirtualMachinesInVapp}", "numVm") {
            createVmWithRetry
        }

    val waitVmStateOff = exec(updateVmState)
        .asLongAs(s => !isVirtualMachineState(s, Set("OFF"))) {
            pause(pollPause)
            .exec(updateVmState)
        }

    val waitVappDeployed = exec(updateVappState)
        .asLongAs(s => !isVirtualApplianceState(s, Set("DEPLOYED"))) {
            pause(pollPause)
            .exec(updateVappState)
        }
    
    val waitVappUndeployed = exec(updateVappState)
        .asLongAs(s => !isVirtualApplianceState(s, Set("NOT_DEPLOYED", "NOT_ALLOCATED"))) {
            pause(pollPause)
            .exec(updateVappState)
        }
    
    val reconfigureFromGetVm =  exitBlockOnFail {
	    	exec(updateVmContent)
	    	.exec(reconfigVm)
	        //.exec(waitVmStateOff) vm is LOCKED
    	}
        
    val powerOffAndPerhapsReconfigure =
            exec(powerOffVm)
            .exec(waitVmStateOff)
            .randomSwitch(
                50 -> reconfigureFromGetVm,
                50 -> updateVmState //XXX do nothing
            )

    val foreachVmReconfigureChance = repeat("${numVirtualMachinesInVapp}", "numVm") {
            exec( (s:Session) => setCurrentVmId(s) )
            .randomSwitch(
                50 -> powerOffAndPerhapsReconfigure,
                50 -> updateVmState //XXX do nothing
            )
            .exec( (s:Session) => clearCurrentVmId(s) )
        }

    val deployVappHard =
        exec(s => deployStartTime(s))
        .tryMax(retry, "retryDeploy") {
            deployVapp
        }
        .exec(waitVappDeployed)
        .exec(s => deployStopTime(s))
        .exec(s => logVirtualApplianceState("deploy", s))

    val undeployVappHard =
        exec(s =>  undeployStartTime(s))
        .tryMax(retry, "retryUndeploy") {
            undeployVapp
        }
        .exec(waitVappUndeployed)
        .exec(s => undeployStopTime(s))
        .exec(s => logVirtualApplianceState("undeploy",s))

    val deployVirtualApplianceChain =
        exec(login)
        .exitHereIfFailed
        .exec(createVappAndAddVms)
        .exitHereIfFailed
        .exec(deployVappHard)
        .pause(0, 5)
        .exec(foreachVmReconfigureChance)
        .exec(waitVappDeployed) // some LOCKED vms reconfiguring
        .pause(0, 5)
        .exec(undeployVappHard)
        .doIf(delVapp){ // default false
          deleteVapp
        }
        .exec( s => reportUserLoop(s))
        .pause(0, 5) // wait before next loop in deployVirtualApplianceChain

    val deployVirtualAppliance = scenario("deployVirtualAppliance")
            .feed(csv("virtualdatacenter.csv").circular)
            .repeat(userLoop, "userLoop") {
                deployVirtualApplianceChain
            }

    val pollStadistics = scenario("pollStadistics")
            .feed(loginFeed)
            .during( 1 hours) {
                login
                .exitHereIfFailed
                .during( 29 minutes, "stadistics") {
                    stadistics
                    .exec(listByEnterprise)
                    .pause(0, 5)
                }
            }

    def apply = {
        List(
            deployVirtualAppliance .configure users(numUsers) ramp( rampTime seconds)                  protocolConfig httpConf
            , pollStadistics       .configure users(60)       delay(rampTime seconds) ramp(1 minutes)  protocolConfig httpConf
        )
    }
}
