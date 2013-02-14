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

    val reconfigureChance = 50

    val retry    = 5
    val pollPause= 5

    val createVmWithRetry = tryMax(retry, "postVMRetry") {
            createVm
        }
        .exec(s => saveCurrentVirtualmachine(s))

    val createVappAndAddVms = tryMax(retry, "createvapp") {
            createVapp
        }
        .repeat("${numVms}", "numVm") {
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
            .exec(s => logVirtualApplianceState("waitdeploy", s))
            .doIf(s => isVirtualApplianceState(s, Set("NOT_DEPLOYED", "NEED_SYNC"))) { // wait "UNKNOWN"
                exec(s => logVirtualApplianceState("deploy fail", s))
                .exec(deployVapp)
            }
        }

    val waitVappUndeployed = exec(updateVappState)
        .asLongAs(s => !isVirtualApplianceState(s, Set("NOT_DEPLOYED"))) {
            pause(pollPause)
            .exec(updateVappState)
            .exec(s => logVirtualApplianceState("waitundeploy", s))
            .doIf(s => isVirtualApplianceState(s, Set("DEPLOYED", "NEED_SYNC"))) { // wait "UNKNOWN"
                exec(s => logVirtualApplianceState("undeploy fail", s))
                .exec(undeployVapp)
            }
        }

    val reconfigureFromGetVm =  exitBlockOnFail {
	    	exec(waitVmStateOff)
            .exec(updateVmContent)
	        .exec(reconfigVm)
	        //.exec(waitVmStateOff) wait the vapp
    	}

    val foreachVm = repeat("${numVms}", "numVm") {
            exec( (s:Session) => setCurrentVmId(s) )
            .doIf("${powerOffVm}", "true") {
                exec(powerOffVm)
                .doIf("${reconfigure}", "true") {
                    randomSwitch(
                        reconfigureChance -> reconfigureFromGetVm,
                        0                 -> updateVmState //XXX do nothing
                    )
                }
            }
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

    val report = repeat("${numVms}", "numVm") {
            exec( (s:Session) => setCurrentVmId(s) )
            .exec(getVmTasks)
            .exec(s => reportTasks(s))
            .exec( (s:Session) => clearCurrentVmId(s) )
        }

    val deployVirtualApplianceChain =
        exitBlockOnFail {
            exec(createVappAndAddVms)
            .exec(deployVappHard)
            .repeat("${vappDeployTime}", "deployed") { // pauseCustom ?
                pause(1)
            }
            .exec(login)
            .exec(foreachVm)
            .exec(waitVappDeployed)
            .doIfOrElse("${undeployVapp}", "true") {
                exec(undeployVappHard)
                .exec(report)
                .doIf("${deleteVapp}", "true") {
                    deleteVapp
                }
            } {
                exec(report)
            }
            .exec( s => reportUserLoop(s))
            .exec( stadistics, listByEnterprise)
            .pause(0, 1) // wait before next loop in deployVirtualApplianceChain
        }

    val vdcVappVm = scenario("vdcVappVm")
            .feed(csv("login.csv").circular)
            .feed(csv("datacenter.csv").circular)
            .feed(csv("virtualdatacenter.csv").circular)
            .feed(csv("virtualmachine.csv").circular)
            .exec(login)
            .exitHereIfFailed
            .repeat("${numVdcs}", "vdcLoop") {
                exitBlockOnFail {
                    exec(createVdc)
                    .repeat("${numVapps}", "vapLoop") {
                        deployVirtualApplianceChain
                    }
                    .doIf("${deleteVdc}", "true") {
                        deleteVdc
                    }
                }
            }

	setUp(
        vdcVappVm users(numUsers) ramp( rampTime seconds)                  protocolConfig httpConf
	)
}