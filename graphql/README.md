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

Apache Unomi GraphQL API
========================

Install
-------

Installing GraphQL feature:

    feature:repo-add mvn:org.apache.unomi/cdp-graphql-feature/1.5.0-SNAPSHOT/xml/features
    feature:install cdp-graphql-feature

GraphQL Endpoint
----------------

You can then access the GraphQL endpoint at the following URL:

    http://localhost:8181/sdlgraphql
    
Query example
-------------

operation::

    query findEvents($filter: CDP_EventFilterInput) {
      cdp {
        findEvents(filter: $filter) {
          pageInfo {
            hasNextPage
            hasPreviousPage
          }
          edges {
            cursor
            node {
              id
              cdp_profileID {
                client {
                  id
                  title
                }
                id
                uri
              }
              __typename
            }
          }
        }
      }
    }

variables::

    {
      "filter": {
        "cdp_profileID_equals": ""
      }
    }
    
Segment query operation:

    query findSegments($segmentFilter: CDP_SegmentFilterInput) {
      cdp {
        findSegments(filter: $segmentFilter) {
          edges {
            node {
              id
              name
              view {
                name
              }
              profiles {
                profileIDs
              }
            }
          }
        }
      }
    }

Manually validating against specification schema
---------------------------------------

Steps:
1. Build aggregation schema using graphql-FIND_NAME_OF_TOOL
2. Validate aggregated schema using https://github.com/kamilkisiela/graphql-inspector against running instance of Apache Unomi
