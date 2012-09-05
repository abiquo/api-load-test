import java.lang.System.{ currentTimeMillis, nanoTime }
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import com.excilys.ebi.gatling.core.Predef._
import com.excilys.ebi.gatling.http.Headers.Names._
import com.excilys.ebi.gatling.http.Predef._
import com.excilys.ebi.gatling.core.session.Session

object AbiquoAPI {
    val ABQ_VERSION = """;""" // version=2.2 """

    val MT_USER     = """application/vnd.abiquo.user+xml""" + ABQ_VERSION
    val MT_USERS    = """application/vnd.abiquo.users+xml""" + ABQ_VERSION
    val MT_DCS      = """application/vnd.abiquo.datacenters+xml""" + ABQ_VERSION
    val MT_DC       = """application/vnd.abiquo.datacenter+xml""" + ABQ_VERSION
    val MT_RACKS    = """application/vnd.abiquo.racks+xml""" + ABQ_VERSION
    val MT_RACK     = """application/vnd.abiquo.rack+xml""" + ABQ_VERSION
    val MT_MACHINE  = """application/vnd.abiquo.machine+xml""" + ABQ_VERSION
    val MT_MACHINES = """application/vnd.abiquo.machines+xml""" + ABQ_VERSION
    val MT_RES_ENT  = """application/vnd.abiquo.enterpriseresources+xml""" + ABQ_VERSION
    val MT_RES_VDC  = """application/vnd.abiquo.virtualdatacentersresources+xml""" + ABQ_VERSION
    val MT_RES_VAPP = """application/vnd.abiquo.virtualappsresources+xml""" + ABQ_VERSION
    val MT_CLOUDUSE = """application/vnd.abiquo.cloudusage+xml""" + ABQ_VERSION
    val MT_DC_REPO  = """application/vnd.abiquo.datacenterrepository+xml""" + ABQ_VERSION
    val MT_VDCS     = """application/vnd.abiquo.virtualdatacenters+xml""" + ABQ_VERSION
    val MT_VDC      = """application/vnd.abiquo.virtualdatacenter+xml""" + ABQ_VERSION
    val MT_VAPPS    = """application/vnd.abiquo.virtualappliances+xml""" + ABQ_VERSION
    val MT_VAPP     = """application/vnd.abiquo.virtualappliance+xml""" + ABQ_VERSION
    val MT_VAPPSTATE= """application/vnd.abiquo.virtualappliancestate+xml""" + ABQ_VERSION
    val MT_VMS_EXT  = """application/vnd.abiquo.virtualmachineswithnodeextended+xml""" + ABQ_VERSION
    val MT_VM_EXT   = """application/vnd.abiquo.virtualmachinewithnodeextended+xml""" + ABQ_VERSION
    val MT_VM_NODE  = """application/vnd.abiquo.virtualmachinewithnode+xml""" + ABQ_VERSION
    val MT_VMTS     = """application/vnd.abiquo.virtualmachinetemplates+xml""" + ABQ_VERSION
    val MT_VMT      = """application/vnd.abiquo.virtualmachinetemplate+xml""" + ABQ_VERSION
    val MT_VM       = """application/vnd.abiquo.virtualmachine+xml""" + ABQ_VERSION
    val MT_VMS      = """application/vnd.abiquo.virtualmachines+xml""" + ABQ_VERSION
    val MT_VMSTATE  = """application/vnd.abiquo.virtualmachinestate+xml""" + ABQ_VERSION
    val MT_ACCEPTED = """application/vnd.abiquo.acceptedrequest+xml""" + ABQ_VERSION
    val MT_TASKS    = """application/vnd.abiquo.tasks+xml""" + ABQ_VERSION
    val MT_TASK     = """application/vnd.abiquo.task+xml""" + ABQ_VERSION
    val MT_VOLS     = """application/vnd.abiquo.iscsivolumes+xml""" + ABQ_VERSION
    val MT_VLAN     = """application/vnd.abiquo.vlan+xml""" + ABQ_VERSION
    val MT_VMTASK   = """application/vnd.abiquo.virtualmachinetask+xml"""+ ABQ_VERSION
    val MT_XML      = """application/xml"""
    val MT_PLAIN    = """text/plain"""

    val OK          = 200
    val CREATED     = 201
    val ACCEPTED    = 202
    val NO_CONTENT  = 204

