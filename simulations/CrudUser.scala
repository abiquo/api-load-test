import com.excilys.ebi.gatling.core.Predef._
import com.excilys.ebi.gatling.http.Headers.Names._
import com.excilys.ebi.gatling.http.Predef._

import AbiquoAPI._
import AdminLogin._

class CrudUser extends Simulation {

	// configure

    val baseUrl  = System.getProperty("baseUrl","http://localhost:80")

	// end configure

		  
	val readUsersChain = chain.exec(http("GET_Users")
			get("/api/admin/enterprises/${enterpriseId}/users") queryParam("numResults", "100") queryParam("page", "0")  queryParam("desc", "false") queryParam("orderBy", "name") queryParam("connected", "false")
			header(ACCEPT, MT_USERS)
			check( status is(200) )            
		)
		.pause(1,2) // wait before next loop
		
	val crudUserChain = chain.exec(http("POST_User")
			post("/api/admin/enterprises/${enterpriseId}/users")
			header(ACCEPT, MT_USER) header(CONTENT_TYPE, MT_USER)
			fileBody("user.xml", 
				Map("lusername" -> "${lusername}", 
					"lusernick" -> "${lusername}"))
			check(  status is(201), 
					regex("""users/(\d+)""") saveAs("luserId") )
		)
		.exec(http("GET_User")
			get("/api/admin/enterprises/${enterpriseId}/users/${luserId}")
			header(ACCEPT, MT_USER)
			check(  status is(200),
					regex("""users/${luserId}""") exists )
		)
		.exec(http("PUT_User")
			put("/api/admin/enterprises/${enterpriseId}/users/${luserId}")
			header(ACCEPT, MT_USER) header(CONTENT_TYPE, MT_USER)
			fileBody("user.xml",
				Map("lusername" -> "${lusername}modified",
					"lusernick" -> "${lusername}"))
			check(  status is(200),
					regex("""${lusername}modified""") exists )
		)
		.exec(http("DEL_User")
			delete("/api/admin/enterprises/${enterpriseId}/users/${luserId}")
			header(ACCEPT, MT_XML)            
			check( status is(204) )
		)
		.pause(1,2) // wait before next loop


	val write_scn = scenario("crud User")
			.insertChain(loginChain)
			.feed(csv("users.csv").circular) 
			//.insertChain(crudUserChain)
			.loop(crudUserChain)    times(100) //during(20, MINUTES)

	val read_sn = scenario("get Users")         
			.insertChain(loginChain)
			.loop(readUsersChain)   during(5, MINUTES)

	def apply = {

		val httpConf = httpConfig.baseURL(baseUrl)

		List(
		read_sn.configure   users 100              ramp 100 protocolConfig httpConf,
		write_scn.configure users 100  delay 101   ramp 100 protocolConfig httpConf
		)
	}
}
