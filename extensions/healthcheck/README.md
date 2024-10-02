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

The Health Check extension provides a simple health check endpoint that can be used to determine if the server is up and running. The health check endpoint is available at `/healthcheck` and returns a simple JSON response.  
The healthcheck is available even if unomi is not started. It gives health information about :   
  - Karaf
  - Elasticsearch (cluster health)
  - Unomi (bundles started or not)
  - Persistence
  - Cluster health (if unomi is in cluster mode)

All healthcheck can have a status :
  - DOWN (service is not available)
  - UP (service is up but does not respond to request (starting or misconfigured))
  - LIVE (service is ready to serve request)
  - ERROR (an error occurred during service health check)

All healthcheck have a timeout of 500ms where check is cancelled and will be returned as error.

Typical response to http://localhost:8181/health when unomi NOT started is : 

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
