WEMI Sandbox
============

A public sandbox project to test ideas for the WEMI specification

Requirements
------------
* JDK 6 or later, http://www.oracle.com/technetwork/java/javase/downloads/index.html (JDK 7+ needed for tests execution)
* Apache Karaf 3.0+, http://karaf.apache.org
* Maven 3.0+, http://maven.apache.org

Building
--------

Simply type at the root of the project:
```
  mvn clean install
```

Deploying
---------

1. Before deploying, make sure that you have Apache Karaf properly installed. You will also have to increase the
default maximum memory size and perm gen size by adjusting the following environment values in the bin/setenv(.bat)
files (at the end of the file):

    ```
       export KARAF_OPTS="-XX:+CMSClassUnloadingEnabled -XX:+CMSPermGenSweepingEnabled"
       export JAVA_MAX_MEM=3G
       export JAVA_MAX_PERM_MEM=384M
    ```

2. If you haven't done it yet, install the WAR support into Karaf by doing the following in the Karaf command line:

    ```
       feature:install -v war
    ```

3. You will also need to install CXF and CDI (OpenWebBeans) for the REST service support

    ```
       feature:repo-add cxf 2.7.11
       feature:install -v cxf/2.7.11
       feature:install -v openwebbeans
       feature:install -v pax-cdi-web-openwebbeans
    ```

4. Copy the following KAR to the Karaf deploy directory, as in this example line:

    ```
      cp wemi-context-server/kar/target/wemi-context-server-kar-1.0-SNAPSHOT.kar ~/java/deployments/wemi-sandbox/apache-karaf-3.0.1/deploy/
    ```

5. (optional) If you prefer a clean startup without an non-blocking error on the native libraries needed for ElasticSearch,
you will need to specify the following command line parameter : 
    ```
      -Djava.library.path=/usr/local/elasticsearch-1.1.1/lib/sigar
    ```
    If this is not specified this will however not impact functionality of the server. The path points to a local 
    installation of ElasticSearch which you will need to download seperately.
   
6. If all went smoothly, you should be able to access the WEMI context script here : http://localhost:8181/context.js
 You should see a digitalData object populated with some values. If not something went wrong during the install.

Testing with an example page
----------------------------

A default test page is provided at the following URL:

```
   http://localhost:8181/index.html
```

This test page will trigger the loading of the WEMI /context.js script, which will try to retrieving the user context
or create a new one if it doesn't exist yet. It also contains an experimental integration with Facebook Login, but it
doesn't yet save the context pack to the WEMI server.

Integrating onto a page
-----------------------

 Simply reference the WEMI script in your HTML as in the following example:

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
    
11. You can then finally copy the WEMI Context Server KAR into the KARAF_HOME/instances/node2/deploy directory and 
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

Todo
----

- Look at possible integration with newsletter management systems such as MailChimp, for example to synchronize user data with collected info.
- Figure out how to load balance on the different cluster nodes: DNS round-robin, JavaScript timeouts, custom technology ?
