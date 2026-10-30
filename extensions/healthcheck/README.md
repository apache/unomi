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

Basic Http Authentication is enabled by default for the health check endpoint. The user needs to have the role `health` to access the endpoint. Users and roles can be configured in the etc/users.properties file. The shipped `health` user has no default password: its password comes from the `UNOMI_HEALTHCHECK_PASSWORD` environment variable, which must be set before starting. An empty password is never accepted.

The healthcheck is available even if unomi is not started. It gives health information about:
  - Karaf (as soon as the karaf container is started)
  - ElasticSearch or OpenSearch (connection to ElasticSearch or OpenSearch cluster and its health)
  - Unomi (unomi bundles status)
  - Persistence (unomi to ElasticSearch/OpenSearch binding)
  - Cluster health (unomi cluster status and nodes information)

All healthcheck can have a status :
  - DOWN (service is not available)
  - UP (service is running or starting)
  - LIVE (service is ready to serve requests)
  - ERROR (an error occurred during service health check)

Any subsystem health check uses the configured timeout (default 400 ms); a check that does not answer in time is cancelled and returned as an error.

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

Configuration is located in the file `etc/org.apache.unomi.healthcheck.cfg` (installed by the Elasticsearch or OpenSearch healthcheck feature).

| Property | Environment variable | Default | Notes |
|---|---|---|---|
| `healthcheck.enabled` | `UNOMI_HEALTHCHECK_ENABLED` | `true` | Any value other than `true` unregisters the endpoint. Never disables authentication. |
| `healthcheck.providers` | `UNOMI_HEALTHCHECK_PROVIDERS` | distribution-specific (see cfg) | Comma-separated: `cluster`, `elasticsearch` or `opensearch`, `unomi`, `persistence`, or the `name()` of a custom provider. Names no installed bundle provides are skipped with a warning. `karaf` is always reported. |
| `healthcheck.timeout` | `UNOMI_HEALTHCHECK_TIMEOUT` | `400` | Milliseconds, allowed range 1–60000. |

These environment variables map through `custom.system.properties` to `org.apache.unomi.healthcheck.*`, which the cfg file resolves. An invalid timeout falls back to the default above with a warning.

Persistence backends that are not packaged with Unomi (PostgreSQL, for example) register their own `HealthCheckProvider` and ship their own healthcheck feature and cfg, like the Elasticsearch and OpenSearch ones. See "Health check for another persistence backend" in the configuration manual.
