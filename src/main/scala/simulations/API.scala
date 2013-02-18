import com.excilys.ebi.gatling.core.Predef._
import com.excilys.ebi.gatling.http.Predef._
import com.excilys.ebi.gatling.http.check.HttpCheck
import com.excilys.ebi.gatling.http.Headers.Names._
import com.excilys.ebi.gatling.http.request.builder.AbstractHttpRequestWithBodyBuilder
import com.excilys.ebi.gatling.core.structure.ChainBuilder
import jodd.util.StringUtil
import com.ning.http.client._

import AbiquoAPI._
import bootstrap._
import akka.util.duration._

object API {

    // requires 'login.csv' feeder
    val login = exec(http("LOGIN")
        get(LOGIN) header(ACCEPT, MT_USER)
        basicAuth("${loginuser}","${loginpassword}")
        check( status is OK, captureEnterpriseId, captureUserId )
    )

    val getUsers = exec(http("USERS_GET")
            get(USERS) header(ACCEPT, MT_USERS)
            queryParam("numResults", "100") queryParam("page", "0")  queryParam("desc", "false") queryParam("orderBy", "name") queryParam("connected", "false")
            check( status is OK )
        )

    val createUser = exec(http("USER_POST")
            post(USERS) header(ACCEPT, MT_USER) header(CONTENT_TYPE, MT_USER)
            fileBody("user.xml", Map(
                "lusername" -> "${lusername}",
                "lusernick" -> "${lusername}",
                "desc"      -> "created")
            )
            check( status is CREATED, captureLuserId)
        )

    // use {luserId}
    val getUser = exec(http("USER_GET")
            get(LUSER) header(ACCEPT, MT_USER)
            check( status is OK, checkLuserId)
        )

    val getNoUser = exec(http("USER_GET")
            get(LUSER) header(ACCEPT, MT_USER)
            check( status is NOT_FOUND)
        )

    val modifyUser = exec(http("USER_PUT")
            put(LUSER) header(ACCEPT, MT_USER) header(CONTENT_TYPE, MT_USER)
            fileBody("user.xml", Map(
                "lusername" -> "${lusername}",
                "lusernick" -> "${lusername}",
                "desc"      -> "modify")
            )
            check( status is OK)//regex("""${lusername}modified""") exists
        )

    val delUser = exec(http("USER_DEL")
            delete(LUSER) header(ACCEPT, MT_XML)
            check( status is NO_CONTENT )
        )

    val createConfDatacenter = exec(http("DC_POST")
            post(DCS) header(ACCEPT, MT_DC) header(CONTENT_TYPE, MT_DC)
            fileBody("datacenter.xml",
                Map("remoteservicesIp" -> "${remoteservicesIp}"))
            check( status is CREATED, captureDatacenterId)
        )

    val createConfRack = exec(http("RACK_POST")
            post(RACKS) header(ACCEPT, MT_RACK) header(CONTENT_TYPE, MT_RACK)
            fileBody("rack.xml",
                Map("name" -> "myrack"))
            check( status is CREATED, captureRackId )
        )

    // Machine (do not use nodecollector) from feeder 'infrastructure.csv'
    // get("/api/admin/datacenters/${datacenter}/action/hypervisor") queryParam("ip", "10.60.1.120")
    // get("/api/admin/datacenters/2/action/discoversingle") queryParam("ip", "10.60.1.120") ....
    val createConfMachine = exec(http("MACHS_POST")
            post(MACHS) header(ACCEPT, MT_MACHINE) header(CONTENT_TYPE, MT_MACHINE)
            fileBody("machine.xml", Map(
                "hypervisorIp"      -> "${hypervisorIp}",
                "hypervisorType"    -> "${hypervisorType}")
            )
            check( status is CREATED, captureMachineId )
        )

    val refreshRepository = exec(http("ACTION_REPO_REFRESH")
            get(REPO) header(ACCEPT, MT_DC_REPO)
            queryParam("refresh", "true") queryParam("usage", "true")
            check( status is OK)
        )

    //{pre: login (enterpiseId and userId)} Set $datacenterId
    val getDefaultDatacenter = exec(http("DC_GET")
            get(DCS) header(ACCEPT, MT_DCS)
            check(status is ACCEPTED, captureDatacenterId)
        )

