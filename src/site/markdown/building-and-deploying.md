<!--
  ~ Licensed to the Apache Software Foundation (ASF) under one or more
  ~ contributor license agreements.  See the NOTICE file distributed with
  ~ this work for additional information regarding copyright ownership.
  ~ The ASF licenses this file to You under the Apache License, Version 2.0
  ~ (the "License"); you may not use this file except in compliance with
  ~ the License.  You may obtain a copy of the License at
  ~
  ~      http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  -->
Building
========

Simply type at the root of the project:

    mvn clean install -P generate-package

The Maven build process will generate both a standalone package you can use directly to start the context server
(see "Deploying the generated package") or a KAR file that you can then deploy using a manual deployment process into
an already installed Apache Karaf server (see "Deploying into an existing Karaf server")

If you want to build and run the integration tests, you should instead use : 

    mvn -P integration-tests clean install

Deploying the generated package
-------------------------------

The "package" sub-project generates a pre-configured Apache Karaf installation that is the simplest way to get started.
Simply uncompress the `package/target/unomi-VERSION.tar.gz` (for Linux or Mac OS X) or
 `package/target/unomi-VERSION.zip` (for Windows) archive into the directory of your choice.
 
You can then start the server simply by using the command on UNIX/Linux/MacOS X : 

    ./bin/karaf start
    
or on Windows shell : 

    bin\karaf.bat start
    

Deploying into an existing Karaf server
---------------------------------------

This is only needed if you didn't use the generated package. Also, this is the preferred way to install a development
environment if you intend to re-deploy the context server KAR iteratively.

Additional requirements:
 - Apache Karaf 3.0.2+, http://karaf.apache.org
 - Local copy of the Elasticsearch ZIP package, available here : http://www.elasticsearch.org

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
go to the Elasticsearch website (http://www.elasticsearch.org)  and download the ZIP package. Decompress it somewhere 
on your disk and copy all the files from the lib/sigar directory into Karaf's lib/sigar directory 
(must be created first) EXCEPT THE SIGAR.JAR file.

3. Install the WAR support, CXF into Karaf by doing the following in the Karaf command line:

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
      cp kar/target/unomi-kar-1.0.0-SNAPSHOT.kar ~/java/deployments/unomi/apache-karaf-3.0.1/deploy/
    ```
   
6. If all went smoothly, you should be able to access the context script here : http://localhost:8181/cxs/cluster .
 You should be able to login with karaf / karaf and see basic server information. If not something went wrong during the install.
 
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
