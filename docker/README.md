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
  
# Building Docker image

You must build the docker image provided in this directory by using the following command:

```
mvn clean install
```

This project will also generate Docker standalone project files in `target/filtered-docker` as well as position the
required Unomi tarball.

# Using Unomi Docker Image

## Launching docker-compose using Maven project

Unomi requires ElasticSearch so it is recommended to run Unomi and ElasticSearch using docker-compose:

```
mvn docker:start
```

You will need to wait while Docker builds the containers and they boot up (ES will take a minute or two). Once they are 
up you can check that the Unomi services are available by visiting http://localhost:8181 in a web browser.

## Manually launching ElasticSearch & Unomi docker images

If you want to run it without docker-compose you should then make sure you setup the following environments properly.

For ElasticSearch:

    docker pull docker.elastic.co/elasticsearch/elasticsearch:7.4.2
    docker network create unomi
    docker run --name elasticsearch --net unomi -p 9200:9200 -p 9300:9300 -e "discovery.type=single-node" -e cluster.name=contextElasticSearch docker.elastic.co/elasticsearch/elasticsearch:7.4.2
    
For Unomi:

    docker pull apache/unomi:1.5.0-SNAPSHOT
    docker run --name unomi --net unomi -p 8181:8181 -p 9443:9443 -p 8102:8102 -e UNOMI_ELASTICSEARCH_ADDRESSES=elasticsearch:9200 apache/unomi:1.5.0-SNAPSHOT

## Using a host OS ElasticSearch installation (only supported on macOS & Windows)

    docker run --name unomi -p 8181:8181 -p 9443:9443 -p 8102:8102 -e UNOMI_ELASTICSEARCH_ADDRESSES=host.docker.internal:9200 apache/unomi:1.5.0-SNAPSHOT

Note: Linux doesn't support the host.docker.internal DNS lookup method yet, it should be available in an upcoming version of Docker. See https://github.com/docker/for-linux/issues/264

# Using docker build tools

If you want to rebuild the images or use docker compose directly, you must still first use `mvn clean install` to generate
the filtered project in `target/filtered-docker`.

You can then use `docker-compose up` to start the project