    //{pre: enterpiseId, userId and datacenterId} Set $templateId
    val getDefaultTemplate = exec(http("VMT_GET")
            get(VMTS) header(ACCEPT, MT_VMTS)
            queryParam("hypervisorTypeName", "VBOX")//${hypervisorType}")
            check(status is ACCEPTED, captureTemplateId)
        )

    val createVapp = exec(http("VAPP_POST")
            post(VAPPS) header(ACCEPT, MT_VAPP) header(CONTENT_TYPE, MT_VAPP)
            basicAuth("${loginuser}","${loginpassword}")
            fileBody("vapp.xml", Map("name" -> "myVirtualappliance"))
            check( status is CREATED, captureErrors("POST_VAPP"), captureVirtualapplianceId)
        )

    val deleteVapp = exec(http("VAPP_DEL")
            delete(VAPP) header(ACCEPT, MT_XML)
            basicAuth("${loginuser}","${loginpassword}")
            check( status is NO_CONTENT )
            //captureErrors("DEL_VAPP") SAXParseException: Premature end of file.
        )

    val updateVappState = exec(http("VAPP_STATE")
            get(VAPP_STATE) header(ACCEPT, MT_VAPPSTATE)
            basicAuth("${loginuser}","${loginpassword}")
            check( status is OK, captureVirtualapplianceState )
        )

    val deployVapp = exec(http("VAPP_DEPLOY")
            post(VAPP_DEPLOY) header(ACCEPT, MT_ACCEPTED) header(CONTENT_TYPE, MT_VMTASK)
            basicAuth("${loginuser}","${loginpassword}")
            fileBody("vmtask.xml", Map("force" -> "true"))
            check( status is ACCEPTED, captureErrors("ACTION_VAPP_DEPLOY") )
        )

    val undeployVapp = exec(http("VAPP_UNDEPLOY")
            post(VAPP_UNDEPLOY) header(ACCEPT, MT_ACCEPTED) header(CONTENT_TYPE, MT_VMTASK)
            basicAuth("${loginuser}","${loginpassword}")
            fileBody("vmtask.xml", Map("force" -> "true"))
            check( status is ACCEPTED, captureErrors("ACTION_VAPP_UNDEPLOY") )
        )

    val createVm = exec(http("VM_POST")
            post(VMS) header(ACCEPT, MT_VM) header(CONTENT_TYPE, MT_VM_NODE)
            basicAuth("${loginuser}","${loginpassword}")
            fileBody("vm.xml",
            Map("name"        -> "myVirtualmachine",
                "enterpriseId"->"${enterpriseId}",
                "datacenterId"->"${datacenterId}",
                "templateId"  ->"${templateId}")
            )
            check( status is CREATED, captureCreatedVirtualmachineId )
        )

    val createVdc = exec(http("VDC_POST")
            post(VDCS) header(ACCEPT, MT_VDC) header(CONTENT_TYPE, MT_VDC)
            basicAuth("${loginuser}","${loginpassword}")
            fileBody("vdc.xml", Map(
                "name"          -> "myVirtualDatacenter",
                "enterpriseId"  ->"${enterpriseId}",
                "datacenterId"  ->"${datacenterId}",
                "hypervisorType"->"${hypervisorType}")
            )
            check( status is CREATED, captureVirtualDatacenterId )
        )

    val deleteVdc = exec(http("VDC_DEL")
            delete(VDC) header(ACCEPT, MT_XML)
            basicAuth("${loginuser}","${loginpassword}")
            check( status is NO_CONTENT )
    )

    val getVmTasks = exec(http("VM_TASKS")
            get(VM_TASKS) header(ACCEPT, MT_TASKS)
            basicAuth("${loginuser}","${loginpassword}")
            check( status is OK, captureCurrentVirtualmachineTasks )
        )

    val updateVmContent = exec(http("VM_GET")
            get(VM) header(ACCEPT, MT_VM)
            basicAuth("${loginuser}","${loginpassword}")
            check( status is OK, captureCurrentVirtualmachine )
        )

    val updateVmState = exec(http("VM_STATE")
            get(VM_STATE) header(ACCEPT, MT_VMSTATE)
            basicAuth("${loginuser}","${loginpassword}")
            check( status is OK, captureCurrentVmState )
        )

    val reconfigVm = exec(http("VM_PUT")
            put(VM) header(ACCEPT, MT_ACCEPTED) header(CONTENT_TYPE, MT_VM)
            basicAuth("${loginuser}","${loginpassword}")
            body(s => reconfigureVmBody(s))
            check( status is ACCEPTED, captureErrors("reconfig"))
        )

