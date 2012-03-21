import com.excilys.ebi.gatling.core.Predef._
import com.excilys.ebi.gatling.http.Predef._
import com.excilys.ebi.gatling.script.GatlingSimulation
import java.util.UUID

class Simulation extends GatlingSimulation {
  
	val urlBase = "http://localhost:80"

	val MT_USER 	= """application/vnd.abiquo.user+xml; version=2.0"""
	val MT_DCS 		= """application/vnd.abiquo.datacenters+xml;version=2.0"""
	val MT_DC     	= """application/vnd.abiquo.datacenter+xml; version=2.0"""
	val MT_RACKS  	= """application/vnd.abiquo.racks+xml; version=2.0"""
  	val MT_RACK     = """application/vnd.abiquo.rack+xml; version=2.0"""
	val MT_MACHINE  = """application/vnd.abiquo.machine+xml; version=2.0"""
  	val MT_MACHINES = """application/vnd.abiquo.machines+xml; version=2.0"""
	val MT_RES_ENT 	= """application/vnd.abiquo.enterpriseresources+xml; version=2.0"""
	val MT_RES_VDC 	= """application/vnd.abiquo.virtualdatacentersresources+xml; version=2.0"""
	val MT_RES_VAPP = """application/vnd.abiquo.virtualappsresources+xml; version=2.0"""
	val MT_CLOUDUSAGE="""application/vnd.abiquo.cloudusage+xml; version=2.0"""
	val MT_DC_REPO 	= """application/vnd.abiquo.datacenterrepository+xml; version=2.0"""
	val MT_VDCS 	= """application/vnd.abiquo.virtualdatacenters+xml; version=2.0"""
	val MT_VDC 		= """application/vnd.abiquo.virtualdatacenter+xml; version=2.0"""
	val MT_VAPPS 	= """application/vnd.abiquo.virtualappliances+xml; version=2.0"""
	val MT_VAPP 	= """application/vnd.abiquo.virtualappliance+xml; version=2.0"""
	val MT_VMS_EXTENDED = """application/vnd.abiquo.virtualmachineswithnodeextended+xml; version=2.0"""
	val MT_VM_EXTENDED 	= """application/vnd.abiquo.virtualmachinewithnodeextended+xml; version=2.0"""
	val MT_VM_NODE 	= """application/vnd.abiquo.virtualmachinewithnode+xml; version=2.0"""
	val MT_VMTS 	= """application/vnd.abiquo.virtualmachinetemplates+xml; version=2.0"""
	val MT_VMT 		= """application/vnd.abiquo.virtualmachinetemplate+xml; version=2.0"""
	val MT_VM 		= """application/vnd.abiquo.virtualmachine+xml; version=2.0"""
	val MT_VMS 		= """application/vnd.abiquo.virtualmachines+xml; version=2.0"""
	val MT_VMSTATE 	= """application/vnd.abiquo.virtualmachinestate+xml;version=2.0"""
	val MT_ACCEPTED = """application/vnd.abiquo.acceptedrequest+xml; version=2.0"""
	val MT_TASKS 	= """application/vnd.abiquo.tasks+xml;version=2.0"""
	val MT_TASK 	= """application/vnd.abiquo.task+xml;version=2.0"""
	val MT_VOLS 	= """application/vnd.abiquo.iscsivolumes+xml; version=2.0"""
	val MT_VLAN 	= """application/vnd.abiquo.vlan+xml; version=2.0""" 	  
	val MT_XML 		= """application/xml"""
	val MT_PLAIN 	= """text/plain"""
	
	  
    //Loggin and sets $enterpriseId, $currentUserId
	val loginChain = chain.exec(http("login")
			get("/api/login")
			header("Accept", MT_USER)
			basicAuth("admin", "xabiquo")
			check(	status.eq(200),
					regex("""enterprises/(\d+)/users/""") saveAs("enterpriseId"),
					regex("""users/(\d+)""") saveAs("currentUserId") 
			)
		)

