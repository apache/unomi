/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
  */

# Health Check Extension

The Health Check extension provides a simple health check endpoint that can be used to determine if the server is up and running.  
The health check endpoint is available at 

```
/health/check
``` 
and returns a simple JSON response that includes all health check provider responses.  

Basic Http Authentication is enabled by default for the health check endpoint. The user needs to have the role `health` to access the endpoint. Users and roles can be configured in the etc/users.properties file. By default a user health/health is configured. 

The healthcheck is available even if unomi is not started. It gives health information about :   
  - Karaf (as soon as the karaf container is started)
  - ElasticSearch or OpenSearch (connection to ElasticSearch or OpenSearch cluster and its health)
  - Unomi (unomi bundles status)
  - Persistence (unomi to ElasticSearch/OpenSearch binding)
  - Cluster health (unomi cluster status and nodes information)

All healthcheck can have a status :
  - DOWN (service is not available)
  - UP (service is up but does not respond to request (starting or misconfigured))
  - LIVE (service is ready to serve request)
  - ERROR (an error occurred during service health check)

Any subsystem health check have a timeout of 500ms where check is cancelled and will be returned as error.

Typical response to /health/check when unomi NOT started is : 

```json 
[
  {
    "name":"karaf",
    "status":"LIVE",
    "collectingTime":0
  },
  {
    "name":"cluster",
    "status":"DOWN",
    "collectingTime":0
  },
  {
    "name":"elasticsearch",
    "status":"LIVE",
    "collectingTime":6
  },
  {
    "name":"persistence",
    "status":"DOWN",
    "collectingTime":0
  },
  {
    "name":"unomi",
    "status":"DOWN",
    "collectingTime":0
  }
]
```

## Configuration

Configuration is located in the file etc/org.apache.unomi.healthcheck.cfg

Extension can be disabled by setting the property `enabled` to `false`. An environment variable can be used to set this property : UNOMI_HEALTHCHECK_ENABLED

By default, all healthcheck providers are included but the list of those included providers can be customized by setting the property `providers` with a comma separated list of provider names. An environment variable can be used to set this property : UNOMI_HEALTHCHECK_PROVIDERS

The timeout used for each health check can be set by setting the property `timeout` to the desired value in milliseconds. An environment variable can be used to set this property : UNOMI_HEALTHCHECK_TIMEOUT 
