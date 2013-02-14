import com.excilys.ebi.gatling.core.Predef._
import com.excilys.ebi.gatling.http.Headers.Names._
import com.excilys.ebi.gatling.http.Predef._
import AbiquoAPI._
import API._
import bootstrap._
import akka.util.duration._

class CrudUser  extends Simulation {

	val duration = 29 minutes;

	val crudUser = exec(createUser)
		.exitHereIfFailed
		.exec(getUser)
		.exec(modifyUser)
		.exec(getUser)
		.exec(delUser)
		.exec(getNoUser)

	val write = scenario("crudUser")
			.feed(loginFeed)
			.exec(login)
			.feed(csv("users.csv").circular)
			.during(duration) {
			//.repeat(100) {
				crudUser
				.pause(0, 2) // wait before next loop
			}

	val read = scenario("readUsers")
			.feed(loginFeed)
			.exec(login)
			.during(duration) {
				getUsers
				.pause(0, 2) // wait before next loop
			}

	setUp(
            write users(numUsers) ramp( rampTime seconds)                 protocolConfig httpConf,
            read  users(60)       delay(rampTime seconds) ramp(1 minutes) protocolConfig httpConf
	)
}
