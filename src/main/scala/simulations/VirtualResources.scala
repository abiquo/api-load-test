import com.excilys.ebi.gatling.core.Predef._
import com.excilys.ebi.gatling.http.Headers.Names._
import com.excilys.ebi.gatling.http.Predef._
import com.excilys.ebi.gatling.http.check.HttpCheck
import org.glassfish.grizzly.http.util.HttpStatus._
import com.excilys.ebi.gatling.http.request.builder.AbstractHttpRequestWithBodyBuilder
import com.excilys.ebi.gatling.core.structure.ChainBuilder
import jodd.util.StringUtil
import com.ning.http.client._

import API._
import AbiquoAPI._

import bootstrap._
import akka.util.duration._

class VirtualResources extends Simulation {

    // configure

    val baseUrl  = System.getProperty("baseUrl","http://localhost:80")
    val numUsers = Integer.getInteger("numUsers", 1)
    val rampTime = Integer.getInteger("rampTime", 1).toLong
    val userLoop = Integer.getInteger("userLoop", 1)
    val retry    = 5;
    val pollPause= 5

    val createVmWithRetry = tryMax(retry, "postVMRetry") {
            createVm
        }

    val createVappAndAddVms = tryMax(retry, "createvapp") {
            createVapp
        }
        .repeat("${numVirtualMachinesInVapp}", "numVirtualmachine") {
            createVmWithRetry
            //.pause(0, 5) // slow down create vm conflicts
        }

    val deployVappHard =
        exec(s => deployStartTime(s))
        .tryMax(retry, "retryDeploy") {
            deployVapp
        }
        .exec(updateVappState)
        .asLongAs(s => !isVirtualApplianceState(s, Set("DEPLOYED"))) {
            pause(pollPause)
            .exec(updateVappState)
        }
        .exec(s => deployStopTime(s))
        .exec(s => logVirtualApplianceState("deploy", s))


    val undeployVappHard =
        exec(s =>  undeployStartTime(s))
        .tryMax(retry, "retryUndeploy") {
            undeployVapp
        }
        .exec(updateVappState)
        .asLongAs(s => !isVirtualApplianceState(s, Set("NOT_DEPLOYED", "NOT_ALLOCATED"))) {
            pause(pollPause)
            .exec(updateVappState)
        }
        .exec(s => undeployStopTime(s))
        .exec(s => logVirtualApplianceState("undeploy",s))

    val deployVirtualApplianceChain =
        exec(login)
        .exec(createVappAndAddVms)
        .exitHereIfFailed
        .exec(deployVappHard)
        .pause(1, 10)
        .exec(undeployVappHard)
        //FIXME do not delete the vapp
        //.exec(deleteVapp)
        .exec( s => reportUserLoop(s))
        .pause(0, 5) // wait before next loop in deployVirtualApplianceChain


    val deployVirtualAppliance = scenario("deployVirtualAppliance")
            .feed(csv("virtualdatacenter.csv").circular)
            .repeat(userLoop, "userLoop") {
                deployVirtualApplianceChain
            }

    val pollStadistics = scenario("pollStadistics")
            .feed(Array(Map("loginuser" -> "admin", "loginpassword" -> "xabiquo")).circular)            
            .during( 1 hours) {
                login
                .during( 29 minutes, "stadistics") {
                    stadistics
                    .exec(listByEnterprise)                
                    .pause(0, 5)
                }
            }

    def apply = {
        val httpConf = httpConfig.baseURL(baseUrl).disableAutomaticReferer

        List(
            deployVirtualAppliance .configure users(numUsers) ramp(rampTime seconds) protocolConfig httpConfig.baseURL(baseUrl)
            , pollStadistics       .configure users(60)       ramp(1 minutes)        protocolConfig httpConfig.baseURL(baseUrl)
        )
    }
}