	//{pre: initInfrastructureChain} Loggin and sets $datacenterId and $templateId
	val loginAndGetDatacenterAndTemplateChain = chain
	 	.insertChain(loginChain)
	 	.exec(http("GET_Datacenters")
	 	    get("/api/admin/datacenters")
		    header("Accept", MT_DCS)
		    check(	status.eq(200), regex("""datacenters/(\d+)""") find(0) saveAs("datacenterId") )
	 	)
	 	.exec(http("GET_Templates")
            get("/api/admin/enterprises/${enterpriseId}/datacenterrepositories/${datacenterId}/virtualmachinetemplates") queryParam("hypervisorTypeName", "VMX_04")//${hypervisorType}")
         	header("Accept", MT_VMTS)
         	check(	status.eq(200), regex("""virtualmachinetemplates/(\d+)""") find(0) saveAs("templateId") )
        )

	//Create a Datacenter a Rack a Machine and refresh the DatacenterRepository: sets $datacenterId, $rackId, $machineId, $templateId
 	val initInfrastructureChain = chain
 	 	// Datacenter
 	 	.exec(http("POST_Datacenter")
            post("/api/admin/datacenters")
            header("Accept", MT_DC) header("Content-Type", MT_DC)
            fileBody("datacenter.xml",
                Map("remoteservicesIp" -> "${remoteservicesIp}"))
            check(	status.eq(201), regex("""datacenters/(\d+)" """) saveAs("datacenterId") )           
        )
 	 	.exec(http("GET_Datacenters")
		    get("/api/admin/datacenters")
		    header("Accept", MT_DCS)
		    check(	status.eq(200), regex("""datacenters/${datacenterId}""") exists )
		)
		// Rack
		.exec(http("POST_Rack")
            post("/api/admin/datacenters/${datacenterId}/racks")
            header("Accept", MT_RACK) header("Content-Type", MT_RACK)
            fileBody("rack.xml",
                Map("name" -> "myrack"))
            check(	status.eq(201), regex("""racks/(\d+)" """) saveAs("rackId") )
        )
		.exec(http("GET_Racks")
		    get("/api/admin/datacenters/${datacenterId}/racks")
		    header("Accept", MT_RACKS)
		    check(	status.eq(200), regex("""racks/${rackId}""") exists	 )
		)
		// Machine (do not use nodecollector) from feeder 'infrastructure.csv'
		// get("/api/admin/datacenters/${datacenter}/action/hypervisor") queryParam("ip", "10.60.1.120")
		// get("/api/admin/datacenters/2/action/discoversingle") queryParam("ip", "10.60.1.120") ....
        .exec(http("POST_Machine")
            post("/api/admin/datacenters/${datacenterId}/racks/${rackId}/machines")
            header("Accept", MT_MACHINE) header("Content-Type", MT_MACHINE)
            fileBody("machine.xml", Map(
				"hypervisorIp" 		-> "${hypervisorIp}", 
				"hypervisorType" 	-> "${hypervisorType}")
            )
			check(	status.eq(201), regex("""machines/(\d+)""") saveAs("machineId") )
        )
        .exec(http("GET_Machines")
		    get("/api/admin/datacenters/${datacenterId}/racks/${rackId}/machines")
		    header("Accept", MT_MACHINES)
		    check(	status.eq(200), regex("""machines/${machineId}""") exists	)
		)
		// Refresh the DatacenterRespository
		.exec(http("refreshDatacenterRepo")
            get("/api/admin/enterprises/${enterpriseId}/datacenterrepositories/${datacenterId}") queryParam("refresh", "true") queryParam("usage", "true")
            header("Accept", MT_DC_REPO)
            check(	status.eq(200)	)
        )
        .pause(5) // wait for AM notifications to populate the VirtualMachineTemplates
		.exec(http("GET_Templates")
            get("/api/admin/enterprises/${enterpriseId}/datacenterrepositories/${datacenterId}/virtualmachinetemplates") queryParam("hypervisorTypeName", "${hypervisorType}")
         	header("Accept", MT_VMTS)
         	check(	status.eq(200), regex("""virtualmachinetemplates/(\d+)""") find(0) saveAs("templateId") )
        )
		.exec(http("GET_Template")
            get("/api/admin/enterprises/${enterpriseId}/datacenterrepositories/${datacenterId}/virtualmachinetemplates/${templateId}")
            header("Accept", MT_VMT)
         	check(	status.eq(200), regex("""virtualmachinetemplates/${templateId}""") exists )
        )

