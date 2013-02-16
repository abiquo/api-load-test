Abiquo API load test
====================

This repo contains [Gatling scripts](https://github.com/excilys/gatling) to test the [Abiquo API](http://community.abiquo.com/display/ABI22/API+Reference)

Run using Maven
------------------

Acepted parameters

* *baseUrl* abiquo api location
* *numUsers* total users to simulate
* *rampTime* in seconds, at this time all the _numUsers_ will be running

See _gatling.conf_ for timeout (simulation/request) configuration, by default max simulation time is 2hours and request timeout to 1.6 minutes (!!!). Also check _logback.xml_ to log failed responses.

VirtualResources
----------------
````
$ mvn gatling:execute -Dsimulation=VirtualResources -DbaseUrl=http://10.60.1.223:80 -DnumUsers=10 -DrampTime=30
````

Each user creates repeat this iteration
* create a virtual datacenter (''numVdcs'' loop)
* create a virtual appliance (''numVapps'' loop)
* create ''numVms'' virtual machin in the virtual appliance
* deploy the virtual appliance and wait until vapp state is DEPLOYED /!\
* wait during ''vappDeployTime'' seconds with the vapp in DEPLOYED state
* if ''powerOffVm'' power off all the vms
* if ''reconfigure'' 50% chance to increment the CPU of each vm
* if ''undeployVapp'' undeploy the virtual appliance and wait until vapp state is NOT_DEPLOYED /!\
* if ''deleteVapp'' deletes the vapp
* if ''deleteVdc'' deletes the vdc

/!\ *NOTE* if _NEED_SYNCH_ will try to repeat the action, but for _UNKNOWN_ will wait


Configuration
-------------
Users are configured reading a line of each of the follow file sequentially and circular

*data/login.csv*
* loginuser
* loginpassword

*data/datacenter.csv* : Required setup. Check this configuration is OK in the abiquo API before running the simulation.
* datacenterId
* hypervisorType
* templateId
*NOTE*: it is important to check _templateId_ is *compatible* with _hypervisorType_.

*data/virtualdatacenter.csv* : Define the number of iterations
** numVdcs
** numVapps
** numVms

*data/virtualmachine.csv*
* vappDeployTime
* powerOffVm
* reconfigure
* undeployVapp
* deleteVapp
* deleteVdc

Output
------
* _results/run..._ gatling reports
* _abiquo.log_ abiquo deploy specific

At the end of each deploy/undeploy prints iteration numbers

* *vappId* virtual appliance identifier
* *deployMs* time from NOT_ALLOCATED to DEPLOYED virtual appliance
* *undeployMs* time from DEPLOYED to NOT_DEPLOYED/UNKNOWN

and a summary of all the virtual machine jobs:
* vm taskId jobId taskType jobType state creation timestamp