    val LOG     = LoggerFactory.getLogger("abiquo");
    val LOGREPO = LoggerFactory.getLogger("repo");

    val POST_VM             = "/api/cloud/virtualdatacenters/${virtualdatacenterId}/virtualappliances/${virtualapplianceId}/virtualmachines"
    val GET_VM_STATE        = "/api/cloud/virtualdatacenters/${virtualdatacenterId}/virtualappliances/${virtualapplianceId}/virtualmachines/${currentVmId}/state"
    val POST_VAPP           = "/api/cloud/virtualdatacenters/${virtualdatacenterId}/virtualappliances"
    val GET_VAPP_STATE      = "/api/cloud/virtualdatacenters/${virtualdatacenterId}/virtualappliances/${virtualapplianceId}/state"
    val DEL_VAPP            = "/api/cloud/virtualdatacenters/${virtualdatacenterId}/virtualappliances/${virtualapplianceId}"
    val ACTION_VAPP_UNDEPLOY= "/api/cloud/virtualdatacenters/${virtualdatacenterId}/virtualappliances/${virtualapplianceId}/action/undeploy"
    val ACTION_VAPP_DEPLOY  = "/api/cloud/virtualdatacenters/${virtualdatacenterId}/virtualappliances/${virtualapplianceId}/action/deploy"
    val ACTION_VM_DEPLOY    = "/api/cloud/virtualdatacenters/${virtualdatacenterId}/virtualappliances/${virtualapplianceId}/virtualmachines/${currentVmId}/action/deploy"
    val ACTION_VM_UNDEPLOY  = "/api/cloud/virtualdatacenters/${virtualdatacenterId}/virtualappliances/${virtualapplianceId}/virtualmachines/${currentVmId}/action/undeploy"
    val DEL_VM              = "/api/cloud/virtualdatacenters/${virtualdatacenterId}/virtualappliances/${virtualapplianceId}/virtualmachines/${currentVmId}"

    def captureErrors(exec:String)     = xpath("""/errors/error/code""").findAll.notExists.saveAs("error-"+exec)
    def captureVirtualapplianceState   = xpath("""/virtualApplianceState/power""").find.exists.saveAs("virtualApplianceState")
    def captureCurrentVmState          = xpath("""/virtualmachinestate/state""").find.exists.saveAs("currentVmState")
    def captureVirtualapplianceId      = regex("""virtualappliances/(\d+)/""").find.exists.saveAs("virtualapplianceId")
    def captureCurrentVirtualmachineId = regex("""virtualmachines/(\d+)/""").find.exists.saveAs("virtualmachineId")

    def vmtaskContent   = Map(  "force" -> "true")
    def vappContent     = Map(  "name" -> "myVirtualappliance")
    def vmContent       = Map(  "name"          -> "myVirtualmachine",
                                "datacenterId"  -> "${datacenterId}",
                                "templateId"    -> "${templateId}")

    def vmIdByCounter(s:Session)               = { "virtualmachineId-" + s.getCounterValue("numVirtualmachine") }
    def saveCurrentVirtualmachineId(s:Session) = {
        if(s.isAttributeDefined("virtualmachineId"))
        {
            s.setAttribute(vmIdByCounter(s),
            s.getTypedAttribute[String]("virtualmachineId"))
        } else{ s }
    }
    def setCurrentVmId(s:Session)           = { s.setAttribute("currentVmId", s.getTypedAttribute[String](vmIdByCounter(s)) ) }
    def clearCurrentVmCreation(s:Session)   = { s.removeAttribute("currentVmCreated") }
    def clearCurrentVmState(s:Session)      = { s.removeAttribute("currentVmState") }

    def actionRetry(action:String, s:Session) = {
        val a = action + "Retry"
        if(s.isAttributeDefined(a)) { s.setAttribute(a, s.getTypedAttribute[Long](a) + 1) }
        else { s.setAttribute(a, 0l) }
    }

    def deployStartTime(s:Session)  = { s.setAttribute("deployStartTime",   currentTimeMillis) }
    def deployStopTime(s:Session)   = { s.setAttribute("deployStopTime",    currentTimeMillis) }
    def undeployStartTime(s:Session)= { s.setAttribute("undeployStartTime", currentTimeMillis) }
    def undeployStopTime(s:Session) = { s.setAttribute("undeployStopTime",  currentTimeMillis) }

