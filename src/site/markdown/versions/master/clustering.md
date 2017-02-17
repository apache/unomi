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

Cluster setup
=============

Apache Karaf relies on Apache Karaf Cellar, which in turn uses Hazelcast to discover and configure its cluster. 
You just need to install multiple context servers on the same network, and then (optionally) change the Hazelcast 
 configuration in the following file :

    etc/hazelcast.xml

All nodes on the same network, sharing the same cluster name will be part of the same cluster.

For the actual ElasticSearch configuration however, this must be done using the following file:

    etc/org.apache.unomi.persistence.elasticsearch.cfg
    
Depending on the cluster size, you will want to adjust the following parameters to make sure your setup is optimal in 
terms of performance and safety.

#### 2 nodes configuration
One node dedicated to context server, 1 node for elasticsearch storage.

Node A :

    numberOfReplicas=0
    monthlyIndex.numberOfReplicas=0

Node B :

    numberOfReplicas=0
    monthlyIndex.numberOfReplicas=0

#### 3 nodes configuration
One node dedicated to context server, 2 nodes for elasticsearch storage with fault-tolerance

Node A :

    numberOfReplicas=1
    monthlyIndex.numberOfReplicas=1

Node B :

    numberOfReplicas=1
    monthlyIndex.numberOfReplicas=1

Node C :

    numberOfReplicas=1
    monthlyIndex.numberOfReplicas=1

