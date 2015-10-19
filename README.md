Context Server
==============

A public project that implements the Context Server specification

License
-------
The source code is available under the Apache License V2 and is copyrighted 2014-2015 Jahia Solutions

Requirements
------------
* JDK 7 or later, http://www.oracle.com/technetwork/java/javase/downloads/index.html
* Maven 3.0+, http://maven.apache.org

Building
--------

Simply type at the root of the project:
```
  mvn clean install -P generate-package
```

The Maven build process will generate both a standalone package you can use directly to start the context server
(see "Deploying the generated package") or a KAR file that you can then deploy using a manual deployment process into
an already installed Apache Karaf server (see "Deploying into an existing Karaf server")

If you want to build and run the integration tests, you should instead use : 

    mvn -P integration-tests clean install

Deploying the generated package
-------------------------------

The "package" sub-project generates a pre-configured Apache Karaf installation that is the simplest way to get started.
Simply uncompress the package/target/context-server-package-VERSION.tar.gz (for Linux or Mac OS X) or
 package/target/context-server-package-VERSION.zip (for Windows) archive into the directory of your choice.
 
You can then start the server simply by using the command on UNIX/Linux/MacOS X : 

    ./bin/karaf start    
    
or on Windows shell : 

    bin\karaf.bat start
    

Deploying into an existing Karaf server
---------------------------------------

This is only needed if you didn't use the generated package. Also, this is the preferred way to install a development
environment if you intend to re-deploy the context server KAR iteratively.

Additional requirements:
* Apache Karaf 3.0.2+, http://karaf.apache.org
* Local copy of the ElasticSearch ZIP package, available here : http://www.elasticsearch.org

1. Before deploying, make sure that you have Apache Karaf properly installed. You will also have to increase the
default maximum memory size and perm gen size by adjusting the following environment values in the bin/setenv(.bat)
files (at the end of the file):

    ```
       MY_DIRNAME=`dirname $0`
       MY_KARAF_HOME=`cd "$MY_DIRNAME/.."; pwd`
       export KARAF_OPTS="-Djava.library.path=$MY_KARAF_HOME/lib/sigar"
       export JAVA_MAX_MEM=3G
       export JAVA_MAX_PERM_MEM=384M
    ```
    
