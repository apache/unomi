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

# Custom extensions

Apache Unomi is a pluggeable server that may be extended in many ways. This document assumes you are familiar with the 
[Apache Unomi concepts](concepts.html) . This document is mostly a reference document on the different things that may 
be used inside an extension. If you are looking for complete samples, please see the [samples page](samples.html).

## Creating an extension

An extension is simply a Maven project, with a Maven pom that looks like this:

    <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
        <parent>
            <groupId>org.apache.unomi</groupId>
            <artifactId>unomi-extensions</artifactId>
            <version>${project.version}</version>
        </parent>
    
        <modelVersion>4.0.0</modelVersion>
    
        <artifactId>unomi-extension-example</artifactId>
        <name>Apache Unomi :: Extensions :: Example</name>
        <description>Service implementation for the Apache Unomi Context Server extension that integrates with the Geonames database</description>
        <version>${project.version}</version>
        <packaging>bundle</packaging>
    
        <dependencies>
            <!-- This dependency is not required but generally used in extensions -->
            <dependency>
                <groupId>org.apache.unomi</groupId>
                <artifactId>unomi-api</artifactId>
                <version>${project.version}</version>
                <scope>provided</scope>
            </dependency>    
        </dependencies>
    
        <build>
            <plugins>
                <plugin>
                    <groupId>org.apache.felix</groupId>
                    <artifactId>maven-bundle-plugin</artifactId>
                    <extensions>true</extensions>
                    <configuration>
                        <instructions>
                            <Embed-Dependency>*;scope=compile|runtime</Embed-Dependency>
                            <Import-Package>
                                sun.misc;resolution:=optional,
                                *
                            </Import-Package>
                        </instructions>
                    </configuration>
                </plugin>    
            </plugins>
        </build>
    </project>

An extension may contain many different kinds of Apache Unomi objects, as well as custom OSGi services or anything that
is needed to build your application.

## Predefined segments

You may provide pre-defined segments by simply adding a JSON file in the src/main/resources/META-INF/cxs/segments directory of 
your Maven project. Here is an example of a pre-defined segment:

    {
      "metadata": {
        "id": "leads",
        "name": "Leads",
        "scope": "systemscope",
        "description": "You can customize the list below by editing the leads segment.",
        "readOnly":true
      },
      "condition": {
        "parameterValues": {
          "subConditions": [
            {
              "parameterValues": {
                "propertyName": "properties.leadAssignedTo",
                "comparisonOperator": "exists"
              },
              "type": "profilePropertyCondition"
            }
          ],
          "operator" : "and"
        },
        "type": "booleanCondition"
      }
    }

Basically this segment uses a condition to test if the profile has a property `leadAssignedTo` that exists. All profiles
that match this condition will be part of the pre-defined segment.

## Predefined rules

You may provide pre-defined rules by simply adding a JSON file in the src/main/resources/META-INF/cxs/rules directory of 
your Maven project. Here is an example of a pre-defined rule:

    {
        "metadata" : {
            "id": "evaluateProfileSegments",
            "name": "Evaluate segments",
            "description" : "Evaluate segments when a profile is modified",
            "readOnly":true
        },
    
        "condition" : {
            "type": "profileUpdatedEventCondition",
            "parameterValues": {
            }
        },
    
        "actions" : [
            {
                "type": "evaluateProfileSegmentsAction",
                "parameterValues": {
                }
            }
        ]
    
    }
    
In this example we provide a rule that will execute when a predefined composed condition of type 
"profileUpdatedEventCondition" is received. See below to see how predefined composed conditions are declared.
Once the condition is matched, the actions will be executed in sequence. In this example there is only a single 
action of type "evaluateProfileSegmentsAction" that is defined so it will be executed by Apache Unomi's rule engine.
You can also see below how custom actions may be defined.     

## Predefined properties

By default Apache Unomi comes with a set of pre-defined properties, but in many cases it is useful to add additional 
predefined property definitions. You can create property definitions for session or profile properties by creating them
in different directories.

