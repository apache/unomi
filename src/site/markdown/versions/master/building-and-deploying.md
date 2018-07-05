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

Initial Setup
-------------

1) Install J2SE 8.0 SDK (or later), which can be downloaded from
   http://www.oracle.com/technetwork/java/javase/downloads/index.html

2) Make sure that your JAVA_HOME environment variable is set to the newly installed
   JDK location, and that your PATH includes %JAVA_HOME%\bin (windows) or
   $JAVA_HOME$/bin (unix).

3) Install Maven 3.0.3 (or later), which can be downloaded from
   http://maven.apache.org/download.html. Make sure that your PATH includes
   the MVN_HOME/bin directory.


Building
--------

1) Change to the top level directory of Apache Unomi source distribution.
2) Run

         $> mvn clean install

   This will compile Apache Unomi and run all of the tests in the
   Apache Unomi source distribution. Alternatively, you can run

         $> mvn -P \!integration-tests,\!performance-tests clean install

   This will compile Apache Unomi without running the tests and takes less
   time to build.

3) The distributions will be available under "package/target" directory.

Installing an ElasticSearch server
----------------------------------

Starting with version 1.2, Apache Unomi no longer embeds an ElasticSearch server as this is no longer supported by 
the developers of ElasticSearch. Therefore you will need to install a standalone ElasticSearch using the following steps:

1. Download an ElasticSearch version. Here's the version you will need depending
on your version of Apache Unomi.

    Apache Unomi <= 1.2 : [https://www.elastic.co/downloads/past-releases/elasticsearch-5-1-2](https://www.elastic.co/downloads/past-releases/elasticsearch-5-1-2)    
    Apache Unomi >= 1.3 : [https://www.elastic.co/downloads/past-releases/elasticsearch-5-6-3](https://www.elastic.co/downloads/past-releases/elasticsearch-5-6-3)
        
2. Uncompress the downloaded package into a directory

3. In the config/elasticsearch.yml file, uncomment and modify the following line :

        cluster.name: contextElasticSearch
    
4. Launch the server using

        bin/elasticsearch (Mac, Linux)
        bin\elasticsearch.bat (Windows)

5. Check that the ElasticSearch is up and running by accessing the following URL : 

    [http://localhost:9200](http://localhost:9200)    

Deploying the generated binary package
--------------------------------------

The "package" sub-project generates a pre-configured Apache Karaf installation that is the simplest way to get started.
Simply uncompress the package/target/unomi-VERSION.tar.gz (for Linux or Mac OS X) or
 package/target/unomi-VERSION.zip (for Windows) archive into the directory of your choice.
 
You can then start the server simply by using the command on UNIX/Linux/MacOS X : 

    ./bin/karaf    
    
or on Windows shell : 

    bin\karaf.bat
    
You will then need to launch (only on the first Karaf start) the Apache Unomi packages using the following Apache Karaf 
shell command:

    unomi:start        

Deploying into an existing Karaf server
---------------------------------------

This is only needed if you didn't use the generated package. Also, this is the preferred way to install a development
environment if you intend to re-deploy the context server KAR iteratively.

Additional requirements:
* Apache Karaf 3.x, http://karaf.apache.org

1. Before deploying, make sure that you have Apache Karaf properly installed. You will also have to increase the
default maximum memory size and perm gen size by adjusting the following environment values in the bin/setenv(.bat)
files (at the end of the file):

    ```
       MY_DIRNAME=`dirname $0`
       MY_KARAF_HOME=`cd "$MY_DIRNAME/.."; pwd`
       export JAVA_MAX_MEM=3G
       export JAVA_MAX_PERM_MEM=384M
    ```
    
2. Install the WAR support, CXF and Karaf Cellar into Karaf by doing the following in the Karaf command line:

    ```
       feature:repo-add cxf 3.0.2
       feature:repo-add cellar 3.0.3
       feature:repo-add mvn:org.apache.unomi/unomi-kar/VERSION/xml/features
       feature:install unomi-kar
    ```

4. Create a new $MY_KARAF_HOME/etc/org.apache.cxf.osgi.cfg file and put the following property inside :

    ```
       org.apache.cxf.servlet.context=/cxs
    ```
   
5. If all went smoothly, you should be able to access the context script here : http://localhost:8181/cxs/cluster .
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
