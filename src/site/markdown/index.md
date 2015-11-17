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
  
![Unomi logo](images/apache-unomi-380x85.png)

## Reference implementation of the OASIS Context Server specification

### In a few words

Apache unomi is the reference implementation of the upcoming OASIS Context Server (CXS) standard 
(https://www.oasis-open.org/committees/cxs/) to help standardize personalization of online experience
while promoting ethical web experience management and increased user privacy controls.

Apache unomi gathers information about users actions, information that is processed and stored by unomi services. 
The collected information can then be used to personalize content, derive insights on user behavior, categorize the 
profiles into segments along user-definable dimensions or acted upon by algorithms.

### Features

![Unomi features](images/unomi-features.png)

* Simple REST API for retrieving the profile context and collecting user-triggered events (page view, click, downloads, etc...)
* Full & simple REST API for Context Server administration
* Highly scalable architecture
* Fully OSGi compliant application
* Persistence & query layer uses ElasticSearch (other providers may be implemented in the future)
* Uses Apache Karaf as the OSGi runtime (supports both Apache Felix and Eclipse Equinox OSGi implementations)
* Very simple to deploy and install (simply unzip & run)
* Extensible through plugin architecture (using OSGi & simple JSON descriptors)

### At a glance

![Unomi input output](images/unomi-input-output.png)

Unomi is an implementation of the Context Server, and therefore always provider the context of the current user 
interacting with any system. Using the context, events may be sent to the context server that will trigger rules. 
These rules will executing matching actions that may either perform context updates or even sent data to external
systems such as BigData frameworks.

### License

The source code is available under the Apache License V2 and is copyrighted 2014-2015 Jahia Solutions

### Requirements

* JDK 7 or later, http://www.oracle.com/technetwork/java/javase/downloads/index.html
* Maven 3.0+, http://maven.apache.org
        
### Todo

- Look at possible integration with newsletter management systems such as MailChimp, for example to synchronize profile data with collected info.
- Integrate with machine learning implementations such as Prediction.io or Apache Mahout
