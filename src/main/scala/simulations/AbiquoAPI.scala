import java.lang.System.{ currentTimeMillis, nanoTime }
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import com.excilys.ebi.gatling.core.Predef._
import com.excilys.ebi.gatling.core.session.Session
import com.excilys.ebi.gatling.http.Predef._
import com.excilys.ebi.gatling.http.Headers.Names._
import com.excilys.ebi.gatling.http.check.HttpCheck
import com.excilys.ebi.gatling.core.session.{ EvaluatableString, Session }

import javax.xml.bind.JAXBContext
import javax.xml.stream.XMLInputFactory
import java.io.StringReader
import java.io.StringWriter

import com.abiquo.server.core.cloud.VirtualMachineDto
import com.abiquo.server.core.task.TasksDto

object AbiquoAPI {
    val ABQ_VERSION = """; version=2.4 """

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
    val MT_VMTASK   = """application/vnd.abiquo.virtualmachinetask+xml"""+ ABQ_VERSION
    val MT_ACCEPTED = """application/vnd.abiquo.acceptedrequest+xml""" + ABQ_VERSION
    val MT_TASKS    = """application/vnd.abiquo.tasks+xml""" + ABQ_VERSION
    val MT_TASK     = """application/vnd.abiquo.task+xml""" + ABQ_VERSION
    val MT_VOLS     = """application/vnd.abiquo.iscsivolumes+xml""" + ABQ_VERSION
    val MT_VLAN     = """application/vnd.abiquo.vlan+xml""" + ABQ_VERSION
    val MT_XML      = """application/xml"""
    val MT_PLAIN    = """text/plain"""

    val OK          = 200
    val CREATED     = 201
    val ACCEPTED    = 202
    val NO_CONTENT  = 204
    val NOT_FOUND   = 404

    val LOG     = LoggerFactory.getLogger("abiquo");
    val LOGREPO = LoggerFactory.getLogger("repo");

    val LOGIN       = "/api/login"
    val USERS       = "/api/admin/enterprises/${enterpriseId}/users"
    val LUSER       = "/api/admin/enterprises/${enterpriseId}/users/${luserId}"
    val DCS         = "/api/admin/datacenters"
    val RACKS       = "/api/admin/datacenters/${datacenterId}/racks"
    val MACHS       = "/api/admin/datacenters/${datacenterId}/racks/${rackId}/machines"
    val REPO        = "/api/admin/enterprises/${enterpriseId}/datacenterrepositories/${datacenterId}"
    val VMTS        = "/api/admin/enterprises/${enterpriseId}/datacenterrepositories/${datacenterId}/virtualmachinetemplates"
    val VDCS        = "/api/cloud/virtualdatacenters"
    val VDC         = "/api/cloud/virtualdatacenters/${virtualdatacenterId}"
    val VAPPS       = "/api/cloud/virtualdatacenters/${virtualdatacenterId}/virtualappliances"
    val VAPP        = "/api/cloud/virtualdatacenters/${virtualdatacenterId}/virtualappliances/${virtualapplianceId}"
    val VAPP_STATE  = "/api/cloud/virtualdatacenters/${virtualdatacenterId}/virtualappliances/${virtualapplianceId}/state"
    val VAPP_UNDEPLOY="/api/cloud/virtualdatacenters/${virtualdatacenterId}/virtualappliances/${virtualapplianceId}/action/undeploy"
    val VAPP_DEPLOY = "/api/cloud/virtualdatacenters/${virtualdatacenterId}/virtualappliances/${virtualapplianceId}/action/deploy"
    val VMS         = "/api/cloud/virtualdatacenters/${virtualdatacenterId}/virtualappliances/${virtualapplianceId}/virtualmachines"
    val VM          = "/api/cloud/virtualdatacenters/${virtualdatacenterId}/virtualappliances/${virtualapplianceId}/virtualmachines/${currentVmId}"
    val VM_TASKS    = "/api/cloud/virtualdatacenters/${virtualdatacenterId}/virtualappliances/${virtualapplianceId}/virtualmachines/${currentVmId}/tasks"
    val VM_STATE    = "/api/cloud/virtualdatacenters/${virtualdatacenterId}/virtualappliances/${virtualapplianceId}/virtualmachines/${currentVmId}/state"
    val VM_DEPLOY   = "/api/cloud/virtualdatacenters/${virtualdatacenterId}/virtualappliances/${virtualapplianceId}/virtualmachines/${currentVmId}/action/deploy"
    val VM_UNDEPLOY = "/api/cloud/virtualdatacenters/${virtualdatacenterId}/virtualappliances/${virtualapplianceId}/virtualmachines/${currentVmId}/action/undeploy"

