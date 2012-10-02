import com.excilys.ebi.gatling.http.Predef._
import com.excilys.ebi.gatling.http.Headers.Names._
import com.excilys.ebi.gatling.core.Predef._
import AbiquoAPI._
import bootstrap._

object AdminLogin{

    def captureDatacenterId = regex("""datacenters/(\d+)""") find(0) saveAs("datacenterId")
    def captureTemplateId   = regex("""virtualmachinetemplates/(\d+)""") find(0) saveAs("templateId")
    def captureEnterpriseId = regex("""enterprises/(\d+)/users/""") saveAs("enterpriseId")
    def captureUserId       = regex("""users/(\d+)""") saveAs("currentUserId")

    def exitIfNoDefined(s:Session, paramname:String) = {
        if(!s.isAttributeDefined(paramname)) {
            println("FATAL ''" + paramname + "'' not set in session, check capture methods")
            println(s)
            sys.exit(0);
        }
        true
    }

    val loginChain = exec(http("login")
        get("/api/login")
        header(ACCEPT, MT_USER)
        basicAuth("${loginuser}","${loginpassword}")
        check(status is 200, captureEnterpriseId, captureUserId )
    )

    //{pre: initInfrastructureChain} Loggin and sets $datacenterId and $templateId
    val loginAndGetDefaultDatacenterAndTemplateChain =  
        exec(loginChain)
        .exec(http("GET_Datacenters")
            get("/api/admin/datacenters")
            header(ACCEPT, MT_DCS)
            check(status is 200, captureDatacenterId)
        )
        .exec(http("GET_Templates")
            get("/api/admin/enterprises/${enterpriseId}/datacenterrepositories/${datacenterId}/virtualmachinetemplates") queryParam("hypervisorTypeName", "VBOX")//${hypervisorType}")
            header(ACCEPT, MT_VMTS)
            check(status is 200, captureTemplateId)
        )
        .doIf( (s:Session) => exitIfNoDefined(s, "datacenterId")) { pause(0,1) }
        .doIf( (s:Session) => exitIfNoDefined(s, "templateId")) { pause(0,1) }
}