    val powerOffVm = exec(http("VM_OFF")
            put(VM_STATE) header(ACCEPT, MT_ACCEPTED) header(CONTENT_TYPE, MT_VMSTATE)
            basicAuth("${loginuser}","${loginpassword}")
            body("""<virtualmachinestate><state>OFF</state></virtualmachinestate>""")
            check(status is ACCEPTED, captureErrors("off"))
        )

    val powerOnVm = exec(http("VM_ON")
            put(VM_STATE) header(ACCEPT, MT_ACCEPTED) header(CONTENT_TYPE, MT_VMSTATE)
            basicAuth("${loginuser}","${loginpassword}")
            body("""<virtualmachinestate><state>ON</state></virtualmachinestate>""")
            check(status is ACCEPTED, captureErrors("on"))
        )

    val deployVm = exec(http("VM_DEPLOY")
            post(VM_DEPLOY) header(ACCEPT, MT_ACCEPTED) header(CONTENT_TYPE, MT_VMTASK)
            basicAuth("${loginuser}","${loginpassword}")
            fileBody("vmtask.xml", Map("force" -> "true"))
            check( status is(ACCEPTED) )
        )

    val undeployVm = exec(http("VM_UNDEPLOY")
            post(VM_UNDEPLOY) header(ACCEPT, MT_ACCEPTED) header(CONTENT_TYPE, MT_VMTASK)
            basicAuth("${loginuser}","${loginpassword}")
            fileBody("vmtask.xml", Map("force" -> "true"))
            check( status is ACCEPTED )
        )

    val deleteVm = exec(http("VM_DEL")
            delete(VM)
            basicAuth("${loginuser}","${loginpassword}")
            check( status is NO_CONTENT )
        )

    val stadistics = group("GET") {
            exec(http("CLOUD_USAGE")
                get("/api/statistics/cloudusage/actions/total") header(ACCEPT, MT_CLOUDUSE)
                basicAuth("${loginuser}","${loginpassword}")
                check(status is OK, captureErrors("cloud"))
            )
            .exec(http("STADISTICS_ENTER")
                get("/api/statistics/enterpriseresources/${enterpriseId}") header(ACCEPT, MT_RES_ENT)
                basicAuth("${loginuser}","${loginpassword}")
                check(status is OK, captureErrors("st_e"))
            )
            .exec(http("STADISTICS_VDC")
                get("/api/statistics/vdcsresources") header(ACCEPT, MT_RES_VDC)
                queryParam("identerprise", "${enterpriseId}")
                basicAuth("${loginuser}","${loginpassword}")
                check(status is OK, captureErrors("st_vdc"))
            )
            .exec(http("STADISTICS_VAPP")
                get("/api/statistics/vappsresources") header(ACCEPT, MT_RES_VAPP)
                queryParam("identerprise", "${enterpriseId}")
                basicAuth("${loginuser}","${loginpassword}")
                check(status is OK, captureErrors("st_vapp"))
            )
        }

    val listByEnterprise = group("GET") {
            exec(http("GET_VDCS_byE")
                get("/api/admin/enterprises/${enterpriseId}/action/virtualdatacenters") header(ACCEPT, MT_VDCS)
                queryParam("startwith", "0") queryParam("limit", "25") queryParam("by", "name") queryParam("asc", "true")
                basicAuth("${loginuser}","${loginpassword}")
                check(status is OK, captureErrors("e_vdc"))
            )
            .exec(http("GET_VAPPS_byE")
                get("/api/admin/enterprises/${enterpriseId}/action/virtualappliances") header(ACCEPT, MT_VAPPS)
                queryParam("startwith", "0") queryParam("limit", "25") queryParam("by", "name") queryParam("asc", "true")
                basicAuth("${loginuser}","${loginpassword}")
                check(status is OK, captureErrors("e_vapp"))
            )
            .exec(http("GET_VMS_byE")
                get("/api/admin/enterprises/${enterpriseId}/action/virtualmachines") header(ACCEPT, MT_VMS)
                queryParam("startwith", "0") queryParam("limit", "25") queryParam("by", "name") queryParam("asc", "true")
                basicAuth("${loginuser}","${loginpassword}")
                check(status is OK, captureErrors("e_vm"))
            )
        }
}