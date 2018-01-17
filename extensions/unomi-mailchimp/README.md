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


Apache Unomi :: Extensions :: MailChimp Connector
=================================

This extension has 3 actions. 
    Add a visitor into a defined Mailchimp list.
    Remove a visitor from a defined Mailchimp list.
    Unsubscribe a visitor from a defined Mailchimp list.
     
## Getting started

1.  Create a new MailChimp account:

        https://login.mailchimp.com/signup/
           
2.  Generate a new API Key, or get the default

        https://usX.admin.mailchimp.com/account/api/

3. Configure the MailChimp Connector Basic
    In the etc/org.apache.unomi.mailchimpconnector.cfg file change the following settings:
    ```
        mailChimpConnector.apiKey=YOUR_APIKEY
        mailChimpConnector.url.subDomain=YOUR_SUB_DOMAIN  (exemple: https://usX.admin.mailchimp.com/account/api/, the X is the SUB_DOMAIN)
    ```
           
            
4. Before starting configure the mapping between Apache Unomi profile properties and MailChimp member properties.
    The mapping can't be use with multitued properties. You need to setup your MailChimp properties first in the MailChimp administration.
       
    ```
        Go to: lists/
        Select the triggered list
        Settings 
    ```
    
    Then in the cfg file
    ```
        mailChimpConnector.list.merge-fields.activate={Boolean} if you like to activate the mapping feature.
    ```
    This is the property to configure for the mapping, the format is as shown.
    ```
        mailChimpConnector.list.merge-fields.mapping={Apache Unomi property ID}<=>{MailChimp Tag name} 
    ```
    NOTE: there is a particular format for the address
    ```
        {Apache Unomi property ID}<=>{MailChimp Tag name}<=>{MailChimp tag sub entry}
    ```
    
    MailChimp type supported are:
    1. Date 
     ```
        The format is (DD/MM/YYYY) or  (MM/DD/YYYY)
    ``` 
    2. Birthday 
     ```    
        The format is (DD/MM) or  (MM/DD)
     ```
    3. Website or Text
     ```
        They are text
     ```
    4. Number 
     ```
        The number will be parse into a Integer 
     ```
    6. Phone
     ```
        The North American format is not supported, use international
     ```
    7. Address  
    Example:
    ```
        address<=>ADDRESS<=>addr1, 
        city<=>ADDRESS<=>city,
        zipCode<=>ADDRESS<=>zip,
        countryName<=>ADDRESS<=>country
    ```
    
5. Deploy into Apache Unomi using the following commands from the Apache Karaf shell:

        feature:repo-add mvn:org.apache.unomi/unomi-mailchimp-connector-karaf-kar/${project.version}/xml/features
        feature:install unomi-mailchimp-connector-karaf-kar
