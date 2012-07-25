import com.excilys.ebi.gatling.http.Predef._
import com.excilys.ebi.gatling.http.Headers.Names._
import com.excilys.ebi.gatling.core.Predef._


import AbiMediaTypes._

object AdminLogin{
    
    val loginChain = chain.exec(
        http("getlogin")
        get("/api/login")
        header(ACCEPT, MT_USER)
        basicAuth("admin", "xabiquo")           
        check(  status is(200),
                regex("""enterprises/(\d+)/users/""") saveAs("enterpriseId"),
                regex("""users/(\d+)""") saveAs("currentUserId") 
        )           
    )   
}