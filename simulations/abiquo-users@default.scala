import com.excilys.ebi.gatling.core.Predef._
import com.excilys.ebi.gatling.http.Predef._
import com.excilys.ebi.gatling.script.GatlingSimulation
import java.lang.System

class Simulation extends GatlingSimulation {
  
    val urlBase = "http://localhost:80"
    
    val MT_USER     = """application/vnd.abiquo.user+xml; version=2.0"""
    val MT_USERS    = """application/vnd.abiquo.users+xml; version=2.0"""
    val MT_XML      = """application/xml"""
      
    val loginChain = chain.exec(
            http("getlogin")
            get("/api/login")
            header("Accept", MT_USER)
            basicAuth("admin", "xabiquo")           
            check(  status.eq(200),
                    regex("""enterprises/(\d+)/users/""") saveAs("enterpriseId"),
                    regex("""users/(\d+)""") saveAs("currentUserId") 
            )           
        )
        
    val readUsersChain = chain.exec(http("GET_Users")
            get("/api/admin/enterprises/${enterpriseId}/users") queryParam("numResults", "100") queryParam("page", "0")  queryParam("desc", "false") queryParam("orderBy", "name") queryParam("connected", "false")
            header("Accept", MT_USERS)
            check( status.eq(200) )
            
        )
        .pause(1,2) // wait before next loop
        
    val crudUserChain = chain.exec(http("POST_User")
            post("/api/admin/enterprises/${enterpriseId}/users")
            header("Accept", MT_USER) header("Content-Type", MT_USER)
            fileBody("user.xml", 
                Map("lusername" -> "${lusername}", 
                    "lusernick" -> "${lusername}"))
            check(  status.eq(201), 
                    regex("""users/(\d+)""") saveAs("luserId") )
        )
        .exec(http("GET_User")
            get("/api/admin/enterprises/${enterpriseId}/users/${luserId}")
            header("Accept", MT_USER)
            check(  status.eq(200),
                    regex("""users/${luserId}""") exists )
        )
        .exec(http("PUT_User")
            put("/api/admin/enterprises/${enterpriseId}/users/${luserId}")
            header("Accept", MT_USER) header("Content-Type", MT_USER)
            fileBody("user.xml",
                Map("lusername" -> "${lusername}modified",
                    "lusernick" -> "${lusername}"))
            check(  status.eq(200),
                    regex("""${lusername}modified""") exists )
        )
        .exec(http("DEL_User")
            delete("/api/admin/enterprises/${enterpriseId}/users/${luserId}")
            header("Accept", MT_XML)            
            check( status.eq(204) )
        )
        .pause(1,2) // wait before next loop

    val write_scn = scenario("crud User")
            .insertChain(loginChain)
            .feed(csv("users.csv")) // max 2000 users
            //.insertChain(crudUserChain)
            .loop(crudUserChain)    during(20, MINUTES) //times(100)

    val read_sn = scenario("get Users")         
            .insertChain(loginChain)
            .loop(readUsersChain)   during(20, MINUTES)

    runSimulation(
        write_scn.configure users 50    ramp 60 protocolConfig httpConfig.baseURL(urlBase),
        read_sn.configure   users 50    ramp 60 protocolConfig httpConfig.baseURL(urlBase)
    )
}
