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



    val waitVmStateOff = exec(updateVmState).asLongAs(isNotVmState("OFF")) {
            pause(pollPause)
            .exec(updateVmState)
            .exec(logVmState("wait off"))
            .doIf(isVmState("ON")) {
                exec(powerOffVm)
            }
        }

    val foreachVmHeplDeploy = foreachVm(
            exec(updateVmState)
            .doIf(isNotVmState("ON")) {
                exec(logVmState("deploy fail"))
            }
            .doIf(isVmState("NOT_ALLOCATED")) {
                exec(deployVm)
            }
            .doIf(isVmState("OFF")) {
                exec(powerOnVm)
            }
        )

    val foreachVmHeplUndeploy = foreachVm(
            exec(updateVmState)
            .doIf(isNotVmState("NOT_ALLOCATED")) {
                exec(logVmState("undeploy fail"))
            }
            .doIf(isVmState("ON")) {
                exec(powerOffVm)
            }
            .doIf(isVmState("OFF")) {
                exec(undeployVm)
            }
        )

    val waitVappDeployed = exec(updateVappState).asLongAs(isNotVappState("DEPLOYED")) {
            pause(pollPause)
            .exec(updateVappState)
            .exec(logVappState("wait deploy"))
            .doIf(isVappState("NOT_DEPLOYED", "NEEDS_SYNC")) { // wait "UNKNOWN"
                exec(foreachVmHeplDeploy)
                .exec(updateVappState)
            }
        }

    val waitVappUndeployed = exec(updateVappState).asLongAs(isNotVappState("NOT_DEPLOYED")) {
            pause(pollPause)
            .exec(updateVappState)
            .exec(logVappState("wait undeploy"))
            .doIf(isVappState("DEPLOYED", "NEEDS_SYNC")) { // wait "UNKNOWN"
                exec(foreachVmHeplUndeploy)
                .exec(updateVappState)
            }
        }

    val reconfigureFromGetVm =  exitBlockOnFail {
             exec(waitVmStateOff)
            .exec(updateVmContent)
            .exec(reconfigVm)
    	}

    val powerOffVms = foreachVm(
            doIf("${powerOffVm}", "true") {
                tryMax(retry) {
                    exec(powerOffVm)
                }
                .doIf("${reconfigure}", "true") {
                    randomSwitch(
                        reconfigureChance -> reconfigureFromGetVm,
                        0                 -> updateVmState
                    )
                }
            }
        )// wait the vapp state

    val deployVappHard = exitBlockOnFail {
            exec(deployVapp)
            .exec(waitVappDeployed)
        }

    val undeployVappHard = exitBlockOnFail {
            exec(undeployVapp)
            .exec(waitVappUndeployed)
        }

    val report = foreachVm(
            exec(getVmTasks)
            .exec(reportTasks)
        )

    val createVappAndAddVms = exitBlockOnFail {
            tryMax(retry) {
                exec(createVapp)
            }
            .repeat("${numVms}", "numVm") {
                tryMax(retry) {
                    exec(createVm)
                    .exec(saveCreatedVm)
                }
            }
        }

    val deployVappChain = exitBlockOnFail {
            exec(createVappAndAddVms)
            .exec(deployVappHard)
            .repeat("${vappDeployTime}", "deployed") { // pauseCustom ?
                pause(1)
            }
            .exec(powerOffVms)
            .exec(waitVappDeployed)
            .doIfOrElse("${undeployVapp}", "true") {
                exec(undeployVappHard)
                .exec(report)
                .doIf("${deleteVapp}", "true") {
                    tryMax(retry) {
                        exec(deleteVapp)
                    }
                }
            } {
                exec(report)
            }
            .exec(vappDone)
            .exec(stadistics, listByEnterprise)
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
                    tryMax(retry) {
                        exec(createVdc)
                    }
                    .repeat("${numVapps}", "vapLoop") {
                        exec(deployVappChain)
                    }
                    .doIf("${deleteVdc}", "true") {
                        tryMax(retry) {
                            exec(deleteVdc)
                        }
                    }
                }
            }

	setUp(
        vdcVappVm users(numUsers) ramp( rampTime seconds)                  protocolConfig httpConf
	)
}