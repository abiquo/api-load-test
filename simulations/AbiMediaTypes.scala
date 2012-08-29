object AbiMediaTypes {
	val ABQ_VERSION = """; version=2.2 """
	  
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
    val MT_CLOUDUSAGE="""application/vnd.abiquo.cloudusage+xml""" + ABQ_VERSION
    val MT_DC_REPO  = """application/vnd.abiquo.datacenterrepository+xml""" + ABQ_VERSION
    val MT_VDCS     = """application/vnd.abiquo.virtualdatacenters+xml""" + ABQ_VERSION
    val MT_VDC      = """application/vnd.abiquo.virtualdatacenter+xml""" + ABQ_VERSION
    val MT_VAPPS    = """application/vnd.abiquo.virtualappliances+xml""" + ABQ_VERSION
    val MT_VAPP     = """application/vnd.abiquo.virtualappliance+xml""" + ABQ_VERSION
    val MT_VAPP_STATE   = """application/vnd.abiquo.virtualappliancestate+xml""" + ABQ_VERSION
    val MT_VMS_EXTENDED = """application/vnd.abiquo.virtualmachineswithnodeextended+xml""" + ABQ_VERSION
    val MT_VM_EXTENDED  = """application/vnd.abiquo.virtualmachinewithnodeextended+xml""" + ABQ_VERSION
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
}