For session properties you must create a JSON file in the following directory in your Maven project:

    src/main/resources/META-INF/cxs/properties/sessions
    
For profile properties you must create the JSON file inside the directory in your Maven project:

    src/main/resources/META-INF/cxs/properties/profiles
    
Here is an example of a property definition JSON file

    {
        "metadata": {
            "id": "city",
            "name": "City",
            "systemTags": ["properties", "profileProperties", "contactProfileProperties"]
        },
        "type": "string",
        "defaultValue": "",
        "automaticMappingsFrom": [ ],
        "rank": "304.0"
    }

## Predefined child conditions

You can define new predefined conditions that are actually conditions inheriting from a parent condition and setting
pre-defined parameter values. You can do this by creating a JSON file in: 

    src/main/resources/META-INF/cxs/conditions
    
Here is an example of a JSON file that defines a profileUpdateEventCondition that inherits from a parent condition of
type eventTypeCondition.     

    {
      "metadata": {
        "id": "profileUpdatedEventCondition",
        "name": "profileUpdatedEventCondition",
        "description": "",
        "systemTags": [
          "event",
          "eventCondition"
        ],
        "readOnly": true
      },
      "parentCondition": {
        "type": "eventTypeCondition",
        "parameterValues": {
          "eventTypeId": "profileUpdated"
        }
      },
    
      "parameters": [
      ]
    }

## Predefined personas

Personas may also be pre-defined by creating JSON files in the following directory:

    src/main/resources/META-INF/cxs/personas
    
Here is an example of a persona definition JSON file:    

    {
        "persona": {
            "itemId": "usVisitor",
            "properties": {
                "description": "Represents a visitor browsing from inside the continental US",
                "firstName": "U.S.",
                "lastName": "Visitor"
            },
            "segments": []
        },
        "sessions": [
            {
                "itemId": "aa3b04bd-8f4d-4a07-8e96-d33ffa04d3d9",
                "profileId": "usVisitor",
                "properties": {
                    "operatingSystemName": "OS X 10.9 Mavericks",
                    "sessionCountryName": "United States",
                    "location": {
                        "lat":37.422,
                        "lon":-122.084058
                    },
                    "userAgentVersion": "37.0.2062.120",
                    "sessionCountryCode": "US",
                    "deviceCategory": "Personal computer",
                    "operatingSystemFamily": "OS X",
                    "userAgentName": "Chrome",
                    "sessionCity": "Mountain View",
                    "remoteHost": "www.google.com",
                    "remoteAddr": "66.249.66.1"
                },
                "timeStamp": "2014-09-18T11:40:54Z",
                "lastEventDate": "2014-09-18T11:40:59Z",
                "duration": 4790
            }
        ]
    }

You can see that it's also possible to define sessions for personas.

## Custom actions

Custom actions are a powerful way to integrate with external systems by being able to define custom logic that will 
be executed by an Apache Unomi rule. An action is defined by a JSON file created in the following directory:

    src/main/resources/META-INF/cxs/actions
    
Here is an example of a JSON action definition:

    {
      "metadata": {
        "id": "addToListsAction",
        "name": "addToListsAction",
        "description": "",
        "systemTags": [
          "demographic",
          "availableToEndUser"
        ],
        "readOnly": true
      },
      "actionExecutor": "addToLists",
      "parameters": [
        {
          "id": "listIdentifiers",
          "type": "string",
          "multivalued": true
        }
      ]
    }    
    
