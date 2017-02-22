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

# The Open Source Customer Data Platform

## Apache Unomi in one sentence
Apache Unomi is __a Java Open Source customer data platform__, a Java server designed to manage customers, 
leads and visitors’ data and help personalize customers’ experiences. 

## For developers 
* Uses ElasticSearch for data storage, dynamic data structure
* Highly scalable architecture
* Very simple to deploy and install (simply unzip & run)
* Uses Apache Karaf as the OSGi runtime 
* Full & simple REST API
* Extensible through plugin architecture (using OSGi & simple JSON descriptors)
* Apache Open Source project

## For IT stakeholders and business users
* First-Party Data Collection and Ownership
* Persistent and consolidated profiles for all your audience (customers & leads), storing anonymous and personally 
identifiable information
* Regulation-proof and privacy management built-in 
* Real-Time Decisioning (Scoring logics, segmentation)
* Open standard, easy integration and extension

## Why would you need a Customer Data Platform ? 
Organizations collect data from multiple sources and channels - sales, web, emails, mobile, brick and mortar - and 
all of this data is spread across different departments and technologies. As a result, many professionals are lacking 
the data they need to do their job, especially data analysts and marketers.

A customer data platform helps business users collect all the customer data in one place, providing a complete view 
of the customers. When the data is collected, the customer data platform will also play a key role in choosing which 
content or offer is more relevant to a customer. 

## The Apache Unomi advantage 
Each organization is unique and will always have specific needs, this is why Apache Unomi has been designed to 
be extended and to ease the integration of external data. The embedded features such as segmentation, scoring and 
built-in privacy will be appreciated by business users while horizontal scalability and open source positioning will 
be loved by developers and architects. 

Apache Unomi is also the reference implementation of the upcoming OASIS Context Server (CXS) standard 
(https://www.oasis-open.org/committees/cxs/) to help standardize personalization of customer experience while promoting 
ethical web experience management and increased user privacy controls. 

## Business cases based on Unomi
* Build a web personalization software to adapt the content on your website to your audience
* Collect data from mobile application and feed Apache Unomi to track and understand your customers’ journeys 
* Connect the beacons deployed in your stores to Apache Unomi and consolidate the profile of your customers and leads across all these channels
* Automatically push profiles from Apache Unomi to your CRM when a lead reach a given number of points in one of your scoring plans

## Unomi logical architecture

All communication is done using HTTP REST requests and JSON data formats.

![Unomi logical architecture](images/unomi-logical-architecture-diagram.png)

## 5 Minute Quick start
1. Install JDK 8 (see [http://www.oracle.com/technetwork/java/javase/downloads/index.html](http://www.oracle.com/technetwork/java/javase/downloads/index.html) and make sure you set the JAVA_HOME variable [https://docs.oracle.com/cd/E19182-01/820-7851/inst_cli_jdk_javahome_t/](https://docs.oracle.com/cd/E19182-01/820-7851/inst_cli_jdk_javahome_t/)
2. Download Apache Unomi here : [http://unomi.incubator.apache.org/download.html](http://unomi.incubator.apache.org/download.html)
3. Start it using : `./bin/karaf`
4. Wait for startup to complete
5. Try accessing [https://localhost:9443/cxs/cluster](https://localhost:9443/cxs/cluster) with username/password: karaf/karaf . You might get a certificate warning in your browser, just accept it despite the warning it is safe.
6. Request your first context by simply accessing : [http://localhost:8181/context.js?session=1234](http://localhost:8181/context.js?session=1234)

### News

- 2016-10-04 Released version 1.1.0-incubating
- 2016-05-22 Released version 1.0.0-incubating
- 2015-11-23 Initial code base import in [Git repository](source-repository.html) 
- 2015-11-20 Added [Apache Maturity Model report page](maturity-model-report.html)
- 2015-11-13 Initial web site created
- 2015-10-20 JIRA, mailing lists, git, website space created.
- 2015-10-05 Project enters incubation.

### Articles & slides

* [Linux.com : Unomi: A Bridge Between Privacy and Digital Marketing](http://www.linux.com/news/enterprise/cloud-computing/858418-unomi-a-bridge-between-privacy-and-digital-marketing)
* [Introducing Apache Unomi, JavaOne 2015](http://www.slideshare.net/sergehuber/introducing-apache-unomi-javaone-2015-session) This presentation has a cool example of integrating Apache Unomi with IoT devices (Internet of Things) such as beacons, smartphones and even televisions
* [Apache Unomi In-depth, ApacheCon EU 2015](http://www.slideshare.net/sergehuber/apache-unomi-in-depth-apachecon-eu-2015-session)

---

### Disclaimer

Apache Unomi is an effort undergoing incubation at The Apache Software Foundation (ASF), sponsored by the Apache Incubator PMC. Incubation is required 
of all newly accepted projects until a further review indicates that the infrastructure, communications, and decision making process have stabilized 
in a manner consistent with other successful ASF projects. While incubation status is not necessarily a reflection of the completeness or stability 
of the code, it does indicate that the project has yet to be fully endorsed by the ASF.

![Unomi is incubating](images/incubator-logo.png)

![Apache Software Foundation](https://www.apache.org/foundation/press/kit/asf_logo_url_small.png)

### Trademarks

Apache Karaf, Apache Mahout, Apache and the Apache feather logo are either registered trademarks or trademarks of The
 Apache Software Foundation in the United States and other countries.
