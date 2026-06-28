<!--
 Licensed to the Apache Software Foundation (ASF) under one or more
 contributor license agreements.  See the NOTICE file distributed with
 this work for additional information regarding copyright ownership.
 The ASF licenses this file to You under the Apache License, Version 2.0
 (the "License"); you may not use this file except in compliance with
 the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
-->
# Apache Unomi Postman Collection

This Postman collection provides comprehensive examples for getting started with Apache Unomi REST API.

## Setup Instructions

### 1. Import the Collection

1. Open Postman
2. Click **Import** button
3. Select the `unomi-postman-collection.json` file
4. The collection will appear in your Postman workspace

### 2. Configure Environment Variables

The collection uses several variables that you need to configure:

#### Required Variables

1. **baseUrl**: Your Unomi server URL
   - Default: `http://localhost:8181`
   - Update if your server is running on a different host/port

2. **scope**: The scope identifier for your tenant
   - Default: `example`
   - This should match your tenant's scope

3. **publicApiKey**: Your tenant's public API key
   - Get this from your tenant after creating it
   - Used for public API endpoints (`/context.json`, `/eventcollector`)
   - Sent via `X-Unomi-Api-Key` header

4. **privateApiKey**: Your tenant's private API key
   - Get this from your tenant after creating it
   - Used for private API endpoints (segments, rules, profiles, scopes, etc.)
   - Used with `tenantId` for Basic Auth (tenantId:privateApiKey)

5. **tenantId**: Your tenant ID
   - Default: `mytenant`
   - Used with `privateApiKey` for Basic Auth on private endpoints
   - Update this to match your actual tenant ID

6. **adminUsername**: Admin username for administrative endpoints
   - Default: `karaf`
   - Used only for tenant management endpoints (`/cxs/tenants`)

7. **adminPassword**: Admin password for administrative endpoints
   - Default: `karaf`
   - Used only for tenant management endpoints (`/cxs/tenants`)

8. **healthUsername**: Health check username
   - Default: `health`
   - Used for health check endpoint

9. **healthPassword**: Health check password
   - Default: `health`
   - Used for health check endpoint

#### Optional Variables (with defaults)

- **sessionId**: `session-123` (used for context requests)
- **profileId**: `profile-123` (used for profile operations)
- **segmentId**: `premium-users` (used for segment operations)
- **ruleId**: `set-premium-on-purchase` (used for rule operations)
- **scopeId**: `example` (used for scope management operations)

### 3. Setting Up Variables in Postman

#### Option 1: Collection Variables (Recommended)
1. Right-click the collection → **Edit**
2. Go to **Variables** tab
3. Update the values for your environment
4. **No base64 encoding needed!** Postman automatically handles Basic Auth encoding

#### Option 2: Environment Variables
1. Create a new Environment in Postman
2. Add all the variables listed above
3. Select the environment before making requests

## Collection Structure

The collection is organized in a logical demo flow order with 12 main folders:

### 1. Health Check
- Basic health check endpoint to verify server status
- **Authentication**: `healthUsername:healthPassword` (Basic Auth)

### 2. Tenant Management
- Create, list, update, delete tenants
- Generate API keys (PUBLIC and PRIVATE)
- **Authentication**: `adminUsername:adminPassword` (Basic Auth)
- **Important**: Save both PUBLIC and PRIVATE API keys from the create tenant response!

### 3. Scope Management
- **List All Scopes**: Retrieve all scopes in the system
- **Get Scope by ID**: Retrieve a specific scope by its identifier
- **Create Scope**: Create a new scope with basic metadata
- **Create Scope with Tags**: Create a scope with tags and additional metadata
- **Update Scope**: Update an existing scope (use POST with same itemId)
- **Delete Scope**: Delete a scope by ID
- **Authentication**: `tenantId:privateApiKey` (Basic Auth)

### 4. Event Schema Creation
- Create JSON schemas for custom events
- Validate events against schemas
- List and manage schemas
- **Authentication**: `tenantId:privateApiKey` (Basic Auth)

### 5. Basic Context Requests
- Minimal context requests
- Context with source information
- Context with all properties
- Context with specific properties
- **Authentication**: `X-Unomi-Api-Key` header with `publicApiKey`

### 6. Segment Creation
- Simple segments (single condition)
- Complex segments (multiple conditions)
- Segments based on past events
- Nested segment conditions
- Segment management operations
- **Authentication**: `tenantId:privateApiKey` (Basic Auth)

### 7. Context Requests with Events
- Context requests with view events
- Context requests with custom events
- Event collector for multiple events
- **Authentication**: `X-Unomi-Api-Key` header with `publicApiKey`

### 8. Personalization
- Simple personalization (matching-first strategy)
- Score-based personalization (score-sorted strategy)
- **Authentication**: `X-Unomi-Api-Key` header with `publicApiKey`

