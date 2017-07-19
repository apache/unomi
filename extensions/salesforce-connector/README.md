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

Unomi Salesforce Connector
==========================

## Getting started

1. Create a new developer account here: 

        https://developer.salesforce.com/signup
        
2. Create a new Connected App, by going into Setup -> App Manager and click "Create Connected App"
 
3. In the settings, make sure you do the following:

        Enable OAuth settings -> Activated
        Enable for device flow -> Activated (no need for a callback URL)
        Add all the selected OAuth scopes you want (or put all of them)
        Make sure Require Secret for Web Server flow is activated
        
4. Make sure you retrieve the following information once you have created the app in the API (Enable OAuth Settings):

        Consumer key
        Consumer secret (click to see it)
        
5. You must also retrieve your user's security token, or create it if you don't have one already. To do this simply 
click on your user at the top right, select "Settings", the click on "Reset my security token". You will receive an email
with the security token.

6. You are now ready to configure the Apache Unomi Salesforce Connector. In the etc/org.apache.unomi.sfdc.cfg file 
change the following settings:

        sfdc.user.username=YOUR_USER_NAME
        sfdc.user.password=YOUR_PASSWORD
        sfdc.user.securityToken=YOUR_USER_SECURITY_TOKEN
        sfdc.consumer.key=CONNECTED_APP_CONSUMER_KEY
        sfdc.consumer.secret=CONNECTED_APP_SECRET
        
7. Connected to the Apache Unomi Karaf Shell using : 

        ssh -p 8102 karaf@localhost (default password is karaf)
           
7. Deploy into Apache Unomi using the following commands from the Apache Karaf shell:

        feature:repo-add mvn:org.apache.unomi/unomi-salesforce-connector-karaf-kar/1.2.0-incubating-SNAPSHOT/xml/features
        feature:install unomi-salesforce-connector-karaf-kar
        
8. You can then test the connection to Salesforce by accessing the following URLs:

        https://localhost:9443/cxs/sfdc/version
        https://localhost:9443/cxs/sfdc/limits
        
    The first URL will give you information about the version of the connector, so this makes it easy to check that the
    plugin is properly deployed, started and the correct version. The second URL will actually make a request to the
    Salesforce REST API to retrieve the limits of the Salesforce API.
    
    Both URLs are password protected by the Apache Unomi (Karaf) password. You can find this user and password information
    in the etc/users.properties file.
    
## Upgrading the Salesforce connector

If you followed all the steps in the Getting Started section, you can upgrade the Salesforce connector by using the following steps:

1. Compile the connector using:

        mvn clean install
        
2. Login to the Unomi Karaf Shell using:

        ssh -p 8102 karaf@localhost (password by default is karaf)
        
3. Execute the following commands in the Karaf shell

        feature:repo-refresh
        feature:uninstall unomi-salesforce-connector-karaf-feature
        feature:install unomi-salesforce-connector-karaf-feature
        
4. You can then check that the new version is properly deployed by accessing the following URL and checking the build date:

        https://localhost:9443/cxs/sfdc/version
        
    (if asked for a password it's the same karaf/karaf default)
   
## Using the Salesforce Workbench for testing REST API
   
The Salesforce Workbench contains a REST API Explorer that is very useful to test requests. You may find it here : 

    https://workbench.developerforce.com/restExplorer.php
    
## Setting up Streaming Push queries

Using the Salesforce Workbench, you can setting streaming push queries (Queries->Streaming push topics) such as the 
following example:

    Name: LeadUpdates
    Query : SELECT Id,FirstName,LastName,Email,Company FROM Lead

## Executing the unit tests

Before running the tests, make sure you have completed all the steps above, including the streaming push queries setup.

By default the unit tests will not run as they need proper Salesforce credentials to run. To set this up create a 
properties file like the following one:

test.properties

    #
    # Licensed to the Apache Software Foundation (ASF) under one or more
    # contributor license agreements.  See the NOTICE file distributed with
    # this work for additional information regarding copyright ownership.
    # The ASF licenses this file to You under the Apache License, Version 2.0
    # (the "License"); you may not use this file except in compliance with
    # the License.  You may obtain a copy of the License at
    #
    #      http://www.apache.org/licenses/LICENSE-2.0
    #
    # Unless required by applicable law or agreed to in writing, software
    # distributed under the License is distributed on an "AS IS" BASIS,
    # WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    # See the License for the specific language governing permissions and
    # limitations under the License.
    #
    sfdc.user.username=YOUR_USER_NAME
    sfdc.user.password=YOUR_PASSWORD
    sfdc.user.securityToken=YOUR_USER_SECURITY_TOKEN
    sfdc.consumer.key=CONNECTED_APP_CONSUMER_KEY
    sfdc.consumer.secret=CONNECTED_APP_SECRET
        
and then use the following command line to reference the file:

    mvn clean install -DsfdcProperties=../test.properties
    
(in case you're wondering the ../ is because the test is located in the services sub-directory)