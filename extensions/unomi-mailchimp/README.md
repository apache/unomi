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


Apache Unomi :: Extensions :: MailChimp Beta Connector
=================================

This extension have 3 actions. 
    Add a visitor into a defined Mailchimp list.
    Remove a visitor from a defined Mailchimp list.
    Unsubscribe a visitor from a defined Mailchimp list.

## Configuration required 

    Marketing Factory version : 1.8.0
    Unomi version : 1.3.0
    Digital Experience : 7.2.1.1  
     
## Getting started


1.  Create a new MailChimp account :

        https://login.mailchimp.com/signup/
           
2.  Generate a new API Key, or get the default

        https://usX.admin.mailchimp.com/account/api/

3.  Configure the MailChimp Beta Connector. In the etc/org.apache.unomi.mailchimpconnector.cfg file change the following settings:
    
        mailChimpConnector.apiKey=YOUR_APIKEY
        mailChimpConnector.url.subDomain=YOUR_SUB_DOMAIN  (exemple: https://usX.admin.mailchimp.com/account/api/, the X is the SUB_DOMAIN)

4.  Deploy into Apache Unomi using the following commands from the Apache Karaf shell:

        feature:repo-add mvn:org.apache.unomi/unomi-mailchimp-connector-karaf-kar/${project.version}/xml/features
        feature:install unomi-mailchimp-connector-karaf-kar
