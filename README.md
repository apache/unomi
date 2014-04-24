WEMI Sandbox
============

A public sandbox project to test ideas for the WEMI specification

Requirements
------------
* JDK 6 or later, http://www.oracle.com/technetwork/java/javase/downloads/index.html
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

1. Before deploying, make sure that you have Apache Karaf properly installed.
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
5. If all went smoothly, you should be able to access the WEMI context script here : http://localhost:8181/context.js
 You should see a digitalData object populated with some values. If not something went wrong during the install.

 Integrating onto a page
 -----------------------

 Simply reference the WEMI script in your HTML as in the following example:

```
<script type="text/javascript">
    (function(){ var u=(("https:" == document.location.protocol) ? "https://localhost:8181/" : "http://localhost:8181/");
    var d=document, g=d.createElement('script'), s=d.getElementsByTagName('script')[0]; g.type='text/javascript'; g.defer=true; g.async=true; g.src=u+'context.js';
    s.parentNode.insertBefore(g,s); })();
</script>
```