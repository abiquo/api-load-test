import com.excilys.ebi.gatling.core.Predef._
import com.excilys.ebi.gatling.http.Headers.Names._
import com.excilys.ebi.gatling.http.Predef._
import AbiMediaTypes._
import AdminLogin._
import SetupInfrastructure._
import com.excilys.ebi.gatling.http.check.HttpCheck
import org.glassfish.grizzly.http.util.HttpStatus._
import com.excilys.ebi.gatling.http.request.builder.AbstractHttpRequestWithBodyBuilder
import jodd.util.StringUtil

object ReadVirtualResources {

    val readVirtualResourcesScenarioChain = chain
        .exec(http("stadistics_cloudUsage")
            get("/api/admin/statistics/cloudusage/actions/total")
            header(ACCEPT, MT_CLOUDUSAGE)
            check(status is 200)
        )
        .exec(http("stadistics_Enter")
            get("/api/admin/statistics/enterpriseresources/${enterpriseId}")
            header(ACCEPT, MT_RES_ENT)
            check(status is 200)
        )
        .exec(http("stadistics_Vdc")
            get("/api/admin/statistics/vdcsresources") queryParam("identerprise", "${enterpriseId}")
            header(ACCEPT, MT_RES_VDC)
            check(status is 200)
        )
        .exec(http("stadistics_Vapp")
            get("/api/admin/statistics/vappsresources") queryParam("identerprise", "${enterpriseId}")
            header(ACCEPT, MT_RES_VAPP)
            check(status is 200)
        )
        // enterprise action links
        .exec(http("GET_Virtualdatacenters_byEnter")
            get("/api/admin/enterprises/${enterpriseId}/action/virtualdatacenters") queryParam("startwith", "0") queryParam("limit", "25") queryParam("by", "name") queryParam("asc", "true")
            header(ACCEPT, MT_VDCS)
            check(status is 200)
        )
        .exec(http("GET_Virtualappliances_byEnter")
            get("/api/admin/enterprises/${enterpriseId}/action/virtualappliances") queryParam("startwith", "0") queryParam("limit", "25") queryParam("by", "name") queryParam("asc", "true")
            header(ACCEPT, MT_VAPPS)
            check(status is 200)
        )
        .exec(http("GET_Virtualmachines_byEnter")
            get("/api/admin/enterprises/${enterpriseId}/action/virtualmachines") queryParam("startwith", "0") queryParam("limit", "25") queryParam("by", "name") queryParam("asc", "true")
            header(ACCEPT, MT_VMS)
            check(status is 200)
        )
        .pause(1,2) // wait before next loop
        // get("/api/admin/enterprises/1/limits")
        // get("/api/config/categories")
        // get("/api/cloud/virtualdatacenters/4/privatenetworks/1") header(ACCEPT, MT_VLAN)
        // get("/api/cloud/virtualdatacenters/4/virtualappliances/3/virtualmachines/1/storage/volumes") header(ACCEPT, MT_VOLS)


    val readVirtualResourcesScenario = scenario("vdc_reads")
            .insertChain(loginAndGetDefaultDatacenterAndTemplateChain)
            .loop(readVirtualResourcesScenarioChain) during(5, MINUTES)   
}