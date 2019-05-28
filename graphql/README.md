Apache Unomi GraphQL API
========================

Install
-------

Installing GraphQL feature:

    feature:repo-add mvn:org.apache.unomi/cdp-graphql-feature/1.4.0-SNAPSHOT/xml/features
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
