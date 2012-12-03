import com.excilys.ebi.gatling.core.Predef._
import com.excilys.ebi.gatling.http.Headers.Names._
import com.excilys.ebi.gatling.http.Predef._
import com.excilys.ebi.gatling.http.check.HttpCheck
import org.glassfish.grizzly.http.util.HttpStatus._
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
        check(status is OK, captureEnterpriseId, captureUserId )
    )

    //{pre: login (enterpiseId and userId)} Set $datacenterId
    val getDefaultDatacenter = exec(http("GET_DC")
            get(DC) header(ACCEPT, MT_DCS)
            check(status is ACCEPTED, captureDatacenterId)
        )

    //{pre: enterpiseId, userId and datacenterId} Set $templateId
    val getDefaultTemplate = exec(http("GET_VMT")
            get(VMTS) header(ACCEPT, MT_VMTS)
            queryParam("hypervisorTypeName", "VBOX")//${hypervisorType}")
            check(status is ACCEPTED, captureTemplateId)
        )

    val createVm = exec(http("POST_VM")
            post(VMS) header(ACCEPT, MT_VM) header(CONTENT_TYPE, MT_VM_NODE)
            fileBody("vm.xml", vmContent)
            basicAuth("${loginuser}","${loginpassword}")
            check( status is CREATED )
        )

    val createVapp = exec(http("POST_VAPP")
            post(VAPPS) header(ACCEPT, MT_VAPP) header(CONTENT_TYPE, MT_VAPP)
            fileBody("vapp.xml", vappContent)
            basicAuth("${loginuser}","${loginpassword}")
            check( status is CREATED, captureErrors("POST_VAPP"), captureVirtualapplianceId)
        )

    val deleteVapp = exec(http("DEL_VAPP")
            delete(VAPP) header(ACCEPT, MT_XML)
            basicAuth("${loginuser}","${loginpassword}")
            check( status is NO_CONTENT )
            //captureErrors("DEL_VAPP") SAXParseException: Premature end of file.
        )

    val updateVappState = exec(http("GET_VAPP_STATE")
            get(VAPP_STATE) header(ACCEPT, MT_VAPPSTATE)
            basicAuth("${loginuser}","${loginpassword}")
            check( status is OK, captureVirtualapplianceState )
        )

    val deployVapp = exec(http("ACTION_VAPP_DEPLOY")
            post(VAPP_DEPLOY) header(ACCEPT, MT_ACCEPTED) header(CONTENT_TYPE, MT_VMTASK)
            fileBody("vmtask.xml", vmtaskContent)
            basicAuth("${loginuser}","${loginpassword}")
            check( status is ACCEPTED, captureErrors("ACTION_VAPP_DEPLOY") )
        )

    val undeployVapp = exec(http("ACTION_VAPP_UNDEPLOY")
            post(VAPP_UNDEPLOY) header(ACCEPT, MT_ACCEPTED) header(CONTENT_TYPE, MT_VMTASK)
            fileBody("vmtask.xml", vmtaskContent)
            basicAuth("${loginuser}","${loginpassword}")
            check( status is ACCEPTED, captureErrors("ACTION_VAPP_UNDEPLOY") )
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