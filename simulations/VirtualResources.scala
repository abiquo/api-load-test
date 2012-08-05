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

	def sessionContainsTaskState(s:Session) = {
        s.getTypedAttribute[String]("taskState").startsWith("PENDING") || s.getTypedAttribute[String]("taskState").startsWith("STARTED")     
    }

    val checkVmStateChain = chain
        .exec(http("GET_VirtualmachineTask")
            get("/api/cloud/virtualdatacenters/${vdcId}/virtualappliances/${vappId}/virtualmachines/${vmId}/tasks/${taskId}")
            header(ACCEPT, MT_TASK)
            check(  status is(200),
                    xpath("""/task/state""") saveAs("taskState") )

        )
        .pause(5,7)

    val writeChain = chain
        // Virtualdatacenter
        .exec(http("POST_Virtualdatacenter")
            post("/api/cloud/virtualdatacenters")
            header(ACCEPT, MT_VDC) header(CONTENT_TYPE, MT_VDC)
            fileBody("vdc.xml", Map(
                "name"          -> "myVirtualdatacenter", 
                "enterpriseId"  -> "${enterpriseId}",
                "datacenterId"  -> "${datacenterId}")
            )
            check(  status is(201), 
                    regex("""virtualdatacenters/(\d+)/""") saveAs("vdcId"))
        ).pause(1,10)
        .exec(http("GET_Virtualdatacenter")
            get("/api/cloud/virtualdatacenters/${vdcId}")
            header(ACCEPT, MT_VDC)
            check( status is(200),
                    regex("""virtualdatacenters/${vdcId}""") exists
            )           
        ).pause(1,10)
        // Virtualappliance
        .exec(http("POST_Virtualappliance")
            post("/api/cloud/virtualdatacenters/${vdcId}/virtualappliances")
            header(ACCEPT, MT_VAPP) header(CONTENT_TYPE, MT_VAPP)
            fileBody("vapp.xml", Map(
                "name"          -> "myVirtualappliancd"))
            check(  status is(201), 
                    regex("""virtualappliances/(\d+)/""") saveAs("vappId"))                     
        ).pause(1,10)
        .exec(http("GET_Virtualappliance")
            get("/api/cloud/virtualdatacenters/${vdcId}/virtualappliances/${vappId}")
            header(ACCEPT, MT_VAPP)
            check( status is(200),
                    regex("""virtualdatacenters/${vdcId}/virtualappliances/${vappId}""") exists
            )           
        ).pause(1,10)
        // Virtualmachine
        .exec(http("POST_Virtualmachine")
            post("/api/cloud/virtualdatacenters/${vdcId}/virtualappliances/${vappId}/virtualmachines")          
            header(ACCEPT, MT_VM) header(CONTENT_TYPE, MT_VM_NODE)
            fileBody("vm.xml", Map(
                "name"          -> "myVirtualmachine", 
                "templateId"    -> "${templateId}"))
            check(  status is(201), 
                    regex("""virtualmachines/(\d+)/""") saveAs("vmId"))
        ).pause(1,10)
        .exec(http("GET_Virtualmachine")
            get("/api/cloud/virtualdatacenters/${vdcId}/virtualappliances/${vappId}/virtualmachines/${vmId}")
            header(ACCEPT, MT_VM)
            check( status is(200),
                    regex("""virtualdatacenters/${vdcId}/virtualappliances/${vappId}/virtualmachines/${vmId}""") exists
            )           
        ).pause(1,10)
        // Pricing      
        .exec(http("GET_VirtualappliancePrice")
            get("/api/cloud/virtualdatacenters/${vdcId}/virtualappliances/${vappId}/action/price")
            header(ACCEPT,MT_PLAIN)
            check( status is(200) )
        ).pause(1,10)
        
        // Deploy the Virtualmachine
        .exec(http("deploy_Virtualmachine")
            post("/api/cloud/virtualdatacenters/${vdcId}/virtualappliances/${vappId}/virtualmachines/${vmId}/action/deploy")
            header(ACCEPT, MT_ACCEPTED) //header(CONTENT_TYPE, MT_TASK)
            fileBody("vmtask.xml", Map("force"  -> "true"))
            check( status is(202), 
                    regex("""/tasks/([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})""") saveAs("taskId"),
                    // taskState is finished, need to initialize again
                    regex("""/tasks/([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})""") saveAs("taskState"))
        )
        .loop(checkVmStateChain).asLongAs( (s:Session) => sessionContainsTaskState(s) )    
 
        .pause(60,600) // enjoy your vm during 1min 

        .exec(http("undeploy_Virtualmachine")
            post("/api/cloud/virtualdatacenters/${vdcId}/virtualappliances/${vappId}/virtualmachines/${vmId}/action/undeploy")
            header(ACCEPT, MT_ACCEPTED) header(CONTENT_TYPE, MT_VMTASK)
            fileBody("vmtask.xml", Map("force" -> "true"))
            check(  status is(202),
                    regex("""/tasks/([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})""") saveAs("taskId"),
                    // taskState is finished, need to initialize again
                    regex("""/tasks/([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})""") saveAs("taskState"))
        )
        .loop(checkVmStateChain).asLongAs( (s:Session) => sessionContainsTaskState(s) )    

        .pause(60,600) 

        // Delete all    
        .exec(http("DEL_Virtualmachine")
            delete("/api/cloud/virtualdatacenters/${vdcId}/virtualappliances/${vappId}/virtualmachines/${vmId}")
            header(ACCEPT, MT_XML)            
            check( status is(204) )
        ).pause(10,100)
        .exec(http("DEL_Virtualappliance")
            delete("/api/cloud/virtualdatacenters/${vdcId}/virtualappliances/${vappId}")
            header(ACCEPT, MT_XML)            
            check( status is(204) )
        ).pause(10,100)
        .exec(http("DEL_Virtualdatacenter")
            delete("/api/cloud/virtualdatacenters/${vdcId}")
            header(ACCEPT, MT_XML)            
            check( status is(204) )
        )
        .pause(300) // wait before next loop


    val virtualResourcesScenario = scenario("vdc_writes")
            .insertChain(loginAndGetDefaultDatacenterAndTemplateChain)
            .insertChain(writeChain)
            //.loop(writeChain) during(5, MINUTES)

              
    def apply = {

        val urlBase = "http://10.60.1.223:80"
        val httpConf = httpConfig.baseURL(urlBase)

        List(
        setupInfrastructureScenario.configure   users 1                        protocolConfig httpConfig.baseURL(urlBase),
        readVirtualResourcesScenario.configure  users 1         ramp 10        protocolConfig httpConfig.baseURL(urlBase),     
        virtualResourcesScenario.configure      users 1         ramp 20        protocolConfig httpConfig.baseURL(urlBase)
        )
    }
 
}