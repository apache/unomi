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
import org.apache.unomi.api.Metadata;
import org.apache.unomi.rest.exception.InvalidRequestException;
import org.apache.unomi.schema.api.SchemaService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Base64;
import java.util.List;

@WebService
@Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
@CrossOriginResourceSharing(allowAllOrigins = true, allowCredentials = true)
@Path("/jsonSchema")
@Component(service = JsonSchemaEndPoint.class, property = "osgi.jaxrs.resource=true")
public class JsonSchemaEndPoint {

    private static final Logger logger = LoggerFactory.getLogger(JsonSchemaEndPoint.class.getName());

    @Reference
    private SchemaService schemaService;

    public JsonSchemaEndPoint() {
        logger.info("Initializing JSON schema endpoint...");
    }

    @WebMethod(exclude = true)
    public void setSchemaService(SchemaService schemaService) {
        this.schemaService = schemaService;
    }

    /**
     * Retrieves the 50 first json schema metadatas by default.
     *
     * @param offset zero or a positive integer specifying the position of the first element in the total ordered collection of matching elements
     * @param size   a positive integer specifying how many matching elements should be retrieved or {@code -1} if all of them should be retrieved
     * @param sortBy an optional ({@code null} if no sorting is required) String of comma ({@code ,}) separated property names on which ordering should be performed, ordering
     *               elements according to the property order in the
     *               String, considering each in turn and moving on to the next one in case of equality of all preceding ones. Each property name is optionally followed by
     *               a column ({@code :}) and an order specifier: {@code asc} or {@code desc}.
     * @return a List of the 50 first json schema metadata
     */
    @GET
    @Path("/")
    public List<Metadata> getJsonSchemaMetadatas(@QueryParam("offset") @DefaultValue("0") int offset,
            @QueryParam("size") @DefaultValue("50") int size, @QueryParam("sort") String sortBy) {
        return schemaService.getJsonSchemaMetadatas(offset, size, sortBy).getList();
    }

    /**
     * Save a JSON schema
     *
     * @param jsonSchema the schema as string to save
     * @return Response of the API call
     */
    @POST
    @Path("/")
    @Consumes(MediaType.TEXT_PLAIN)
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
     * Deletes a JSON schema.
     * The id is a Base64 id as the id have is basically an URL
     *
     * @param base64JsonSchemaId the identifier of the JSON schema that we want to delete
     */
    @DELETE
    @Path("/{base64JsonSchemaId}")
    public void remove(@PathParam("base64JsonSchemaId") String base64JsonSchemaId) {
        schemaService.deleteSchema(new String(Base64.getDecoder().decode(base64JsonSchemaId)));
    }
}
