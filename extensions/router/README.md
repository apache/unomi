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
This extension is implemented using Apache Camel routes and can use Apache Kafka to buffer import process and make it failsafe. 

## Getting started
1. Configure your Unomi Router:
    In the `etc/org.apache.unomi.router.cfg` file, first of all you need to decide if you want to use Apache Kafka to support huge amount of imported data
    or just use the default configuration (Without broker) you might want to update the following settings:
    
    Configuration type
    >`#Configuration Type values {'nobroker', 'kafka'}`
    
    >`router.config.type=nobroker`
    
    Change to 'kafka' and uncomment settings below to switch your configuration. 
    
    Kafka settings 
    >`#Kafka settings`
    
    >`kafka.host=localhost`
    
    >`kafka.port=9092`
    
    >`kafka.import.topic=import-deposit`
    
    >`kafka.import.groupId=unomi-import-group`
    
    >`kafka.export.topic=export-deposit`
        
    >`kafka.export.groupId=unomi-export-group`
    
    Kafka host and port with the topic name and the groupId to which the topic is assigned
    
    >`#Import One Shot upload directory`
    
    >`import.oneshot.uploadDir=${karaf.data}/tmp/unomi_oneshot_import_configs/`
   
    Path to the folder where unomi should stock file imported for a oneshot processing
    
2. Send your import configuration:

    An import configuration is nothing else than a simple JSON to describe how you want to import your data (Profiles).
    To create/update an import configuration
    
    `POST /cxs/importConfiguration`
    ```json
     {
         "itemId": "f57f1f86-97bf-4ba0-b4e4-7d5e77e7c0bd",
         "itemType": "importConfig",
         "scope": "integration",
         "name": "Recurrent import",
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
         "columnSeparator": ",",
         "lineSeparator": "\\n",
         "multiValueSeparator": "|",
         "multiValueDelimiter": "",
         "hasHeader": false,
         "hasDeleteColumn": false,
         "mergingProperty": "email",
         "overwriteExistingProfiles": true,
         "propertiesToOverwrite": ["firstName", "lastName"],
         "active": true
     }
    ```
    
    Omit the `itemId` when creating new entry, `configType` can be '**recurrent**' for file/ftp/network path polling or  '**oneshot**' for one time import (in case of oneshot configuration, omit the properties.source attribute).
    
    The `properties.source` attribute is an Apache Camel endpoint uri (See http://camel.apache.org/uris.html for more details). Unomi Router is designed to use **File** and **FTP** Camel components. 
            
           **path** the path to the file 
           **fileName** is the name of the file to consume, you can use a pattern by using include option instead of fileName (eg. include=.*.csv)
           **move** is the folder where you want to move you consumed files (By default they are moved to '.camel' folder)
           **consumer.delay** the polling frequency on the specified path, have different format (number of milliseconds, or '2s', '1h', etc.)
    
    The attribute `properties.mapping` is a Map of:
    * Key: Profile property id in Unomi
    * Value: Index of the column in the imported file to copy the in the previous property.
    
    The attribute `columnSeparator` is a string that defaults to "," the most common column separator for CSV files.
    
    The attribute `lineSeparator` is a string that defaults to "\n" the most common line separator for files.
    
    The attribute `multiValueSeparator` is a string that defaults to "|".
    
    **ATTENTION:** Be careful not to use the same separator as for column.
    
    The attribute `multiValueDelimiter` is a string that defaults to an empty string (No delimiter), some CSV producers tend to wrap multivalued column use this attribute to specify your producers' delimiter 
    (eg. for brackets you can fill the field with "[]", opening and closing are needed).
    
    The attribute `hasHeader` is a boolean that defaults to false (the imported file has no header).
    
    The attribute `hasDeleteColumn` is a boolean that defaults to false (the imported file' last column is a delete flag).
    
    The attribute `mergingProperty` is the profile property id in Unomi to use to check for duplicates.
    
    The attribute `overwriteExistingProfiles` is a flag to tell what you want to do if the profile already exist (**Merge -> true / Skip -> false**).
        
    The attribute `propertiesToOverwrite` is a list of profile properties ids to overwrite (ignored if 'overwriteExistingProfiles' is **false**), if **null** all properties
    will be overwritten.
    
    The attribute `active` is the flag to activate or deactivate the import configuration.
    
    Concerning oneshot import configuration using the previously described service will only create the import configuration, to send the file to process
        you need to call : 
        
    `POST /cxs/importConfiguration/oneshot`
    `Content-Type : multipart/form-data`

    First multipart with the name '**importConfigId**' is the importConfiguration to use to import the file, second one with the name '**file**' is the file to import.
            
  3. Send your export configuration:
            
    An export configuration is nothing else than a simple JSON to describe how you want to export your data (Profiles).
    To create/update an export configuration
    
    `POST /cxs/exportConfiguration`
    ```json
     {
         "itemId": "0e59d271-f5a4-497f-8646-0c8c66602278",
         "itemType": "exportConfig",
         "scope": "integration",
         "name": "Test Recurrent",
         "description": "Just test recurrent export",
         "configType": "recurrent",
         "properties": {
           "destination": "{file/ftp}://{path}?fileName=profiles-export-${date:now:yyyyMMddHHmm}.csv",
           "period": "1m",
           "segment": "contacts",
           "mapping": {
             "0": "firstName",
             "1": "lastName",
             ...
           }
         },
         "active": true
     }
    ```
   Omit the `itemId` when creating new entry, `configType` can be '**recurrent**' for file/ftp/network path polling or  '**oneshot**' for one time export (in case of oneshot configuration, omit the properties.destination 
   and properties.period attributes).
       
   The `properties.destination` attribute is an Apache Camel endpoint uri (See http://camel.apache.org/uris.html for more details). Unomi Router is designed to use **File** and **FTP**/**FTPS**/**SFTP** Camel components.
    
   File and ftp URI format:
    
   `file://directoryName[?options]`
    
   See http://camel.apache.org/file2.html for more details.
       
   `ftp://[username@]hostname[:port]/directoryname[?options]`
   `sftp://[username@]hostname[:port]/directoryname[?options]`
   `ftps://[username@]hostname[:port]/directoryname[?options]`
   
   See http://camel.apache.org/ftp.html for more details.
   
   `properties.period` is same as 'consumer.delay' option in the import source path
   
   `properties.segment`is the segment ID to use to collect profiles to export
   
   Concerning oneshot export configuration using the previously described service will create the export configuration, to return the generated file to download you need to call: 
   
   `POST /cxs/importConfiguration/oneshot`
   
   `Content-Type : application/json`

    
    