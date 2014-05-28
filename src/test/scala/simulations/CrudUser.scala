import com.excilys.ebi.gatling.core.Predef._
import com.excilys.ebi.gatling.http.Headers.Names._
import com.excilys.ebi.gatling.http.Predef._
import AbiquoAPI._
import API._
import bootstrap._
import akka.util.duration._

class CrudUser  extends Simulation {

	val duration = 1 minutes;

	val crudUser = exec(createUser)
		.exitHereIfFailed
		.exec(getUser)
		.exec(modifyUser)
		.exec(getUser)
		.exec(delUser)
		.exec(getNoUser)

	val loginFeed = csv("login.csv").circular

	val write = scenario("crudUser")
			.feed(loginFeed)
			.exec(login)
			.exitHereIfFailed
			.feed(csv("users.csv").circular)
			.during(duration) {
				crudUser
			}

	val read = scenario("readUsers")
			.feed(loginFeed)
			.exec(login)
			.exitHereIfFailed
			.during(duration) {
				getUsers
			}

	setUp(
            write users(numUsers) ramp( rampTime seconds)                 protocolConfig httpConf
            //,read  users(60)       delay(rampTime seconds) ramp(1 minutes) protocolConfig httpConf
	)
}
