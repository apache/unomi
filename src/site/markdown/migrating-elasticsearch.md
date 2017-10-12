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
  
# Migrating ElasticSearch

## Introduction

In this section of the documentation we will explain some general notions of how to migrate between ElasticSearch 
versions, as well as present specific migration steps to migrate from one version of ElasticSearch used by Apache Unomi
to another, if it is needed.

## General steps and notions

Depending on the type of ElasticSearch install you may have, the migration steps will differ. Basically when dealing with
a single node (standalone) installation, a simple procedure may be used that simply copies some directories over, while
in the case of a cluster installation ElasticSearch Snapshot and Restore functionality must be used.

### Standalone (one node migration)

In the case of a standalone install, it is generally sufficient, provided the versions are compatible (meaning that only
one major version seperates the two installs), to simply copy over the `data` directory over to the new version. Also
you will need to make sure that you copy over any custom settings from the `config/elasticsearch.yml` file over to the 
new version.

### Cluster migration

Here we recommend you read the [official upgrading documentation](https://www.elastic.co/guide/en/elasticsearch/reference/current/setup-upgrade.html).

## Migrating between versions used by Apache Unomi.

### ElasticSearch 5.1.2 to 5.6.3 (Unomi 1.2.0 -> 1.3.0)

Steps:

1. Depending on your install, perform either the standalone or cluster migration
2. That's it !