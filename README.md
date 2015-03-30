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
 package/target/context-server-package-VERSION.tar.gz (for Windows) archive into the directory of your choice.
 
You can then start the server simply by using the command on UNIX/Linux/MacOS X : 

    bin/karaf start    
    
or on Windows shell : 

    bin\karaf.bat start
    
Installing the MaxMind GeoIPLite2 IP lookup database
----------------------------------------------------

By default, the Context Server will use a remote IP lookup service but this can proove very inefficient in terms of
 performance especially as the service throttles the lookups and is limited. Also this will prevent working offline.
The IP lookup service also supports downloadable maps from MaxMind that you can get here : 
http://dev.maxmind.com/geoip/geoip2/geolite2/

Simply download the desired maps into the "etc" directory and then adjust the following configuration file : 

    etc/org.oasis_open.contextserver.plugins.request.cfg
    
it should point to a file such as : 

    request.ipDatabase.location=${karaf.etc}/GeoLite2-City.mmdb
    
(by default we reference the free city database).

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
       feature:install -v openwebbeans
       feature:install -v pax-cdi-web-openwebbeans
    ```

4. Create a new etc/org.apache.cxf.osgi.cfg file and put the following property inside :

    ```
       org.apache.cxf.servlet.context=/cxs
    ```

5. Copy the following KAR to the Karaf deploy directory, as in this example line:

    ```
      cp kar/target/context-server-kar-1.0-SNAPSHOT.kar ~/java/deployments/unomi/apache-karaf-3.0.1/deploy/
    ```
   
6. If all went smoothly, you should be able to access the context script here : http://localhost:8181/context.js
 You should see a digitalData object populated with some values. If not something went wrong during the install.
 
Changing the default configuration
----------------------------------

If you want to change the default configuration, you can perform any modification you want in the karaf/etc directory.

The context server configuration is kept in the etc/org.oasis_open.contextserver.web.cfg . It defines the
addresses and port where it can be found :

    contextserver.address=localhost
    contextserver.port=8181
    contextserver.secureAddress=localhost
    contextserver.securePort=9443

If you need to specify an ElasticSearch cluster name that is different than the default, it is recommended to do this
BEFORE you start the server for the first time, or you will loose all the data you have stored previously.

To change the cluster name, first create a file called 

    etc/org.oasis_open.contextserver.persistence.elasticsearch.cfg

with the following contents:

    cluster.name=contextElasticSearch
    index.name=context
    elasticSearchConfig=file:${karaf.etc}/elasticsearch.yml

And replace the cluster.name parameter here by your cluster name.

You can also put an elasticsearch configuration file in etc/elasticsearch.yml ,
and put any standard ElasticSearch configuration options in this last file.

If you want your context server to be a client only on a cluster of elasticsearch nodes, just set the node.data property
to false.

REST API Security
-----------------

The Context Server REST API is protected using JAAS authentication and using Basic or Digest HTTP auth.
By default, the login/password for the REST API full administrative access is "karaf/karaf".

The generated package is also configured with a default SSL certificate. You can change it by following these steps :

1. Replace the existing keystore in /etc/keystore by your own certificate :
 
    http://wiki.eclipse.org/Jetty/Howto/Configure_SSL
    
2. Update the keystore and certificate password in /etc/custom.properties file :
 
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

If you want to use a cluster of Karaf instances instead of a single instance, you can use Karaf's instance administration
to generate the nodes quickly on the same machine. Here are the steps to do this : 

1. With all Karaf instances shut down, in KARAF_HOME/bin launch the ./instance list script, you should get something like this : 

    ```
    ./instance list
    SSH Port | RMI Registry | RMI Server | State   | PID  | Name 
    -------------------------------------------------------------
        8101 |         1099 |      44444 | Stopped | 0    | root 
    ```

2. Create a new instance with the following command:

    ```
    ./instance create node2
    ```
    
3. You should change the default Java options of the instance, you can do so with a command like this one : 
    
    ```
    ./instance opts-change root "-server -Xmx3G -XX:MaxPermSize=384M -Dcom.sun.management.jmxremote -XX:+UnlockDiagnosticVMOptions -XX:+UnsyncloadClass"
    ```        
    It is recommmended to do this even for the root node, otherwise if you use the instance command to start it it will
    use default values that are too small.
   
4. You can then start the node by using the command :  
 
    ```
    ./instance start node2
    ```

5. Use "instance list" to get the SSH port of the new node : 

    ```
    SSH Port | RMI Registry | RMI Server | State   | PID  | Name 
    -------------------------------------------------------------
        8101 |         1099 |      44444 | Started | 3853 | root 
        8103 |         1101 |      44446 | Started | 4180 | node2
    ```

6. You can then connect to it using : 

    ```
    ssh karaf@localhost -p 8103 
    ```
    
    The default password is "karaf". BE SURE TO CHANGE THIS WHEN YOU GO TO PRODUCTION !
   
7. You can then install all the required features by using the following commands : 

    ```
       feature:install -v war
       feature:repo-add cxf 2.7.11
       feature:install -v cxf/2.7.11
       feature:install -v openwebbeans
       feature:install -v pax-cdi-web-openwebbeans
    ```
       
8. You can then disconnect from the node by using:

    ```
    logout
    ```
    
    and shutdown the node instance because we will need to perform a configuration change for the HTTP port if you 
    are running the instances on the same machine. To shut it down use : 
   
    ```
    ./instance stop node2
    ```

9. You should then change the default HTTP port by changing the value in the KARAF_HOME/instances/node2/etc/jetty.xml 
file from 8181 to something else. In this example we have changed to port to 8182 : 
 
    ```
     <Call name="addConnector">
         <Arg>
             <New class="org.eclipse.jetty.server.nio.SelectChannelConnector">
                 <Set name="host">
                     <Property name="jetty.host" />
                 </Set>
                 <Set name="port">
                     <Property name="jetty.port" default="8182" />
                 </Set>
    ```
 
10. You can then start your instance by using the following command : 

    ```
    ./instance start node2
    ```
    
11. You can then finally copy the Context Server KAR into the KARAF_HOME/instances/node2/deploy directory and 
everything should be up and running.

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

    etc/users.properties

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

You can adjust this setting by using the following setting in the etc/elasticsearch.yml file : 

    security.ipranges: localhost,127.0.0.1,::1,10.0.1.0-10.0.1.255

Step 3 : Follow industry recommended best practices for securing ElasticSearch

You may find more valuable recommendations here : 

- https://www.found.no/foundation/elasticsearch-security/
- http://www.elasticsearch.org/blog/scripting-security/
    
Step 4 : Setup a proxy in front of the context server

As an alternative to an application-level firewall, you could also route all traffic to the context server through
a proxy, and use it to filter any communication.

Todo
----

- Look at possible integration with newsletter management systems such as MailChimp, for example to synchronize profile data with collected info.
- Integrate with machine learning implementations such as Prediction.io or Apache Mahout