### 9. Rule Management
- Create rules with conditions and actions
- List and get rules
- View rule statistics
- Delete rules
- **Authentication**: `tenantId:privateApiKey` (Basic Auth)

### 11. Profile Management
- Get, create, update, delete profiles
- Search profiles
- Get profile segments and sessions
- **Authentication**: `tenantId:privateApiKey` (Basic Auth)

### 10. Explain Context Request
- Context requests with explain mode enabled
- Returns detailed tracing information
- **Authentication**: `X-Unomi-Api-Key` header with `publicApiKey`

### 12. Additional Useful Requests
- Event type management
- Event search
- Condition and action definitions
- Property types
- **Authentication**: `tenantId:privateApiKey` (Basic Auth)

## Quick Start Guide

### Step 1: Check Server Health
1. Go to **1. Health Check** → **Health Check**
2. Update `healthAuth` variable if needed
3. Send the request
4. Verify all components are LIVE

### Step 2: Create a Tenant
1. Go to **2. Tenant Management** → **Create Tenant**
2. Update the request body with your tenant details (or use the default `{{tenantId}}`)
3. Send the request
4. **IMPORTANT**: Save both API keys from the response:
   - Find the API key with `"type": "PUBLIC"` → Update `publicApiKey` variable
   - Find the API key with `"type": "PRIVATE"` → Update `privateApiKey` variable
5. Update `tenantId` variable to match your actual tenant ID

### Step 3: Create a Scope (if needed)
1. Go to **3. Scope Management** → **Create Scope**
2. Update the request body with your scope ID and metadata
3. Send the request

### Step 4: Create a Simple Segment
1. Go to **6. Segment Creation** → **Simple Segment - Profile Property**
2. Update the `scope` variable if needed
3. Send the request (uses `tenantId:privateApiKey` authentication)

### Step 5: Make a Basic Context Request
1. Go to **5. Basic Context Requests** → **Get Context (Minimal)**
2. Update `sessionId` if needed
3. Send the request (uses `X-Unomi-Api-Key` header with `publicApiKey`)
4. Note the `profileId` and `sessionId` in the response

### Step 6: Send an Event
1. Go to **7. Context Requests with Events** → **Context with View Event**
2. Update `sessionId` and `scope` variables
3. Send the request (uses `X-Unomi-Api-Key` header with `publicApiKey`)

## Common Use Cases

### Creating a Custom Event Type

1. **Create Event Schema** (4. Event Schema Creation → Create Event Schema)
2. **Validate Event** (4. Event Schema Creation → Validate Event)
3. **Send Custom Event** (7. Context Requests with Events → Context with Custom Event)

### Setting Up Personalization

1. **Create Segments** (6. Segment Creation)
2. **Create Rules** (9. Rule Management) - Optional, to automatically add users to segments
3. **Request Personalization** (8. Personalization)

### Debugging with Explain Mode

1. **Enable Explain** (10. Explain Context Request → Context Request with Explain)
2. Review the `requestTracing` object in the response
3. Check rule execution, segment evaluation, and personalization decisions

## Tips

1. **Start Simple**: Begin with basic context requests before moving to complex segments and rules
2. **Use Explain Mode**: When debugging, use explain mode to understand why rules/segments match or don't match
3. **Save Responses**: Save important responses (like tenant creation) to retrieve API keys
4. **Update Variables**: Keep your variables up to date, especially `sessionId` and `profileId` as you work
5. **Check Health**: Regularly check the health endpoint to ensure all components are running

## Troubleshooting

### 401 Unauthorized
- **Public endpoints** (`/context.json`, `/eventcollector`): Check that `publicApiKey` is set correctly
- **Private endpoints** (segments, rules, profiles, scopes): Check that both `tenantId` and `privateApiKey` are set correctly
- **Admin endpoints** (`/cxs/tenants`): Verify `adminUsername` and `adminPassword` are correct
- **Health endpoint**: Verify `healthUsername` and `healthPassword` are correct

### 404 Not Found
- Verify the endpoint URL is correct
- Check that the resource (tenant, segment, rule) exists
- Ensure you're using the correct scope

### 400 Bad Request
- Validate your JSON payload structure
- Check that required fields are present
- For custom events, ensure the schema exists and matches the event type

### Schema Validation Errors
- Ensure event schema name matches the `eventType` in your events
- Check that all required properties are present
- Verify property types match the schema definition

## Additional Resources

- [Apache Unomi Documentation](https://unomi.apache.org/)
- [Unomi API Reference](https://unomi.apache.org/unomi-api/)
- [Unomi Manual](https://unomi.apache.org/manual/)

## Notes

- All timestamps in Unomi are in ISO 8601 format
- Session IDs and Profile IDs are case-sensitive
- Scopes are used to isolate data between different tenants/applications
- Rules are executed automatically when matching events occur
- Segments are evaluated dynamically based on current profile state