The `actionExecutor` identifier refers to a service property that is defined in the OSGi Blueprint service registration.
Note that any OSGi service registration may be used, but in these examples we use OSGi Blueprint. The definition for the
above JSON file will be found in a file called `src/main/resources/OSGI-INF/blueprint/blueprint.xml` with the following
content:

    <?xml version="1.0" encoding="UTF-8"?>
    <blueprint xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns="http://www.osgi.org/xmlns/blueprint/v1.0.0"
               xsi:schemaLocation="http://www.osgi.org/xmlns/blueprint/v1.0.0 http://www.osgi.org/xmlns/blueprint/v1.0.0/blueprint.xsd">
    
        <reference id="profileService" interface="org.apache.unomi.api.services.ProfileService"/>
        <reference id="eventService" interface="org.apache.unomi.api.services.EventService"/>
    
        <!-- Action executors -->
    
        <service auto-export="interfaces">
            <service-properties>
                <entry key="actionExecutorId" value="addToLists"/>
            </service-properties>
            <bean class="org.apache.unomi.lists.actions.AddToListsAction">
                <property name="profileService" ref="profileService"/>
                <property name="eventService" ref="eventService"/>
            </bean>
        </service>
    
    </blueprint>
     
You can note here the `actionExecutorId` that corresponds to the `actionExecutor` in the JSON file.

The implementation of the action is available here : [org.apache.unomi.lists.actions.AddToListsAction](https://github.com/apache/incubator-unomi/blob/master/extensions/lists-extension/actions/src/main/java/org/apache/unomi/lists/actions/AddToListsAction.java) 

## Custom conditions

Custom conditions are different from predefined child conditions because they implement their logic using Java classes.
They are also declared by adding a JSON file into the conditions directory:

    src/main/resources/META-INF/cxs/conditions
    
Here is an example of JSON custom condition definition:

    {
      "metadata": {
        "id": "matchAllCondition",
        "name": "matchAllCondition",
        "description": "",
        "systemTags": [
          "logical",
          "profileCondition",
          "eventCondition",
          "sessionCondition",
          "sourceEventCondition"
        ],
        "readOnly": true
      },
      "conditionEvaluator": "matchAllConditionEvaluator",
      "queryBuilder": "matchAllConditionESQueryBuilder",
    
      "parameters": [
      ]
    }
    
Note the `conditionEvaluator` and the `queryBuilder` values. These reference OSGi service properties that are declared
in an OSGi Blueprint configuration file (other service definitions may also be used such as Declarative Services or even
Java registered services). Here is an example of an OSGi Blueprint definition corresponding to the above JSON condition
definition file.

    src/main/resources/OSGI-INF/blueprint/blueprint.xml
    
    <blueprint xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns="http://www.osgi.org/xmlns/blueprint/v1.0.0"
               xsi:schemaLocation="http://www.osgi.org/xmlns/blueprint/v1.0.0 http://www.osgi.org/xmlns/blueprint/v1.0.0/blueprint.xsd">
        
        <service
                interface="org.apache.unomi.persistence.elasticsearch.conditions.ConditionESQueryBuilder">
            <service-properties>
                <entry key="queryBuilderId" value="matchAllConditionESQueryBuilder"/>
            </service-properties>
            <bean class="org.apache.unomi.plugins.baseplugin.conditions.MatchAllConditionESQueryBuilder"/>
        </service>
            
        <service interface="org.apache.unomi.persistence.elasticsearch.conditions.ConditionEvaluator">
            <service-properties>
                <entry key="conditionEvaluatorId" value="matchAllConditionEvaluator"/>
            </service-properties>
            <bean class="org.apache.unomi.plugins.baseplugin.conditions.MatchAllConditionEvaluator"/>
        </service>
      
    </blueprint>
    
You can find the implementation of the two classes here : 

- [org.apache.unomi.plugins.baseplugin.conditions.MatchAllConditionESQueryBuilder](https://github.com/apache/incubator-unomi/blob/master/plugins/baseplugin/src/main/java/org/apache/unomi/plugins/baseplugin/conditions/MatchAllConditionESQueryBuilder.java)
- [org.apache.unomi.plugins.baseplugin.conditions.MatchAllConditionEvaluator](https://github.com/apache/incubator-unomi/blob/master/plugins/baseplugin/src/main/java/org/apache/unomi/plugins/baseplugin/conditions/MatchAllConditionEvaluator.java)
    
