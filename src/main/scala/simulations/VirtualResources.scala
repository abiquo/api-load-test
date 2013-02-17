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


    val waitVmStateOff = exec(updateVmState).asLongAs(s => !isVirtualMachineState(s, Set("OFF"))) {
            pause(pollPause)
            .exec(updateVmState)
            .exec(s => logVmState("wait off", s))
            .doIf(s => isVirtualMachineState(s, Set("ON"))) {
                exec(s => logVmState("off fail", s))
                .exec(powerOffVm)
            }
        }

    val foreachVmHeplDeploy = repeat("${numVms}", "numVm") {
            exec( (s:Session) => setCurrentVmId(s) )
            .exec(updateVmState)
            .exec(s => logVmState("wait deploy", s))
            .doIf(s => isVirtualMachineState(s, Set("NOT_ALLOCATED"))) {
                exec(s => logVmState("deploy fail", s))
                exec(deployVm)
            }
            .exec( (s:Session) => clearCurrentVmId(s) )
        }

    val foreachVmHeplUndeploy = repeat("${numVms}", "numVm") {
            exec( (s:Session) => setCurrentVmId(s) )
            .exec(updateVmState)
            .exec(s => logVmState("wait undeploy", s))
            .doIf(s => isVirtualMachineState(s, Set("ON", "OFF"))) {
                exec(s => logVmState("undeploy fail", s))
                exec(undeployVm)
            }
            .exec( (s:Session) => clearCurrentVmId(s) )
        }

    val waitVappDeployed = exec(updateVappState).asLongAs(s => !isVirtualApplianceState(s, Set("DEPLOYED"))) {
            pause(pollPause)
            .exec(updateVappState)
            .exec(s => logVirtualApplianceState("wait deploy", s))
            .doIf(s => isVirtualApplianceState(s, Set("NOT_DEPLOYED", "NEED_SYNC"))) { // wait "UNKNOWN"
                exec(s => logVirtualApplianceState("deploy fail", s))
                .exec(foreachVmHeplDeploy)
            }
        }

    val waitVappUndeployed = exec(updateVappState).asLongAs(s => !isVirtualApplianceState(s, Set("NOT_DEPLOYED"))) {
            pause(pollPause)
            .exec(updateVappState)
            .exec(s => logVirtualApplianceState("wait undeploy", s))
            .doIf(s => isVirtualApplianceState(s, Set("DEPLOYED", "NEED_SYNC"))) { // wait "UNKNOWN"
                exec(s => logVirtualApplianceState("undeploy fail", s))
                .exec(foreachVmHeplUndeploy)
            }
        }

    val reconfigureFromGetVm =  exitBlockOnFail {
	    	exec(waitVmStateOff)
            .exec(updateVmContent)
            .exec(reconfigVm)
    	}

    val foreachVm = repeat("${numVms}", "numVm") {
            exec( (s:Session) => setCurrentVmId(s) )
            .doIf("${powerOffVm}", "true") {
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
            .exec( (s:Session) => clearCurrentVmId(s) )
        } // wait the vapp state

    val deployVappHard = tryMax(retry) {
            exec(s => deployStartTime(s))
            .exec(deployVapp)
            .exec(waitVappDeployed)
            .exec(s => deployStopTime(s))
            .exec(s => logVirtualApplianceState("deploy", s))
        }

    val undeployVappHard = tryMax(retry) {
            exec(s =>  undeployStartTime(s))
            .exec(undeployVapp)
            .exec(waitVappUndeployed)
            .exec(s => undeployStopTime(s))
            .exec(s => logVirtualApplianceState("undeploy",s))
        }

    val report = repeat("${numVms}", "numVm") {
            exec( (s:Session) => setCurrentVmId(s) )
            .exec(getVmTasks)
            .exec(s => reportTasks(s))
            .exec( (s:Session) => clearCurrentVmId(s) )
        }

    val createVappAndAddVms = exitBlockOnFail {
            tryMax(retry) {
                createVapp
            }
            .repeat("${numVms}", "numVm") {
                tryMax(retry) {
                    exec(createVm)
                    .exec(s => saveCurrentVirtualmachine(s))
                }
            }
        }

    val deployVirtualApplianceChain = exitBlockOnFail {
            exec(createVappAndAddVms)
            .exec(deployVappHard)
            .repeat("${vappDeployTime}", "deployed") { // pauseCustom ?
                pause(1)
            }
            .exec(login)
            .exec(foreachVm)
            .exec(waitVappDeployed)
            .exec(login)
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
                    tryMax(retry) {
                        createVdc
                    }
                    .repeat("${numVapps}", "vapLoop") {
                        deployVirtualApplianceChain
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