	val readChain = chain
	  	.exec(http("stadistics_cloudUsage")
            get("/api/admin/statistics/cloudusage/actions/total")
			header("Accept", MT_CLOUDUSAGE)
			check(status.eq(200))
		)
	  	.exec(http("stadistics_Enter")
			get("/api/admin/statistics/enterpriseresources/${enterpriseId}")
			header("Accept", MT_RES_ENT)
			check(status.eq(200))
		)
		.exec(http("stadistics_Vdc")
			get("/api/admin/statistics/vdcsresources") queryParam("identerprise", "${enterpriseId}")
			header("Accept", MT_RES_VDC)
			check(status.eq(200))
		)
		.exec(http("stadistics_Vapp")
			get("/api/admin/statistics/vappsresources") queryParam("identerprise", "${enterpriseId}")
			header("Accept", MT_RES_VAPP)
			check(status.eq(200))
		)
		// enterprise action links
		.exec(http("GET_Virtualdatacenters_byEnter")
			get("/api/admin/enterprises/${enterpriseId}/action/virtualdatacenters") queryParam("startwith", "0") queryParam("limit", "25") queryParam("by", "name") queryParam("asc", "true")
			header("Accept", MT_VDCS)
			check(status.eq(200))
		)
		.exec(http("GET_Virtualappliances_byEnter")
			get("/api/admin/enterprises/${enterpriseId}/action/virtualappliances") queryParam("startwith", "0") queryParam("limit", "25") queryParam("by", "name") queryParam("asc", "true")
			header("Accept", MT_VAPPS)
			check(status.eq(200))
		)
		.exec(http("GET_Virtualmachines_byEnter")
			get("/api/admin/enterprises/${enterpriseId}/action/virtualmachines") queryParam("startwith", "0") queryParam("limit", "25") queryParam("by", "name") queryParam("asc", "true")
			header("Accept", MT_VMS)
			check(status.eq(200))
		)
		.pause(1,2) // wait before next loop
		// get("/api/admin/enterprises/1/limits")
		// get("/api/config/categories")
        // get("/api/cloud/virtualdatacenters/4/privatenetworks/1") header("Accept", MT_VLAN)
		// get("/api/cloud/virtualdatacenters/4/virtualappliances/3/virtualmachines/1/storage/volumes") header("Accept", MT_VOLS)