    def captureCurrentVirtualmachineId = regex("""virtualmachines/(\d+)/""").find.exists.saveAs("currentVmId")
    def captureCurrentVirtualmachine   = bodyString.saveAs("currentVmBody")
    def captureCurrentVirtualmachineTasks= bodyString.saveAs("currentVmTasks")
    def captureDatacenterId            = regex("""datacenters/(\d+)""") find(0) saveAs("datacenterId")
    def captureTemplateId              = regex("""virtualmachinetemplates/(\d+)""") find(0) saveAs("templateId")
    def captureEnterpriseId            = regex("""enterprises/(\d+)/users/""") find(0) saveAs("enterpriseId")
    def captureUserId                  = regex("""users/(\d+)""") find(0) saveAs("currentUserId")
    def captureLuserId                 = regex("""users/(\d+)""") find(0) saveAs("luserId")
    def checkLuserId                   = regex("""users/${luserId}""") find(0) exists
    def captureRackId                  = regex("""racks/(\d+)""") find(0) saveAs("rackId")
    def captureMachineId               = regex("""machines/(\d+)""") find(0) saveAs("machineId")
    def captureErrors(exec:String)     = xpath("""/errors/error/code""").findAll.notExists.saveAs("error-"+exec)
    def captureVirtualapplianceState   = xpath("""/virtualApplianceState/power""").find.exists.saveAs("virtualApplianceState")
    def captureVirtualapplianceId      = regex("""virtualappliances/(\d+)/""").find.exists.saveAs("virtualapplianceId")
    def captureCurrentVmState          = xpath("""/virtualmachinestate/state""").find.exists.saveAs("currentVmState")
    def captureVirtualDatacenterId     = regex("""virtualdatacenters/(\d+)/""").find.exists.saveAs("virtualdatacenterId")

    def vmAttributeByCounter(s:Session)     = { "virtualmachine-" + s.getTypedAttribute[String]("numVm") }
    def setCurrentVmId(s:Session)           = { s.setAttribute("currentVmId", s.getTypedAttribute[String](vmAttributeByCounter(s)+"-id")) }
    def clearCurrentVmId(s:Session)			= { s.removeAttribute("currentVmId").removeAttribute("currentVmState").removeAttribute("currentVmBody").removeAttribute("currentVmTasks")}
    def saveCurrentVirtualmachine(s:Session)= {
        val vmkey = vmAttributeByCounter(s);
        if(s.isAttributeDefined("currentVmId")) {
            s.setAttribute(vmkey+"-id",      s.getTypedAttribute[Int]("currentVmId"))
        }
        else{
            LOG.error("can't get virtual machine {}", vmkey); s
        }
    }

    val factory   = XMLInputFactory.newInstance()
    val tcontext  = JAXBContext.newInstance(classOf[TasksDto])
    val vmcontext = JAXBContext.newInstance(classOf[VirtualMachineDto])
    def readTasks(tcontent:String) : TasksDto   = { tcontext.createUnmarshaller().unmarshal(factory.createXMLStreamReader(new StringReader(tcontent))).asInstanceOf[TasksDto] }
    def readVm(vmcontent:String) : VirtualMachineDto= { vmcontext.createUnmarshaller().unmarshal(factory.createXMLStreamReader(new StringReader(vmcontent))).asInstanceOf[VirtualMachineDto] }
    def writeVm(vm:VirtualMachineDto) : String      = { val sw = new StringWriter(); vmcontext.createMarshaller().marshal(vm, sw); sw.toString() }

