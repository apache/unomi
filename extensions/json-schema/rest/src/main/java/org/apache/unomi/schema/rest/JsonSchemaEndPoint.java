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
     * Get the list of installed Json Schema Ids
     *
     * @return A Set of JSON schema ids
     */
    @GET
    @Path("/")
    public Set<String> getInstalledJsonSchemaIds() {
        return schemaService.getInstalledJsonSchemaIds();
    }

    /**
     * Get a schema by it's id
     *
     * @param id of the schema
     * @return Json schema matching the id
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
     * Save a JSON schema
     *
     * @param jsonSchema the schema as string to save
     * @return Response of the API call
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
     * Deletes a JSON schema by it's id.
     *
     * @param id the identifier of the JSON schema that we want to delete
     */
    @POST
    @Path("/delete")
    public boolean remove(String id) {
        return schemaService.deleteSchema(id);
    }

    /**
     * Being able to validate a given event is useful when you want to develop custom events and associated schemas
     *
     * @param event the event to be validated
     * @return Validation error messages if there is some
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
     * Being able to validate a given list of event is useful when you want to develop custom events and associated schemas
     *
     * @param events the events to be validated
     * @return Validation error messages if there is some grouped per event type
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
