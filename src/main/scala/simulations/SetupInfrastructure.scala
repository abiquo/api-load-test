import com.excilys.ebi.gatling.core.Predef._
import com.excilys.ebi.gatling.http.Headers.Names._
import com.excilys.ebi.gatling.http.Predef._
import AbiquoAPI._
import AdminLogin._
import com.excilys.ebi.gatling.http.check.HttpCheck
import org.glassfish.grizzly.http.util.HttpStatus._
import com.excilys.ebi.gatling.http.request.builder.AbstractHttpRequestWithBodyBuilder
import bootstrap._

object SetupInfrastructure extends Simulation {

    def captureDatacenterId = regex("""datacenters/(\d+)""") find(0) saveAs("datacenterId")
    def captureRackId       = regex("""racks/(\d+)""") find(0) saveAs("rackId")
    def captureMachineId    = regex("""machines/(\d+)""") find(0) saveAs("machineId")

    //Create a Datacenter a Rack a Machine and refresh the DatacenterRepository: sets $datacenterId, $rackId, $machineId, $templateId
    val setupInfrastructureChain = 
        // Datacenter
        exec(http("POST_Datacenter")
            post("/api/admin/datacenters")
            header(ACCEPT, MT_DC) header(CONTENT_TYPE, MT_DC)
            fileBody("datacenter.xml",
                Map("remoteservicesIp" -> "${remoteservicesIp}"))
            check(  status is(201), captureDatacenterId)
        )
        .doIf( (s:Session) => exitIfNoDefined(s, "datacenterId")) { pause(1) }

        // Rack
        .exec(http("POST_Rack")
            post("/api/admin/datacenters/${datacenterId}/racks")
            header(ACCEPT, MT_RACK) header(CONTENT_TYPE, MT_RACK)
            fileBody("rack.xml",
                Map("name" -> "myrack"))
            check(  status is(201),captureRackId )
        )
        .doIf( (s:Session) => exitIfNoDefined(s, "rackId")) { pause(1) }

        // Machine (do not use nodecollector) from feeder 'infrastructure.csv'
        // get("/api/admin/datacenters/${datacenter}/action/hypervisor") queryParam("ip", "10.60.1.120")
        // get("/api/admin/datacenters/2/action/discoversingle") queryParam("ip", "10.60.1.120") ....
        .exec(http("POST_Machine")
            post("/api/admin/datacenters/${datacenterId}/racks/${rackId}/machines")
            header(ACCEPT, MT_MACHINE) header(CONTENT_TYPE, MT_MACHINE)
            fileBody("machine.xml", Map(
                "hypervisorIp"      -> "${hypervisorIp}",
                "hypervisorType"    -> "${hypervisorType}")
            )
            check(  status is(201), captureMachineId )
        )
        .doIf( (s:Session) => exitIfNoDefined(s, "machineId")) { pause(1) }

        // Refresh the DatacenterRespository
        .exec(http("refreshDatacenterRepo")
            get("/api/admin/enterprises/${enterpriseId}/datacenterrepositories/${datacenterId}") queryParam("refresh", "true") queryParam("usage", "true")
            header(ACCEPT, MT_DC_REPO)
            check(  status is(200)  )
        )
        .pause(5) // wait for AM notifications to populate the VirtualMachineTemplates
        .exec((s:Session) => { println(s); s })


    val setupInfrastructureScenario = scenario("initInfrastructure")
        .exec(loginChain)
        .feed(csv("infrastructure.csv"))
        .exec(setupInfrastructureChain)


    def apply = {

        val urlBase = "http://10.60.1.223:80"
        val httpConf = httpConfig.baseURL(urlBase)

        List( setupInfrastructureScenario.configure users 1 protocolConfig httpConfig.baseURL(urlBase) )
    }

}