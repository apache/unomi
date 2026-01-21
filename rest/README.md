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

# REST API Documentation

Apache Unomi provides REST API documentation through OpenAPI/Swagger UI.

## Accessing the API Documentation

When the Unomi server is running, you can access the REST API documentation in the following ways:

### Swagger UI (Interactive Documentation)

The Swagger UI provides an interactive interface to explore and test the REST API:

- **URL**: `http://localhost:8181/cxs/api-docs?url=openapi.json`
- **Alternative**: Access via the main Unomi web interface at `/cxs/api-docs?url=openapi.json`

The Swagger UI allows you to:
- Browse all available REST API endpoints
- View request/response schemas
- Test API calls directly from the browser
- Download the OpenAPI specification

### OpenAPI Specification

The OpenAPI 3.0 specification is available as JSON:

- **URL**: `http://localhost:8181/openapi.json`

This specification can be used with:
- API client generators
- Documentation tools
- API testing tools

## Postman Collection

A comprehensive Postman collection with example requests is available in this directory:

- **Collection file**: `unomi-postman-collection.json`
- **Documentation**: `postman-readme.md`

See `postman-readme.md` for detailed setup instructions and usage examples.

## Technical Details

The REST API documentation is automatically generated using:

- **OpenAPI Feature**: Apache CXF's OpenApiFeature
- **Swagger UI**: Embedded Swagger UI (version 5.27.1)
- **Configuration**: Configured in `RestServer.java`

The OpenAPI specification is generated dynamically from the JAX-RS annotations on the REST endpoints.