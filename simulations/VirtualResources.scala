import com.excilys.ebi.gatling.core.Predef._
import com.excilys.ebi.gatling.http.Headers.Names._
import com.excilys.ebi.gatling.http.Predef._

import AbiMediaTypes._
import AdminLogin._

class VirtualResources extends Simulation {

    
    //{pre: initInfrastructureChain} Loggin and sets $datacenterId and $templateId
    val loginAndGetDatacenterAndTemplateChain = chain
        .insertChain(loginChain)
        .exec(http("GET_Datacenters")
            get("/api/admin/datacenters")
            header(ACCEPT, MT_DCS)
            check(  status is(200), regex("""datacenters/(\d+)""") find(0) saveAs("datacenterId") )
        )
        .exec(http("GET_Templates")
            get("/api/admin/enterprises/${enterpriseId}/datacenterrepositories/${datacenterId}/virtualmachinetemplates") queryParam("hypervisorTypeName", "VBOX")//${hypervisorType}")
            header(ACCEPT, MT_VMTS)
            check(  status is(200), regex("""virtualmachinetemplates/(\d+)""") find(0) saveAs("templateId") )
        )
        //.exec(
        //    (s:Session) => println("________________________________________________________"+s.getAttribute("templateId"));
        //    s
        //)
        


    //Create a Datacenter a Rack a Machine and refresh the DatacenterRepository: sets $datacenterId, $rackId, $machineId, $templateId
    val initInfrastructureChain = chain
        // Datacenter
        .exec(http("POST_Datacenter")
            post("/api/admin/datacenters")
            header(ACCEPT, MT_DC) header(CONTENT_TYPE, MT_DC)
            fileBody("datacenter.xml",
                Map("remoteservicesIp" -> "${remoteservicesIp}"))
            check(  status is(201), regex("""datacenters/(\d+)" """) saveAs("datacenterId") )           
        )
        .exec(http("GET_Datacenters")
            get("/api/admin/datacenters")
            header(ACCEPT, MT_DCS)
            check(  status is(200), regex("""datacenters/${datacenterId}""") exists )
        )
        // Rack
        .exec(http("POST_Rack")
            post("/api/admin/datacenters/${datacenterId}/racks")
            header(ACCEPT, MT_RACK) header(CONTENT_TYPE, MT_RACK)
            fileBody("rack.xml",
                Map("name" -> "myrack"))
            check(  status is(201), regex("""racks/(\d+)" """) saveAs("rackId") )
        )
        .exec(http("GET_Racks")
            get("/api/admin/datacenters/${datacenterId}/racks")
            header(ACCEPT, MT_RACKS)
            check(  status is(200), regex("""racks/${rackId}""") exists  )
        )
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
            check(  status is(201), regex("""machines/(\d+)""") saveAs("machineId") )
        )
        .exec(http("GET_Machines")
            get("/api/admin/datacenters/${datacenterId}/racks/${rackId}/machines")
            header(ACCEPT, MT_MACHINES)
            check(  status is(200), regex("""machines/${machineId}""") exists   )
        )
        // Refresh the DatacenterRespository
        .exec(http("refreshDatacenterRepo")
            get("/api/admin/enterprises/${enterpriseId}/datacenterrepositories/${datacenterId}") queryParam("refresh", "true") queryParam("usage", "true")
            header(ACCEPT, MT_DC_REPO)
            check(  status is(200)  )
        )
        .pause(5) // wait for AM notifications to populate the VirtualMachineTemplates
        .exec(http("GET_Templates")
            get("/api/admin/enterprises/${enterpriseId}/datacenterrepositories/${datacenterId}/virtualmachinetemplates") queryParam("hypervisorTypeName", "${hypervisorType}")
            header(ACCEPT, MT_VMTS)
            check(  status is(200), regex("""virtualmachinetemplates/(\d+)""") find(0) saveAs("templateId") )
        )
        .exec(http("GET_Template")
            get("/api/admin/enterprises/${enterpriseId}/datacenterrepositories/${datacenterId}/virtualmachinetemplates/${templateId}")
            header(ACCEPT, MT_VMT)
            check(  status is(200), regex("""virtualmachinetemplates/${templateId}""") exists )
        )

    val readChain = chain
        .exec(http("stadistics_cloudUsage")
            get("/api/admin/statistics/cloudusage/actions/total")
            header(ACCEPT, MT_CLOUDUSAGE)
            check(status is(200))
        )
        .exec(http("stadistics_Enter")
            get("/api/admin/statistics/enterpriseresources/${enterpriseId}")
            header(ACCEPT, MT_RES_ENT)
            check(status is(200))
        )
        .exec(http("stadistics_Vdc")
            get("/api/admin/statistics/vdcsresources") queryParam("identerprise", "${enterpriseId}")
            header(ACCEPT, MT_RES_VDC)
            check(status is(200))
        )
        .exec(http("stadistics_Vapp")
            get("/api/admin/statistics/vappsresources") queryParam("identerprise", "${enterpriseId}")
            header(ACCEPT, MT_RES_VAPP)
            check(status is(200))
        )
        // enterprise action links
        .exec(http("GET_Virtualdatacenters_byEnter")
            get("/api/admin/enterprises/${enterpriseId}/action/virtualdatacenters") queryParam("startwith", "0") queryParam("limit", "25") queryParam("by", "name") queryParam("asc", "true")
            header(ACCEPT, MT_VDCS)
            check(status is(200))
        )
        .exec(http("GET_Virtualappliances_byEnter")
            get("/api/admin/enterprises/${enterpriseId}/action/virtualappliances") queryParam("startwith", "0") queryParam("limit", "25") queryParam("by", "name") queryParam("asc", "true")
            header(ACCEPT, MT_VAPPS)
            check(status is(200))
        )
        .exec(http("GET_Virtualmachines_byEnter")
            get("/api/admin/enterprises/${enterpriseId}/action/virtualmachines") queryParam("startwith", "0") queryParam("limit", "25") queryParam("by", "name") queryParam("asc", "true")
            header(ACCEPT, MT_VMS)
            check(status is(200))
        )
        .pause(1,2) // wait before next loop
        // get("/api/admin/enterprises/1/limits")
        // get("/api/config/categories")
        // get("/api/cloud/virtualdatacenters/4/privatenetworks/1") header(ACCEPT, MT_VLAN)
        // get("/api/cloud/virtualdatacenters/4/virtualappliances/3/virtualmachines/1/storage/volumes") header(ACCEPT, MT_VOLS)

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
        .loop(checkVmStateChain).asLongAs( (s: Session) => s.getTypedAttribute[String]("taskState").startsWith("PENDING") || s.getTypedAttribute[String]("taskState").startsWith("STARTED") )    
 
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
        .loop(checkVmStateChain).asLongAs( (s: Session) => s.getTypedAttribute[String]("taskState").startsWith("PENDING") || s.getTypedAttribute[String]("taskState").startsWith("STARTED") )    

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



    val init_scn = scenario("initInfrastructure")
        .insertChain(loginChain)
        .feed(csv("infrastructure.csv"))
        .insertChain(initInfrastructureChain)
        
    val read_scn = scenario("vdc_reads")
            .insertChain(loginAndGetDatacenterAndTemplateChain)
            .loop(readChain) during(5, MINUTES)

    val write_scn = scenario("vdc_writes")
            .insertChain(loginAndGetDatacenterAndTemplateChain)
            //.insertChain(writeChain)
            .loop(writeChain) during(12, HOURS)

              
    def apply = {

        val urlBase = "http://10.60.1.223:80"
        val httpConf = httpConfig.baseURL(urlBase)

        List(
        //init_scn.configure              users 1                 protocolConfig httpConfig.baseURL(urlBase)
        //read_scn.configure      users 100         ramp 100     protocolConfig httpConfig.baseURL(urlBase),     
        write_scn.configure     users 100         ramp 600     protocolConfig httpConfig.baseURL(urlBase)
        )
    }
 
}