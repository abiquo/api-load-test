import com.excilys.ebi.gatling.core.Predef._
import com.excilys.ebi.gatling.http.Headers.Names._
import com.excilys.ebi.gatling.http.Predef._
import AbiquoAPI._
import API._
import com.excilys.ebi.gatling.http.check.HttpCheck
import com.excilys.ebi.gatling.http.request.builder.AbstractHttpRequestWithBodyBuilder
import bootstrap._

object SetupInfrastructure extends Simulation {
    //Create a Datacenter a Rack a Machine and refresh the DatacenterRepository
    //sets $datacenterId, $rackId, $machineId, $templateId
    val setupInfrastructure =
        exitBlockOnFail {
            exec(login)
            .exec(createConfDatacenter, createConfRack, createConfMachine, refreshRepository)
            .pause(5) // wait for AM notifications to populate the VirtualMachineTemplates
        }

    val setupInfrastructureScenario = scenario("initInfrastructure")
        .feed(csv("login.csv"))
        .feed(csv("infrastructure.csv"))
        .exec(setupInfrastructure)

	setUp(setupInfrastructureScenario users 1 protocolConfig httpConf )
}