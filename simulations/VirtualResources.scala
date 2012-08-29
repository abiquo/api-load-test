import com.excilys.ebi.gatling.core.Predef._
import com.excilys.ebi.gatling.http.Headers.Names._
import com.excilys.ebi.gatling.http.Predef._
import AbiMediaTypes._
import AdminLogin._
import SetupInfrastructure._
import ReadVirtualResources._
import com.excilys.ebi.gatling.http.check.HttpCheck
import org.glassfish.grizzly.http.util.HttpStatus._
import com.excilys.ebi.gatling.http.request.builder.AbstractHttpRequestWithBodyBuilder
import jodd.util.StringUtil

class VirtualResources extends Simulation {

    val checkPollingPause = 5

	def isVirtualApplianceState(s:Session, state:String) = {
        println("\n waiting for " +  state + " -- " + s.getTypedAttribute[String]("virtualApplianceState")+"\n")
        s.getTypedAttribute[String]("virtualApplianceState").startsWith(state)
    }

    val checkVirtualApplianceState = chain
        .exec(http("GET_VirtualapplianceState")
            get("/api/cloud/virtualdatacenters/${virtualdatacenterId}/virtualappliances/${virtualapplianceId}/state")
            header(ACCEPT, MT_VAPP_STATE)
            check(  status is(200),
                    xpath("""/virtualApplianceState/power""") saveAs("virtualApplianceState") ) // DEPLOYED, NOT_DEPLOYED, NEEDS_SYNC, LOCKED, UNKONWN
        )        
        .pause(checkPollingPause)

    val addVirtualMachineChain = chain
         // Virtualmachine
        .exec(http("POST_Virtualmachine")
            post("/api/cloud/virtualdatacenters/${virtualdatacenterId}/virtualappliances/${virtualapplianceId}/virtualmachines")          
            header(ACCEPT, MT_VM) header(CONTENT_TYPE, MT_VM_NODE)
            fileBody("vm.xml", Map(
                "name"          -> "myVirtualmachine",
                "datacenterId"  -> "${datacenterId}",
                "templateId"    -> "${templateId}"))
            check( status is(201) )
        )//.pause(1,10)
   
    val deployVirtualApplianceChain = chain
        .exec(http("POST_Virtualappliance")
            post("/api/cloud/virtualdatacenters/${virtualdatacenterId}/virtualappliances")
            header(ACCEPT, MT_VAPP) header(CONTENT_TYPE, MT_VAPP)
            fileBody("vapp.xml", Map(
                "name"          -> "myVirtualappliance"))
            check(  status is(201), 
                    regex("""virtualappliances/(\d+)/""") saveAs("virtualapplianceId"))     
        )
        .doIf( (s:Session) => exitIfNoDefined(s, "virtualapplianceId"), chain.pause(0,1))
        .loop(addVirtualMachineChain).counterName("numVirtualmachine").times("${numVirtualMachinesInVapp}")

        .exec(http("deploy_Virtualappliance")
            post("/api/cloud/virtualdatacenters/${virtualdatacenterId}/virtualappliances/${virtualapplianceId}/action/deploy")
            header(ACCEPT, MT_ACCEPTED) header(CONTENT_TYPE, MT_VMTASK)
            fileBody("vmtask.xml", Map("force"  -> "true"))
            check( status is(202) )
        )
        .insertChain(checkVirtualApplianceState) // clear virtualMachineState from prev iterations
        .loop(checkVirtualApplianceState).asLongAs( (s:Session) => !isVirtualApplianceState(s, "DEPLOYED") )
 
        .pause(10,30) // enjoy your virtualappliance

        .exec(http("undeploy_Virtualappliance")
            post("/api/cloud/virtualdatacenters/${virtualdatacenterId}/virtualappliances/${virtualapplianceId}/action/undeploy")
            header(ACCEPT, MT_ACCEPTED) header(CONTENT_TYPE, MT_VMTASK)
            fileBody("vmtask.xml", Map("force" -> "true"))
            check( status is(202) )
        )
        .insertChain(checkVirtualApplianceState) // clear virtualMachineState from prev iterations
        .loop(checkVirtualApplianceState).asLongAs( (s:Session) => !isVirtualApplianceState(s, "NOT_DEPLOYED") )

        .exec(http("DEL_Virtualappliance")
            delete("/api/cloud/virtualdatacenters/${virtualdatacenterId}/virtualappliances/${virtualapplianceId}")
            header(ACCEPT, MT_XML)            
            check( status is(204) )
        ).pause(5,10) // wait before next loop
        

    val virtualResourcesScenario = scenario("deployVirtualAppliance")
            // read session vars: loginuser,loginpassword,datacenterId,templateId,virtualdatacenterId,numVirtualMachinesInVapp
            .feed(csv("virtualdatacenter.csv").circular)
            .insertChain(loginChain)
            //.insertChain(deployVirtualApplianceChain)
            .loop(deployVirtualApplianceChain) times 10 //during(5, MINUTES)

              
    def apply = {

        val urlBase = "http://10.60.1.223:80"
        val httpConf = httpConfig.baseURL(urlBase)

        List(
        virtualResourcesScenario.configure    users 10 ramp 60 protocolConfig httpConfig.baseURL(urlBase)
        )
    }
}