    def reconfigureVmBody(s:Session) = {
        val vmcontent = s.getTypedAttribute[String]("currentVmBody")
        val vm = readVm(vmcontent)
        vm.setCpu(vm.getCpu() + 1)
        writeVm(vm)
    }

    def reportTasks(s:Session): Session = {
        if(!s.isAttributeDefined("currentVmTasks")) {
            LOGREPO.error("can't get vm tasks")
            return s
        }
        val tcontent = s.getTypedAttribute[String]("currentVmTasks")
        val tasks = readTasks(tcontent)

        import scala.collection.JavaConverters._
        for(t <- tasks.getCollection().asScala) {
            for(j <- t.getJobs().getCollection().asScala) {
                // vm taskId jobId taskType jobType state creation timestamp
                LOGREPO.info("{}", Array[Object](
                        t.getOwnerId, t.getTaskId, j.getId, t.getType.name, j.getType.name,
                        j.getState.name, j.getCreationTimestamp, j.getTimestamp.toString)
                )
            }
        };s
    }

    def deployStartTime(s:Session)  = { s.setAttribute("deployStartTime",   currentTimeMillis) }
    def deployStopTime(s:Session)   = { s.setAttribute("deployStopTime",    currentTimeMillis) }
    def undeployStartTime(s:Session)= { s.setAttribute("undeployStartTime", currentTimeMillis) }
    def undeployStopTime(s:Session) = { s.setAttribute("undeployStopTime",  currentTimeMillis) }

    def isVirtualApplianceState(s:Session, states:Set[String]) = {
        s.isAttributeDefined("virtualApplianceState")  && states.contains(s.getTypedAttribute[String]("virtualApplianceState"))
    }
    def isVirtualMachineState(s:Session, states:Set[String]) = {
        s.isAttributeDefined("currentVmState")  && states.contains(s.getTypedAttribute[String]("currentVmState"))
    }

    def loginFeed = Array(Map("loginuser" -> "admin", "loginpassword" -> "xabiquo")).circular

    def reportUserLoop(s:Session) = {
        if(s.isAttributeDefined("virtualApplianceState") && s.isAttributeDefined("undeployStopTime"))  {
            LOGREPO.info("vapp {}\t deployMs {}\t undeployMs {}",
                s.getTypedAttribute[String]("virtualapplianceId"),
                (s.getTypedAttribute[Long]("deployStopTime") - s.getTypedAttribute[Long]("deployStartTime")).asInstanceOf[java.lang.Long],
                (s.getTypedAttribute[Long]("undeployStopTime") - s.getTypedAttribute[Long]("undeployStartTime")).asInstanceOf[java.lang.Long])
        }
        else { LOGREPO.info("can't create vapp (or not undeploy)") }

        LOG.trace("{}",s);
        s.removeAttribute("virtualApplianceState")
    }

    def printSession(s:Session) = {
        LOG.info("{}",s); s
    }

    def logVirtualApplianceState(msg:String, s:Session) = {
        if(s.isAttributeDefined("virtualApplianceState") &&
            !s.getTypedAttribute[String]("virtualApplianceState").startsWith("LOCKED")) {
            LOG.debug(msg + "\tvapp {}\t{} {}",
            s.getTypedAttribute[String]("virtualapplianceId"),
            s.getTypedAttribute[String]("virtualApplianceState"), "");
        }; s
    }

    def logVmState(s:Session) = {
        if(s.isAttributeDefined("currentVmState")) {
            LOG.info("fucking vm is in state {} ", s.getTypedAttribute[String]("currentVmState"));
        }; s
    }

    val baseUrl  = System.getProperty("baseUrl",   "http://localhost:80")
    val numUsers = Integer.getInteger("numUsers", 1)
    val rampTime = Integer.getInteger("rampTime", 1).toLong

    val httpConf = httpConfig.baseURL(baseUrl).disableAutomaticReferer
}
