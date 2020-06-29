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

Apache Unomi Integration tests
=================================

## Information
You will likely run into situation where you need to wait for the execution of your test.  
To do so please avoid long Thread.sleep(10000) it tend to make the test unstable, prefer a shorter sleep that you will repeat.  
e.g:  
```java
boolean isDone = false;
while (!isDone) {
    importConfiguration = importConfigurationService.load(itemId);
    if (importConfiguration != null && importConfiguration.getStatus() != null) {
        isDone = importConfiguration.getStatus().equals(RouterConstants.CONFIG_STATUS_COMPLETE_SUCCESS);
    }
    Thread.sleep(1000);
}
```

**NEVER** create dependencies between your test, even in the same class, the execution order is not guaranteed therefore you may not 
have what you expect and it could work fine on your machine but not on others.  

If possible clean what your test created at the end of its execution or at the very least make sure to use unique IDs  

When you need a service from Unomi to execute your test inject it with a filer:  
```java
@Inject @Filter(timeout = 60000)
protected ProfileService profileService;
``` 
This will ensure the service is available before starting the test and if you need a resource like an URL you can do something like this:  
```java
@Inject @Filter(value="(configDiscriminator=IMPORT)", timeout = 60000)
protected ImportExportConfigurationService<ImportConfiguration> importConfigurationService;
```
## Running integration tests

You can run the integration tests along with the build by doing:

    mvn clean install -P integration-tests
    
from the project's root directory

If you want to run the tests with a debugger, you can use the `it.karaf.debug` system property.
Here's an example:

    cd itests
    mvn clean install -Dit.karaf.debug=hold:true
    
The `hold:true` will tell the JVM to pause for you to connect a debugger. You can simply connect a remote debugger on $
port 5006 to debug the integration tests.

Here are the parameters supported by the `it.karaf.debug` property:

    hold:true - forces a wait for a remote debugger to connect 
    hold:false - continues even with no remote debugger connected
    port:XXXX allows to configure the binding port to XXXX
    
You can combine both parameters using a comma as a separator, as in the following example:

    mvn clean install -Dit.karaf.debug=hold:true,port=5006
    
## Running a single test

If you want to run a single test or single methods, following the instructions given here:
https://maven.apache.org/surefire/maven-failsafe-plugin/examples/single-test.html

Here's an example:

    mvn clean install -Dit.karaf.debug=hold:true -Dit.test=org.apache.unomi.itests.graphql.GraphQLEventIT