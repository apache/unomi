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

Unomi Router
==========================

## Getting started
Unomi Router Extension a Karaf Feature that provide an Enterprise Application Integration tool.
It is optional so you must configure it and install it in Karaf, and can be used for Machine - Machine or Human - Machine integration with Unomi.
Mainly Unomi Router Extension aim to make it easy to import third party applications/platforms profiles into Unomi.
This extension is implemented using Apache Camel routes and is using Apache Kafka to buffer import process and make it failsafe. 

## Getting started
1. Configure your Unomi Router:
    In the `etc/org.apache.unomi.router.cfg` file, you might want to update the following settings:
    Kafka settings 
    >`#Kafka settings`
    
    >`kafka.host=localhost`
    
    >`kafka.port=9092`
    
    >`kafka.import.topic=camel-deposit`
    
    >`kafka.import.groupId=unomi-import-group`
    
    Kafka host and port with the topic name and the groupId ti which the topic is assigned
    
    >`#Import One Shot upload directory`
    
    >`import.oneshot.uploadDir=/tmp/unomi_oneshot_import_configs/`
   
    Path to the folder where unomi should stock file imported for a oneshot processing
    

2. Deploy into Apache Unomi using the following commands from the Apache Karaf shell:
    ```sh
    $ feature:repo-add mvn:org.apache.unomi/unomi-router-karaf-feature/${version}/xml/features
    $ feature:install unomi-router-karaf-feature
    ```
    
3. Send your import configuration:

    An import configuration is nothing else than a simple JSON to describe how you want to import your data (Profiles).
    To create/update an import configuration
    
    `POST /cxs/importConfiguration`
    ```json
     {
         "itemId": "f57f1f86-97bf-4ba0-b4e4-7d5e77e7c0bd",
         "itemType": "importConfig",
         "scope": "integration",
         "name": "Test Recurrent",
         "description": "Just test recurrent import",
         "configType": "recurrent",
         "properties": {
           "source": "{file/ftp}://{path}?fileName={file-name}.csv&move=.done&consumer.delay=20000",
           "mapping": {
             "firstName": 0,
             "lastName": 1,
             ...
           }
         },
         "mergingProperty": "email",
         "overwriteExistingProfiles": true,
         "propertiesToOverwrite": ["firstName", "lastName"],
         "active": true
     }
    ```
    
    Omit the `itemId` when creating new entry, `configType` can be '**recurrent**' for file/ftp/network path polling or  '**oneshot**' for one time import.
    
    The `properties.source` attribute is an Apache Camel endpoint uri (See http://camel.apache.org/uris.html for more details). Unomi Router is designed to use **File** and **FTP** Camel components. 
    
    The attribute `properties.mapping` is a Map of:
    * Key: Profile property id in Unomi
    * Value: Index of the column in the imported file to copy the in the previous property.
        
    The attribute `mergingProperty` is the profile property id in Unomi to use to check for duplication.
    
    The attribute `propertiesToOverwrite` is a list of profile properties ids to overwrite, if **null** all properties
    will be overwritten.
    
    The attribute `active` is the flag to activate or deactivate the import configuration.
    
    Concerning oneshot import configuration using the previously described service will only create the import configuration, to send the file to process
    you need to call : 
    
    `POST /cxs/importConfiguration/oneshot`
    
    `Content-Type : multipart/form-data`
    
    First multipart with the name '**importConfigId**' is the importConfiguration to use to import the file, second one with the name '**file**' is the file to import.
    
    
   

    
    