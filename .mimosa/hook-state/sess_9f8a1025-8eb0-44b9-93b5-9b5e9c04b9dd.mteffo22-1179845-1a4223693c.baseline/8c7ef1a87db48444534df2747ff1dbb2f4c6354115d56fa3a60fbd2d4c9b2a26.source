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

package org.apache.unomi.schema.rest;

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.apache.unomi.rest.exception.InvalidRequestException;
import org.apache.unomi.schema.api.JsonSchemaWrapper;
import org.apache.unomi.schema.api.SchemaService;
import org.apache.unomi.schema.api.ValidationError;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

@Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
@CrossOriginResourceSharing(allowAllOrigins = true, allowCredentials = true)
@Path("/jsonSchema")
@Component(service = JsonSchemaEndPoint.class, property = "osgi.jaxrs.resource=true")
public class JsonSchemaEndPoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(JsonSchemaEndPoint.class.getName());

    @Reference
    private SchemaService schemaService;

    public JsonSchemaEndPoint() {
        LOGGER.info("Initializing JSON schema endpoint...");
    }

    public void setSchemaService(SchemaService schemaService) {
        this.schemaService = schemaService;
    }

    /**
     * Returns identifiers of all installed JSON schemas.
     *
     * @return installed JSON schema ids
     * @api.status 200 array empty Schema id set (may be empty).
     * @api.example ["profile","event"]
     */
    @GET
    @Path("/")
    public Set<String> getInstalledJsonSchemaIds() {
        return schemaService.getInstalledJsonSchemaIds();
    }

    /**
     * Returns the JSON schema document for the given id.
     * <p>
     * Request body is the schema id as a plain JSON string. When no schema matches, returns {@code null} (HTTP 200 with empty body).
     *
     * @param id the schema identifier
     * @return the schema JSON string, or {@code null} when missing
     * @api.status 200 empty Schema JSON string, or empty body when missing.
     * @api.example "{\"$schema\":\"http://json-schema.org/draft-07/schema#\",\"type\":\"object\"}"
     */
    @POST
    @Path("/query")
    public String getSchema(String id) {
        JsonSchemaWrapper schema = schemaService.getSchema(id);
        if (schema != null) {
            return schema.getSchema().replace("\n", "");
        }
        return null;
    }

    /**
     * Saves a JSON schema document.
     *
     * @param jsonSchema the schema JSON as plain text or JSON body
     * @return an empty success response
     * @api.status 200 empty Schema saved.
     * @api.status 400 empty Invalid or unreadable schema body.
     * @api.example "{\"$schema\":\"https://json-schema.org/draft/2019-09/schema\",\"self\":{\"vendor\":\"com.example\",\"target\":\"events\",\"name\":\"customEvent\",\"format\":\"jsonschema\",\"version\":\"1-0-0\"},\"title\":\"CustomEvent\",\"type\":\"object\",\"allOf\":[{\"$ref\":\"https://unomi.apache.org/schemas/json/event/1-0-0\"}],\"properties\":{\"properties\":{\"type\":\"object\",\"properties\":{\"category\":{\"type\":\"string\"}}}},\"unevaluatedProperties\":false}"
     */
    @POST
    @Path("/")
    @Consumes({MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON})
    @Produces(MediaType.APPLICATION_JSON)
    public Response save(String jsonSchema) {
        try {
            schemaService.saveSchema(jsonSchema);
            return Response.ok().build();
        } catch (Exception e) {
            throw new InvalidRequestException(e.getMessage(), "Unable to save schema");
        }
    }

    /**
     * Deletes the JSON schema with the given id.
     * <p>
     * Request body is the schema id as a plain JSON string.
     *
     * @param id the schema identifier
     * @return {@code true} when the schema was deleted, {@code false} when it did not exist
     * @api.status 200 empty Deletion result flag.
     * @api.example true
     */
    @POST
    @Path("/delete")
    public boolean remove(String id) {
        return schemaService.deleteSchema(id);
    }

    /**
     * Validates a single event JSON document against installed schemas.
     *
     * @param event the event JSON to validate
     * @return validation errors (empty collection when valid)
     * @api.status 200 array org.apache.unomi.schema.api.ValidationError Validation errors (may be empty).
     * @api.status 400 empty Invalid or unreadable event body.
     * @api.example [{"error":"$.eventType: is missing but it is required"}]
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    @Consumes({MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON})
    @Path("/validateEvent")
    public Collection<ValidationError> validateEvent(String event) {
        try {
            return schemaService.validateEvent(event);
        } catch (Exception e) {
            String errorMessage = "Unable to validate event: " + e.getMessage();
            throw new InvalidRequestException(errorMessage, errorMessage);
        }
    }

    /**
     * Validates a batch of events JSON against installed schemas.
     *
     * @param events the events JSON to validate
     * @return validation errors grouped by event type (empty map when all valid)
     * @api.status 200 empty Event type to validation error set map (may be empty).
     * @api.status 400 empty Invalid or unreadable events body.
     * @api.example {"view":{"error":"$.scope: is missing but it is required"}}
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    @Consumes({MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON})
    @Path("/validateEvents")
    public Map<String, Set<ValidationError>> validateEvents(String events) {
        try {
            return schemaService.validateEvents(events);
        } catch (Exception e) {
            String errorMessage = "Unable to validate events: " + e.getMessage();
            throw new InvalidRequestException(errorMessage, errorMessage);
        }
    }
}