	val writeChain = chain
		// Virtualdatacenter
		.exec(http("POST_Virtualdatacenter")
			post("/api/cloud/virtualdatacenters")
			header("Accept", MT_VDC) header("Content-Type", MT_VDC)
			fileBody("vdc.xml", Map(
				"name" 			-> "myVirtualdatacenter", 
				"enterpriseId" 	-> "${enterpriseId}",
				"datacenterId" 	-> "${datacenterId}")
			)
			check(	status.eq(201), 
					regex("""virtualdatacenters/(\d+)/""") saveAs("vdcId"))
		)
		.exec(http("GET_Virtualdatacenters")
		    get("/api/cloud/virtualdatacenters/${vdcId}")
		    header("Accept", MT_VDC)
		    check( status.eq(200),
		        	regex("""virtualdatacenters/${vdcId}""") exists
		    )		    
		)
		// Virtualappliance
		.exec(http("POST_Virtualappliance")
		    post("/api/cloud/virtualdatacenters/${vdcId}/virtualappliances")
			header("Accept", MT_VAPP) header("Content-Type", MT_VAPP)
			fileBody("vapp.xml", Map(
				"name" 			-> "myVirtualappliancd"))
			check(	status.eq(201), 
					regex("""virtualappliances/(\d+)/""") saveAs("vappId"))						
		)
		.exec(http("GET_Virtualappliances")
		    get("/api/cloud/virtualdatacenters/${vdcId}/virtualappliances/${vappId}")
		    header("Accept", MT_VAPP)
		    check( status.eq(200),
		        	regex("""virtualdatacenters/${vdcId}/virtualappliances/${vappId}""") exists
		    )		    
		)
		// Virtualmachine
		.exec(http("POST_Virtualmachine")
		    post("/api/cloud/virtualdatacenters/${vdcId}/virtualappliances/${vappId}/virtualmachines")			
		    header("Accept", MT_VM) header("Content-Type", MT_VM_NODE)
			fileBody("vm.xml", Map(
				"name" 			-> "myVirtualmachine", 
				"templateId" 	-> "${templateId}"))
			check(	status.eq(201), 
					regex("""virtualmachines/(\d+)/""") saveAs("vmId"))
		)
		.exec(http("GET_Virtualmachines")
		    get("/api/cloud/virtualdatacenters/${vdcId}/virtualappliances/${vappId}/virtualmachines/${vmId}")
		    header("Accept", MT_VM)
		    check( status.eq(200),
		        	regex("""virtualdatacenters/${vdcId}/virtualappliances/${vappId}/virtualmachines/${vmId}""") exists
		    )		    
		)
		// VirtualmachineTask
		.exec(http("GET_VirtualmachineTasks")
			get("/api/cloud/virtualdatacenters/${vdcId}/virtualappliances/${vappId}/virtualmachines/${vmId}/tasks")
			header("Accept", MT_TASKS)
			check( status.eq(200) )
		)
		// Pricing		
		.exec(http("GET_VirtualappliancePrice")
			get("/api/cloud/virtualdatacenters/${vdcId}/virtualappliances/${vappId}/action/price")
		    header("Accept",MT_PLAIN)
		    check( status.eq(200) )
		)
		//.insertChain(readChain) 		
		//.exec(http("deployVapp")
		//    post("/api/cloud/virtualdatacenters/${vdcId}/virtualappliances/${vappId}/action/deploy")
		//    header("Accept", MT_ACCEPTED) header("Content-Type", MT_TASK)
		//    fileBody("vmtask.xml")
		//)
		//.exec(http("undeployVapp")
		//   post("/api/cloud/virtualdatacenters/4/virtualappliances/3/action/undeploy")
		//	header("Accept", MT_ACCEPTED) header("Content-Type", MT_TASK)
		//	fileBody("vmtask.xml")
		//)		
		.exec(http("DEL_Virtualmachine")
			delete("/api/cloud/virtualdatacenters/${vdcId}/virtualappliances/${vappId}/virtualmachines/${vmId}")
			header("Accept", MT_XML)			
			check( status.eq(204) )
		)
		.exec(http("DEL_Virtualappliance")
			delete("/api/cloud/virtualdatacenters/${vdcId}/virtualappliances/${vappId}")
			header("Accept", MT_XML)			
			check( status.eq(204) )
		)
		.exec(http("DEL_Virtualdatacenter")
			delete("/api/cloud/virtualdatacenters/${vdcId}")
			header("Accept", MT_XML)			
			check( status.eq(204) )
		)
		.pause(1,2) // wait before next loop


	val init_scn = scenario("initInfrastructure")
		.insertChain(loginChain)
		.feed(csv("infrastructure.csv"))
		.insertChain(initInfrastructureChain)
		
	val read_scn = scenario("vdc_reads")
			.insertChain(loginAndGetDatacenterAndTemplateChain)
			.loop(readChain).during(5, MINUTES)

	val write_scn = scenario("vdc_writes")
			.insertChain(loginAndGetDatacenterAndTemplateChain)
			//.insertChain(writeChain)
			.loop(writeChain).during(2, MINUTES) // times(2) 

	
	runSimulation(
		init_scn.configure 				users 1  				protocolConfig httpConfig.baseURL(urlBase),
		read_scn.configure	delay 60 	users 50	ramp 60		protocolConfig httpConfig.baseURL(urlBase),		
		write_scn.configure delay 160  	users 50 	ramp 60  	protocolConfig httpConfig.baseURL(urlBase)
		)
}