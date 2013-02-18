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



    val waitVmStateOff = exec(updateVmState).asLongAs(!isVirtualMachineState(Set("OFF"),_)) {
            pause(pollPause)
            .exec(updateVmState)
            .exec(logVmState("wait off",_) )
            .doIf(isVirtualMachineState(Set("ON"),_) ) {
                exec(logVmState("off fail",_) )
                .exec(powerOffVm)
            }
        }

    val foreachVmHeplDeploy = repeat("${numVms}", "numVm") {
            exec(setCurrentVmId)
            .exec(updateVmState)
            .doIf(!isVirtualMachineState(Set("ON"),_) ) {
                exec(logVmState("deploy fail",_) )
            }
            .doIf(isVirtualMachineState(Set("NOT_ALLOCATED"),_) ) {
                exec(deployVm)
            }
            .doIf(isVirtualMachineState(Set("OFF"),_) ) {
                exec(powerOnVm)
            }
            .exec(clearCurrentVmId)
        }

    val foreachVmHeplUndeploy = repeat("${numVms}", "numVm") {
            exec(setCurrentVmId)
            .exec(updateVmState)
            .doIf(!isVirtualMachineState(Set("NOT_ALLOCATED"),_) ) {
                exec(logVmState("undeploy fail",_) )
            }
            .doIf(isVirtualMachineState(Set("ON"),_) ) {
                exec(powerOffVm)
            }
            .doIf(isVirtualMachineState(Set("OFF"),_) ) {
                exec(undeployVm)
            }
            .exec(clearCurrentVmId)
        }

    val waitVappDeployed = exec(updateVappState).asLongAs(!isVirtualApplianceState(Set("DEPLOYED"),_) ) {
            pause(pollPause)
            .exec(updateVappState)
            .exec(logVirtualApplianceState("wait deploy",_) )
            .doIf(isVirtualApplianceState(Set("NOT_DEPLOYED", "NEEDS_SYNC"),_) ) { // wait "UNKNOWN"
                exec(logVirtualApplianceState("deploy fail",_) )
                .exec(foreachVmHeplDeploy)
                .exec(updateVappState)
            }
        }

    val waitVappUndeployed = exec(updateVappState).asLongAs(!isVirtualApplianceState(Set("NOT_DEPLOYED"),_) ) {
            pause(pollPause)
            .exec(updateVappState)
            .exec(logVirtualApplianceState("wait undeploy",_) )
            .doIf(isVirtualApplianceState(Set("DEPLOYED", "NEEDS_SYNC"),_) ) { // wait "UNKNOWN"
                exec(logVirtualApplianceState("undeploy fail",_) )
                .exec(foreachVmHeplUndeploy)
                .exec(updateVappState)
            }
        }

    val reconfigureFromGetVm =  exitBlockOnFail {
	    	exec(waitVmStateOff)
            .exec(updateVmContent)
            .exec(reconfigVm)
    	}

    val foreachVm = repeat("${numVms}", "numVm") {
            exec(setCurrentVmId)
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
            .exec(clearCurrentVmId)
        } // wait the vapp state

    val deployVappHard =
            exec(deployStartTime)
            .exec(deployVapp)
            .exec(waitVappDeployed)
            .exec(deployStopTime)
            .exec(logVirtualApplianceState("deploy",_) )


    val undeployVappHard =
            exec(undeployStartTime)
            .exec(undeployVapp)
            .exec(waitVappUndeployed)
            .exec(undeployStopTime)
            .exec(logVirtualApplianceState("undeploy",_) )

    val report = repeat("${numVms}", "numVm") {
            exec(setCurrentVmId)
            .exec(getVmTasks)
            .exec(reportTasks)
            .exec(clearCurrentVmId)
        }

    val createVappAndAddVms = exitBlockOnFail {
            tryMax(retry) {
                createVapp
            }
            .repeat("${numVms}", "numVm") {
                tryMax(retry) {
                    exec(createVm)
                    .exec(saveCurrentVirtualmachine)
                }
            }
        }

    val deployVirtualApplianceChain = exitBlockOnFail {
            exec(createVappAndAddVms)
            .exec(deployVappHard)
            .repeat("${vappDeployTime}", "deployed") { // pauseCustom ?
                pause(1)
            }
            .exec(foreachVm)
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
            .exec( stadistics, listByEnterprise)
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