    def reportUserLoop(s:Session) = {
        var userloop = LOGREPO.info(
            "vappId {} \tpost_vm {} \tdeploy {} \tundeploy {} "
            +"\tvmfail-D {} \tvmfail-U {} \tvmdel-U {} " //\tvmdel-D {}
            +"\tdeployMs {} \tundeployMs {} ",
                Array[Object](
                    s.getTypedAttribute[String]("virtualapplianceId"),
                    getCreateVmRetry(s).asInstanceOf[java.lang.Long],
                    (s.getTypedAttribute[Long]("deployRetry") -1).asInstanceOf[java.lang.Long],
                    (s.getTypedAttribute[Long]("undeployRetry") -1).asInstanceOf[java.lang.Long],

                    getDeployVmRetry(s).asInstanceOf[java.lang.Long],
                    getUndeployVmRetry(s).asInstanceOf[java.lang.Long],
                    getDelVmUndeploy(s).asInstanceOf[java.lang.Long],
//                    getDelVmDeploy(s).asInstanceOf[java.lang.Long],

                    (s.getTypedAttribute[Long]("deployStopTime") - s.getTypedAttribute[Long]("deployStartTime")).asInstanceOf[java.lang.Long],
                    (s.getTypedAttribute[Long]("undeployStopTime") - s.getTypedAttribute[Long]("undeployStartTime")).asInstanceOf[java.lang.Long]
                    )
            )
        LOG.trace("{}",s);

        s.removeAttribute("virtualApplianceState").removeAttribute("currentVmState").removeAttribute("currentVmId")
        .removeAttribute("deployRetry").removeAttribute("undeployRetry")
        .removeAttribute("createVmRetry").removeAttribute("forceUndeployVmRetry").removeAttribute("forceDeployVmRetry")
        .removeAttribute("delVmDeployRetry").removeAttribute("delVmUndeployRetry")
    }

    def getDelVmUndeploy(s:Session) = {
        if(s.isAttributeDefined("delVmUndeployRetry")) { s.getTypedAttribute[Long]("delVmUndeployRetry") + 1 } else { 0l }
    }
    def getDelVmDeploy(s:Session) = {
        if(s.isAttributeDefined("delVmDeployRetry")) { s.getTypedAttribute[Long]("delVmDeployRetry") + 1 } else { 0l }
    }
    def getCreateVmRetry(s:Session) = { if(s.isAttributeDefined("createVmRetry")) {
            s.getTypedAttribute[Long]("createVmRetry") - s.getTypedAttribute[String]("numVirtualMachinesInVapp").toLong + 1 } else { 0l }
    }
    def getDeployVmRetry(s:Session) = {
        if(s.isAttributeDefined("forceDeployVmRetry")) { s.getTypedAttribute[Long]("forceDeployVmRetry") + 1 } else { 0l }
    }
    def getUndeployVmRetry(s:Session) = {
        if(s.isAttributeDefined("forceUndeployVmRetry")) { s.getTypedAttribute[Long]("forceUndeployVmRetry") + 1 } else { 0l }
    }

    def logVirtualApplianceState(msg:String, s:Session) = {
        if(s.isAttributeDefined("virtualApplianceState") &&
            !s.getTypedAttribute[String]("virtualApplianceState").startsWith("LOCKED")) {
            LOG.debug(msg + "\tvapp {}\t{}",
            s.getTypedAttribute[String]("virtualapplianceId"),
            s.getTypedAttribute[String]("virtualApplianceState"));
        }; s
    }

    def needCreateVirtualMachineRetry(s:Session) = { !s.isAttributeDefined("currentVmCreated") }
    def needAcceptedDeplyRetry(s:Session)        = { !s.isAttributeDefined("acceptedDeply")}
    def needAcceptedUndeplyRetry(s:Session)      = { !s.isAttributeDefined("acceptedUndeply")}

    def isCurrentVirtualMachineState(s:Session, states:Set[String]) = { s.isAttributeDefined("currentVmState")  && states.contains(s.getTypedAttribute[String]("currentVmState")) }
    def isVirtualApplianceState(s:Session, states:Set[String])      = { s.isAttributeDefined("virtualApplianceState")  && states.contains(s.getTypedAttribute[String]("virtualApplianceState")) }
}