2. You will also need to have the Hyperic Sigar native libraries in your Karaf installation, so in order to this
go to the ElasticSearch website (http://www.elasticsearch.org)  and download the ZIP package. Decompress it somewhere 
on your disk and copy all the files from the lib/sigar directory into Karaf's lib/sigar directory 
(must be created first) EXCEPT THE SIGAR.JAR file.

3. Install the WAR support, CXF and CDI (OpenWebBeans) into Karaf by doing the following in the Karaf command line:

    ```
       feature:install -v war
       feature:repo-add cxf 2.7.11
       feature:install -v cxf/2.7.11
    ```

4. Create a new $MY_KARAF_HOME/etc/org.apache.cxf.osgi.cfg file and put the following property inside :

    ```
       org.apache.cxf.servlet.context=/cxs
    ```

5. Copy the following KAR to the Karaf deploy directory, as in this example line:

    ```
      cp kar/target/context-server-kar-1.0-SNAPSHOT.kar ~/java/deployments/unomi/apache-karaf-3.0.1/deploy/
    ```
   
6. If all went smoothly, you should be able to access the context script here : http://localhost:8181/cxs/cluster .
 You should be able to login with karaf / karaf and see basic server information. If not something went wrong during the install.

Installing the MaxMind GeoIPLite2 IP lookup database
----------------------------------------------------

The Context Server requires an IP database in order to resolve IP addresses to user location.
The GeoLite2 database can be downloaded from MaxMind here :
http://dev.maxmind.com/geoip/geoip2/geolite2/

Simply download the GeoLite2-City.mmdb file into the "etc" directory.

Installing Geonames database
----------------------------

Context server includes a geocoding service based on the geonames database ( http://www.geonames.org/ ). It can be
used to create conditions on countries or cities.

In order to use it, you need to install the Geonames database into . Get the "allCountries.zip" database from here :
http://download.geonames.org/export/dump/

Download it and put it in the "etc" directory, without unzipping it.
Edit $MY_KARAF_HOME/etc/org.oasis_open.contextserver.geonames.cfg and set request.geonamesDatabase.forceImport to true, import should start right away.
Otherwise, import should start at the next startup. Import runs in background, but can take about 15 minutes.
At the end, you should have about 4 million entries in the geonames index.
 
Changing the default configuration
----------------------------------

If you want to change the default configuration, you can perform any modification you want in the $MY_KARAF_HOME/etc directory.

The context server configuration is kept in the $MY_KARAF_HOME/etc/org.oasis_open.contextserver.web.cfg . It defines the
addresses and port where it can be found :

    contextserver.address=localhost
    contextserver.port=8181
    contextserver.secureAddress=localhost
    contextserver.securePort=9443

If you need to specify an ElasticSearch cluster name that is different than the default, it is recommended to do this
BEFORE you start the server for the first time, or you will loose all the data you have stored previously.

To change the cluster name, first create a file called 

    $MY_KARAF_HOME/etc/org.oasis_open.contextserver.persistence.elasticsearch.cfg

with the following contents:

    cluster.name=contextElasticSearch
    index.name=context
    elasticSearchConfig=file:${karaf.etc}/elasticsearch.yml

And replace the cluster.name parameter here by your cluster name.

You can also put an elasticsearch configuration file in $MY_KARAF_HOME/etc/elasticsearch.yml ,
and put any standard ElasticSearch configuration options in this last file.

If you want your context server to be a client only on a cluster of elasticsearch nodes, just set the node.data property
to false.

REST API Security
-----------------

The Context Server REST API is protected using JAAS authentication and using Basic or Digest HTTP auth.
By default, the login/password for the REST API full administrative access is "karaf/karaf".

The generated package is also configured with a default SSL certificate. You can change it by following these steps :

1. Replace the existing keystore in $MY_KARAF_HOME/etc/keystore by your own certificate :
 
    http://wiki.eclipse.org/Jetty/Howto/Configure_SSL
    
2. Update the keystore and certificate password in $MY_KARAF_HOME/etc/custom.properties file :
 
```
    org.osgi.service.http.secure.enabled = true
    org.ops4j.pax.web.ssl.keystore=${karaf.etc}/keystore
    org.ops4j.pax.web.ssl.password=changeme
    org.ops4j.pax.web.ssl.keypassword=changeme
    org.osgi.service.http.port.secure=9443
```

You should now have SSL setup on Karaf with your certificate, and you can test it by trying to access it on port 9443.

 
Running the integration tests
-----------------------------

The integration tests are not executed by default to make build time minimal, but it is recommended to run the 
integration tests at least once before using the server to make sure that everything is ok in the build. Another way
to use these tests is to run them from a continuous integration server such as Jenkins, Apache Gump, Atlassian Bamboo or
 others. 
 
Note : the integration tests require a JDK 7 or more recent !

To run the tests simply activate the following profile : 
 
    mvn -P integration-tests clean install

Running the performance tests
-----------------------------

Performance tests are based on Gatling. You need to have a running context server or cluster of servers before
executing the tests.

Test parameteres are editable in the performance-tests/src/test/scala/unomi/Parameters.scala file. baseUrls should
contains the URLs of all your cluster nodes

Run the test by using the gatling.conf file in performance-tests/src/test/resources :

```
    export GATLING_CONF=<path>/performance-tests/src/test/resources
    gatling.sh
```

Reports are generated in performance-tests/target/results.


Testing with an example page
----------------------------

A default test page is provided at the following URL:

```
   http://localhost:8181/index.html
```

This test page will trigger the loading of the /context.js script, which will try to retrieving the user context
or create a new one if it doesn't exist yet. It also contains an experimental integration with Facebook Login, but it
doesn't yet save the context back to the context server.

Integrating onto a page
-----------------------

 Simply reference the context script in your HTML as in the following example:

```javascript
<script type="text/javascript">
    (function(){ var u=(("https:" == document.location.protocol) ? "https://localhost:8181/" : "http://localhost:8181/");
    var d=document, g=d.createElement('script'), s=d.getElementsByTagName('script')[0]; g.type='text/javascript'; g.defer=true; g.async=true; g.src=u+'context.js';
    s.parentNode.insertBefore(g,s); })();
</script>
```

Cluster setup
-------------

Context server relies on Elasticsearch to discover and configure its cluster. You just need to install multiple context
servers on the same network, and enable the discovery protocol in $MY_KARAF_HOME/etc/org.oasis_open.contextserver.persistence.elasticsearch.cfg file :

    discovery.zen.ping.multicast.enabled=true

All nodes on the same network, sharing the same cluster name will be part of the same cluster.

###Recommended configurations

It is recommended to have one node dedicated to the context server, where the other nodes take care of the
Elasticsearch persistence. The node dedicated to the context server will have node.data set to false.

#### 2 nodes  configuration
One node dedicated to context server, 1 node for elasticsearch storage.

Node A :

    node.data=true
    numberOfReplicas=0
    monthlyIndex.numberOfReplicas=0

Node B :

    node.data=false
    numberOfReplicas=0
    monthlyIndex.numberOfReplicas=0

#### 3 nodes configuration
One node dedicated to context server, 2 nodes for elasticsearch storage with fault-tolerance

Node A :

    node.data=false
    numberOfReplicas=1
    monthlyIndex.numberOfReplicas=1

Node B :

    node.data=true
    numberOfReplicas=1
    monthlyIndex.numberOfReplicas=1

Node C :

    node.data=true
    numberOfReplicas=1
    monthlyIndex.numberOfReplicas=1

### Specific configuration
If multicast is not allowed on your network, you'll need to switch to unicast protocol and manually configure the server IPs. This can be
done by disabling the elasticsearch automatic discovery in $MY_KARAF_HOME/etc/org.oasis_open.contextserver.persistence.elasticsearch.cfg :

    discovery.zen.ping.multicast.enabled=false


And then set the property discovery.zen.ping.unicast.hosts in $MY_KARAF_HOME/etc/elasticsearch.yml files :


    discovery.zen.ping.unicast.hosts: [‘192.168.0.1:9300', ‘192.168.0.2:9300']


More information and configuration options can be found at :
[https://www.elastic.co/guide/en/elasticsearch/reference/current/modules-discovery.html](https://www.elastic.co/guide/en/elasticsearch/reference/current/modules-discovery.html)



JDK Selection on Mac OS X
-------------------------

You might need to select the JDK to run the tests in the itests subproject. In order to do so you can list the 
installed JDKs with the following command : 

    /usr/libexec/java_home -V
    
which will output something like this : 

    Matching Java Virtual Machines (7):
        1.7.0_51, x86_64:	"Java SE 7"	/Library/Java/JavaVirtualMachines/jdk1.7.0_51.jdk/Contents/Home
        1.7.0_45, x86_64:	"Java SE 7"	/Library/Java/JavaVirtualMachines/jdk1.7.0_45.jdk/Contents/Home
        1.7.0_25, x86_64:	"Java SE 7"	/Library/Java/JavaVirtualMachines/jdk1.7.0_25.jdk/Contents/Home
        1.6.0_65-b14-462, x86_64:	"Java SE 6"	/Library/Java/JavaVirtualMachines/1.6.0_65-b14-462.jdk/Contents/Home
        1.6.0_65-b14-462, i386:	"Java SE 6"	/Library/Java/JavaVirtualMachines/1.6.0_65-b14-462.jdk/Contents/Home
        1.6.0_65-b14-462, x86_64:	"Java SE 6"	/System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Home
        1.6.0_65-b14-462, i386:	"Java SE 6"	/System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Home


You can then select the one you want using : 

    export JAVA_HOME=`/usr/libexec/java_home -v 1.7.0_51`
    
and then check that it was correctly referenced using: 

    java -version
    
which should give you a result such as this: 

    java version "1.7.0_51"
    Java(TM) SE Runtime Environment (build 1.7.0_51-b13)
    Java HotSpot(TM) 64-Bit Server VM (build 24.51-b03, mixed mode)
        
Automatic profile merging
-------------------------

The context server is capable of merging profiles based on a common property value. In order to use this, you must
add the MergeProfileOnPropertyAction to a rule (such as a login rule for example), and configure it with the name
 of the property that will be used to identify the profiles to be merged. An example could be the "email" property,
 meaning that if two (or more) profiles are found to have the same value for the "email" property they will be merged
 by this action.
 
Upon merge, the old profiles are marked with a "mergedWith" property that will be used on next profile access to delete
the original profile and replace it with the merged profile (aka "master" profile). Once this is done, all cookie tracking
will use the merged profile.

To test, simply configure the action in the "login" or "facebookLogin" rules and set it up on the "email" property. 
Upon sending one of the events, all matching profiles will be merged.

Securing a production environment
---------------------------------

Before going live with a project, you should *absolutely* read the following section that will help you setup a proper 
secure environment for running your context server.         

Step 1: Install and configure a firewall 

You should setup a firewall around your cluster of context servers and/or ElasticSearch nodes. If you have an 
application-level firewall you should only allow the following connections open to the whole world : 

 - http://localhost:8181/context.js
 - http://localhost:8181/eventcollector

All other ports should not be accessible to the world.

For your Context Server client applications (such as the Jahia CMS), you will need to make the following ports 
accessible : 

    8181 (Context Server HTTP port) 
    9443 (Context Server HTTPS port)
    
The context server actually requires HTTP Basic Auth for access to the Context Server administration REST API, so it is
highly recommended that you design your client applications to use the HTTPS port for accessing the REST API.

The user accounts to access the REST API are actually routed through Karaf's JAAS support, which you may find the
documentation for here : 

 - http://karaf.apache.org/manual/latest/users-guide/security.html
    
The default username/password is 

    karaf/karaf
    
You should really change this default username/password as soon as possible. To do so, simply modify the following
file : 

    $MY_KARAF_HOME/etc/users.properties

For your context servers, and for any standalone ElasticSearch nodes you will need to open the following ports for proper
node-to-node communication : 9200 (ElasticSearch REST API), 9300 (ElasticSearch TCP transport)

Of course any ports listed here are the default ports configured in each server, you may adjust them if needed.

Step 2 : Adjust the Context Server IP filtering

By default the Context Server limits to connections to port 9200 and 9300 to the following IP ranges

    - localhost
    - 127.0.0.1
    - ::1
    - the current subnet (i.e., 192.168.1.0-192.168.1.255)
    
(this is done using a custom plugin for ElasticSearch, that you may find here : 
https://github.com/Jahia/unomi/tree/master/context-server/persistence-elasticsearch/plugins/security)

You can adjust this setting by using the following setting in the $MY_KARAF_HOME/etc/elasticsearch.yml file : 

    security.ipranges: localhost,127.0.0.1,::1,10.0.1.0-10.0.1.255

Step 3 : Follow industry recommended best practices for securing ElasticSearch

You may find more valuable recommendations here : 

- https://www.elastic.co/blog/found-elasticsearch-security
- https://www.elastic.co/blog/scripting-security
    
Step 4 : Setup a proxy in front of the context server

As an alternative to an application-level firewall, you could also route all traffic to the context server through
a proxy, and use it to filter any communication.

Todo
----

- Look at possible integration with newsletter management systems such as MailChimp, for example to synchronize profile data with collected info.
- Integrate with machine learning implementations such as Prediction.io or Apache Mahout
