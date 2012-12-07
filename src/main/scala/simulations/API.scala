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

    // requires loginuser and loginpassword paramaters. Set enterpiseId and userId
    val login = exec(http("LOGIN")
        get(LOGIN) header(ACCEPT, MT_USER)
        basicAuth("${loginuser}","${loginpassword}")
        check( status is OK, captureEnterpriseId, captureUserId )
    )

    val getUsers = exec(http("GET_USERS")
            get(USERS) header(ACCEPT, MT_USERS)
            queryParam("numResults", "100") queryParam("page", "0")  queryParam("desc", "false") queryParam("orderBy", "name") queryParam("connected", "false")
            check( status is OK )
        )

    val createUser = exec(http("POST_USER")
            post(USERS) header(ACCEPT, MT_USER) header(CONTENT_TYPE, MT_USER)
            fileBody("user.xml", userContent)
            check( status is CREATED, captureLuserId)
        )

    // use {luserId}
    val getUser = exec(http("GET_USER")
            get(LUSER) header(ACCEPT, MT_USER)
            check( status is OK, checkLuserId)
        )

    val getNoUser = exec(http("GET_USER")
            get(LUSER) header(ACCEPT, MT_USER)
            check( status is NOT_FOUND)
        )

    val modifyUser = exec(http("PUT_USER")
            put(LUSER) header(ACCEPT, MT_USER) header(CONTENT_TYPE, MT_USER)
            fileBody("user.xml", userContentPut)
            check( status is OK)//regex("""${lusername}modified""") exists
        )

    val delUser = exec(http("DEL_USER")
            delete(LUSER) header(ACCEPT, MT_XML)
            check( status is NO_CONTENT )
        )

    val createConfDatacenter = exec(http("POST_DC")
            post(DCS) header(ACCEPT, MT_DC) header(CONTENT_TYPE, MT_DC)
            fileBody("datacenter.xml",
                Map("remoteservicesIp" -> "${remoteservicesIp}"))
            check( status is CREATED, captureDatacenterId)
        )

    val createConfRack = exec(http("POST_RACK")
            post(RACKS) header(ACCEPT, MT_RACK) header(CONTENT_TYPE, MT_RACK)
            fileBody("rack.xml",
                Map("name" -> "myrack"))
            check( status is CREATED, captureRackId )
        )

    // Machine (do not use nodecollector) from feeder 'infrastructure.csv'
    // get("/api/admin/datacenters/${datacenter}/action/hypervisor") queryParam("ip", "10.60.1.120")
    // get("/api/admin/datacenters/2/action/discoversingle") queryParam("ip", "10.60.1.120") ....
    val createConfMachine = exec(http("POST_MACHS")
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
    val getDefaultDatacenter = exec(http("GET_DC")
            get(DCS) header(ACCEPT, MT_DCS)
            check(status is ACCEPTED, captureDatacenterId)
        )

    //{pre: enterpiseId, userId and datacenterId} Set $templateId
    val getDefaultTemplate = exec(http("GET_VMT")
            get(VMTS) header(ACCEPT, MT_VMTS)
            queryParam("hypervisorTypeName", "VBOX")//${hypervisorType}")
            check(status is ACCEPTED, captureTemplateId)
        )

    val createVapp = exec(http("POST_VAPP")
            post(VAPPS) header(ACCEPT, MT_VAPP) header(CONTENT_TYPE, MT_VAPP)
            fileBody("vapp.xml", vappContent)
            check( status is CREATED, captureErrors("POST_VAPP"), captureVirtualapplianceId)
        )

    val deleteVapp = exec(http("DEL_VAPP")
            delete(VAPP) header(ACCEPT, MT_XML)
            check( status is NO_CONTENT )
            //captureErrors("DEL_VAPP") SAXParseException: Premature end of file.
        )

    val updateVappState = exec(http("GET_VAPP_STATE")
            get(VAPP_STATE) header(ACCEPT, MT_VAPPSTATE)
            check( status is OK, captureVirtualapplianceState )
        )

    val deployVapp = exec(http("ACTION_VAPP_DEPLOY")
            post(VAPP_DEPLOY) header(ACCEPT, MT_ACCEPTED) header(CONTENT_TYPE, MT_VMTASK)
            fileBody("vmtask.xml", vmtaskContent)
            check( status is ACCEPTED, captureErrors("ACTION_VAPP_DEPLOY") )
        )

    val undeployVapp = exec(http("ACTION_VAPP_UNDEPLOY")
            post(VAPP_UNDEPLOY) header(ACCEPT, MT_ACCEPTED) header(CONTENT_TYPE, MT_VMTASK)
            fileBody("vmtask.xml", vmtaskContent)
            check( status is ACCEPTED, captureErrors("ACTION_VAPP_UNDEPLOY") )
        )

    val createVm = exec(http("POST_VM")
            post(VMS) header(ACCEPT, MT_VM) header(CONTENT_TYPE, MT_VM_NODE)
            fileBody("vm.xml", vmContent)
            check( status is CREATED, captureCurrentVirtualmachine, captureCurrentVirtualmachineId )
        )

    val updateVmState = exec(http("GET_VM_STATE")
            get(VM_STATE) header(ACCEPT, MT_VMSTATE)
            check( status is OK, captureCurrentVmState )
        )

    val reconfigVm = exec(http("PUT_VM")
            put(VM) header(ACCEPT, MT_ACCEPTED) header(CONTENT_TYPE, MT_VM)
            body(s => reconfigureVmBody(s))
            check( status is ACCEPTED, captureErrors("reconfig"))
        )

    val powerOffVm = exec(http("ACTION_VM_OFF")
            put(VM+"/state") header(ACCEPT, MT_ACCEPTED) header(CONTENT_TYPE, MT_VMSTATE)
            body("""<virtualmachinestate><state>OFF</state></virtualmachinestate>""")
            check(status is ACCEPTED, captureErrors("off"))
        )

    val deployVm = exec(http("ACTION_VM_DEPLOY")
            post(VM_DEPLOY) header(ACCEPT, MT_ACCEPTED) header(CONTENT_TYPE, MT_VMTASK)
            fileBody("vmtask.xml", vmtaskContent)
            check( status is(ACCEPTED) )
        )

    val undeployVm = exec(http("ACTION_VM_UNDEPLOY")
            post(VM_UNDEPLOY) header(ACCEPT, MT_ACCEPTED) header(CONTENT_TYPE, MT_VMTASK)
            fileBody("vmtask.xml", vmtaskContent)
            check( status is ACCEPTED )
        )

    val deleteVm = exec(http("DEL_VM")
            delete(VM)
            check( status is NO_CONTENT )
        )

    val stadistics =
    exec(http("CLOUD_USAGE")
        get("/api/statistics/cloudusage/actions/total") header(ACCEPT, MT_CLOUDUSE)
        basicAuth("${loginuser}","${loginpassword}")
        check(status is OK)
    )
    .exec(http("STADISTICS_ENTER")
        get("/api/statistics/enterpriseresources/${enterpriseId}") header(ACCEPT, MT_RES_ENT)
        basicAuth("${loginuser}","${loginpassword}")
        check(status is OK)
    )
    .exec(http("STADISTICS_VDC")
        get("/api/statistics/vdcsresources") header(ACCEPT, MT_RES_VDC)
        queryParam("identerprise", "${enterpriseId}")
        basicAuth("${loginuser}","${loginpassword}")
        check(status is OK)
    )
    .exec(http("STADISTICS_VAPP")
        get("/api/statistics/vappsresources") header(ACCEPT, MT_RES_VAPP)
        queryParam("identerprise", "${enterpriseId}")
        basicAuth("${loginuser}","${loginpassword}")
        check(status is OK)
    )

    val listByEnterprise =
    exec(http("GET_VDCS_byE")
        get("/api/admin/enterprises/${enterpriseId}/action/virtualdatacenters") header(ACCEPT, MT_VDCS)
        queryParam("startwith", "0") queryParam("limit", "25") queryParam("by", "name") queryParam("asc", "true")
        basicAuth("${loginuser}","${loginpassword}")
        check(status is OK)
    )
    .exec(http("GET_VAPPS_byE")
        get("/api/admin/enterprises/${enterpriseId}/action/virtualappliances") header(ACCEPT, MT_VAPPS)
        queryParam("startwith", "0") queryParam("limit", "25") queryParam("by", "name") queryParam("asc", "true")
        basicAuth("${loginuser}","${loginpassword}")
        check(status is OK)
    )
    .exec(http("GET_VMS_byE")
        get("/api/admin/enterprises/${enterpriseId}/action/virtualmachines") header(ACCEPT, MT_VMS)
        queryParam("startwith", "0") queryParam("limit", "25") queryParam("by", "name") queryParam("asc", "true")
        basicAuth("${loginuser}","${loginpassword}")
        check(status is OK)
    )
}