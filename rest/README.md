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

# Apache Unomi REST API

## Consumer documentation

The REST API is served under `/cxs` on the default HTTP port (8181).

Unomi 3.1 authentication:

| Access | Credentials |
|--------|-------------|
| Public endpoints (`/cxs/context.json`, `/cxs/eventcollector`, …) | `X-Unomi-Api-Key: <tenant-public-key>` |
| Tenant private endpoints | Basic auth `tenantId:privateApiKey` |
| System administration (tenants, cluster, tasks, …) | JAAS user (for example `karaf:karaf`) |

Key manual chapters:

* `manual/src/main/asciidoc/multitenancy.adoc` — tenants and API keys
* `manual/src/main/asciidoc/request-examples.adoc` — curl examples with auth
* `manual/src/main/asciidoc/useful-unomi-urls.adoc` — endpoint reference
* `manual/src/main/asciidoc/scheduler.adoc` — `/cxs/tasks` API

Postman collections: see `rest/postman-readme.md`.

## Generating Miredot API documentation (maintainers)

- Switch to the `rest-documentation` branch (adds `@Path` annotations and the Maven plugin for doc generation).
- Rebase on master: `git rebase master`.
- Run `mvn test`.
- Open `target/miredot/index.html`.
