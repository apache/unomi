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
