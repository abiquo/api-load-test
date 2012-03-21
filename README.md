Abiquo API load test
====================

This repo contains [Gatling scripts](https://github.com/excilys/gatling) to test the [Abiquo API](http://community.abiquo.com/display/ABI20/API+Reference)

Getting started
---------------

Install Gatling

    $ wget https://github.com/downloads/excilys/gatling/gatling-charts-highcharts-1.0.3-bundle.tar.gz
    $ tar xvf gatling-charts-highcharts-1.0.3-bundle.tar.gz

Get the scripts into the *user-files* folder

	$ cd gatling-charts-highcharts-1.0.3-bundle/user-files
    $ rm -rf *
    $ git clone git@github.com:abiquo/api-load-test

Before running the test check it point to the correct api location  

    $ nanu simulations/abiquo-XXX@default.scala

    val urlBase = "http://localhost:80"

Gatling the api  
    $ ./bin/gatling.sh


User simulation
---------------

Basic users CRUD operations, composed by 

* ''read'' chain 
Login and get all the users

* ''write'' chain
Login, create a new user, check the user can be retrieved, modify it and finally delete

By default each chain is executed during 5minutes

To modify the number of users look at the bottom of _abiquo-users@default.scala_ file

    runSimulation(
        write_scn.configure users 100 	ramp 100 protocolConfig httpConfig.baseURL(urlBase),
        read_sn.configure 	users 100 	ramp 100 protocolConfig httpConfig.baseURL(urlBase)
    )

use the _ramp_ to control the user frequence (100 users with ramp 100 mean 1 new session each second)


Virtual simulation
------------------

In order to create virtual datacenters you need to configure a datacenter and a hypervisor, edit _data/infrastructure.csv_ to configure the remote services ip and the hypervisor (always use VMX_04 (esx) hypervisor, it doesn't really support this variable yet)


* initInfrastructureChain
Login, created a datacenter, a hypervisor and refresh the datacenter repository

* ''read'' chain
Login and get cloud usage, enterpirse, virtualdatacenter and virtualappliance stadistics.
Get virtual datacenters, virtual appliances and virtual machines from the enterprise

* ''write'' chain
Login, get the datacenter id and a virtual machine template. 
Create a virtualdatacenter, get it, create a virtualappliance, get it, create a virtualmachien, get it. Get vm tasks and vapp pricing, then start deleting the vm, vapp and finally the vdc

Look at the bottom of _abiquo-virtual@default.scala_ to modify the number of users for each chain