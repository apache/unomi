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

## A front end for personalization big data technologies (with built-in privacy management)

### In a few words

Apache Unomi is a REST server that manages user profiles and events related to the profiles. It can be used to
integrate personalization and profile management within very different systems such as CMS, CRMs, Issue Trackers,
native mobile application. It was designed to be easy to integrate with external systems, promoting profile sharing
and re-use in very different applications.
 
Basically the server tracks users using different mechanisms (by default cookies), builds a progressively populated
profile and associates events that are performed by a user with his profile. Events may range from a click on a page,
to a file being downloaded, a native mobile application button being clicked, or anything that can be sent to the
server.

The server has a built-in rule system that makes it possible to perform any action when an event is collected for
a profile. It also has the notion of user segments, making it possible to classify user profiles into dynamic
 sub-groups, notably to build personalized experiences for specific segments.
 
As Apache Unomi is built as an OSGi application running inside Apache Karaf, it is extremely extensible and built to
be scalable. For example it is possible to plugin new conditions, actions, or any other service that may be needed 
such as beacon tracking or push notifications.

Apache Unomi is also the reference implementation of the upcoming OASIS Context Server (CXS) standard 
(https://www.oasis-open.org/committees/cxs/) to help standardize personalization of online experience
while promoting ethical web experience management and increased user privacy controls.

### What is unique about Apache Unomi ?

One of the most unique features of this server is its privacy management features. Using the privacy REST API, it is
possible for integrators to build user facing UIs that let them manage their profile, and control how they are being
tracked, what data has been collected and even anonymize previously collected data or future data ! Finally there is 
even the possibility for end-users to delete their profile information completely. 

It is becoming more and more important to address privacy issues correctly, and it is even becoming more and more of 
a legal issue since a lot of legislation is now appearing in many countries to make sure that user's right to privacy is 
respected.

Of course these possibilities have no default UI inside of Apache Unomi so it is left up to the developers to expose
them (or not).

### Use cases

#### Use Apache Unomi as a personalization service for a Web CMS

In this use case Apache Unomi is used to track all the users that visits the sites being managed by the CMS. The 
sites may also contain personalized content elements that will use the profile information coming from Apache Unomi
to change their display based on the user. It will also send events (such as login events for example) back to the
server using simple AJAX calls to the Apache Unomi REST API. 

The Web CMS can also build UIs to expose the privacy management feature to end-users of the platform, and will of 
course build UIs to perform administration tasks such as profile, segments, goals, rules management. 

#### Use Apache Unomi as an analytics service for a native mobile application

In this case the server is used as a back-end for a native mobile application that will authenticate a user and then
send events to the server when the user performs certains tasks within the app. Tasks may include pressing a button,
getting close to a location (using GPS or beacons), etc...

The application may also include a UI to expose the privacy management features of Apache Unomi.

#### Use Apache Unomi as a centralized profile management system

In this use case the server is used a centralized profile management system, making it easy to aggregate different
profile information that may be stored in different systems such as CRMs, Issue tracking systems, forums, CMS, ...
One way of achieving this is to make sure that "anonymous" profiles are merged when an event such as a login happens
and a unique cross-system identifier (usually the email address) is detected on each system.

In this case connectors to all the different systems will need to be developped (and hopefully contributed back to
the Apache Unomi community), so that the centralization of the information is managed by an Open Source and standards
compliant server community.

### News

- 2015-11-23 Initial code base import in [Git repository](source-repository.html) 
- 2015-11-20 Added [Apache Maturity Model report page](maturity-model-report.html)
- 2015-11-13 Initial web site created
- 2015-10-20 JIRA, mailing lists, git, website space created.
- 2015-10-05 Project enters incubation.

### Articles & slides

* [Linux.com : Unomi: A Bridge Between Privacy and Digital Marketing](http://www.linux.com/news/enterprise/cloud-computing/858418-unomi-a-bridge-between-privacy-and-digital-marketing)
* [Introducing Apache Unomi, JavaOne 2015](http://www.slideshare.net/sergehuber/introducing-apache-unomi-javaone-2015-session) This presentation has a cool example of integrating Apache Unomi with IoT devices (Internet of Things) such as beacons, smartphones and even televisions
* [Apache Unomi In-depth, ApacheCon EU 2015](http://www.slideshare.net/sergehuber/apache-unomi-in-depth-apachecon-eu-2015-session)

### Features

![Unomi features](images/unomi-features.png)

* Simple entry-point to retrieve the profile context and collecting user-triggered events (page view, click, downloads, etc...)
* Full & simple REST API for Context Server administration
* Highly scalable architecture
* Fully OSGi compliant application
* Persistence & query layer uses ElasticSearch (other providers may be implemented in the future)
* Uses Apache Karaf as the OSGi runtime (supports both Apache Felix and Eclipse Equinox OSGi implementations)
* Very simple to deploy and install (simply unzip & run)
* Extensible through plugin architecture (using OSGi & simple JSON descriptors)

### At a glance

![Unomi input output](images/unomi-input-output.png)

Unomi provides the context of the current user interacting with any Unomi-aware system. Using this context, Unomi-connected systems can
send events to the context server. These events might, in turn, trigger rules that perform actions that can update the current context,
interact with external systems or pretty much anything that can be implemented using the Unomi API.

### Requirements

* JDK 7 or later, http://www.oracle.com/technetwork/java/javase/downloads/index.html
* Maven 3.0+, http://maven.apache.org
        
### Todo

- Look at possible integration with newsletter management systems such as MailChimp, for example to synchronize profile data with collected info.
- Integrate with machine learning implementations such as Prediction.io or Apache Mahout

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
 

