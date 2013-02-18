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
                exec(logVmState("off fail"))
                .exec(powerOffVm)
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
                exec(logVappState("deploy fail"))
                .exec(foreachVmHeplDeploy)
                .exec(updateVappState)
            }
        }

    val waitVappUndeployed = exec(updateVappState).asLongAs(isNotVappState("NOT_DEPLOYED")) {
            pause(pollPause)
            .exec(updateVappState)
            .exec(logVappState("wait undeploy"))
            .doIf(isVappState("DEPLOYED", "NEEDS_SYNC")) { // wait "UNKNOWN"
                exec(logVappState("undeploy fail"))
                .exec(foreachVmHeplUndeploy)
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

    val deployVappHard =
            exec(deployStartTime)
            .exec(deployVapp)
            .exec(waitVappDeployed)
            .exec(deployStopTime)
            .exec(logVappState("deploy"))


    val undeployVappHard =
            exec(undeployStartTime)
            .exec(undeployVapp)
            .exec(waitVappUndeployed)
            .exec(undeployStopTime)
            .exec(logVappState("undeploy"))

    val report = foreachVm(
            exec(getVmTasks)
            .exec(reportTasks)
        )

    val createVappAndAddVms = exitBlockOnFail {
            tryMax(retry) {
                createVapp
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
                        deleteVapp
                    }
                }
            } {
                exec(report)
            }
            .exec(reportUserLoop)
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
                        createVdc
                    }
                    .repeat("${numVapps}", "vapLoop") {
                        deployVappChain
                    }
                    .doIf("${deleteVdc}", "true") {
                        tryMax(retry) {
                            deleteVdc
                        }
                    }
                }
            }

	setUp(
        vdcVappVm users(numUsers) ramp( rampTime seconds)                  protocolConfig httpConf
	)
}