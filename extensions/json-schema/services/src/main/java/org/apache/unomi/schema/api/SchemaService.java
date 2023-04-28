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

package org.apache.unomi.schema.api;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service that allow to manage JSON schema. It allows to get, save and delete schemas
 */
public interface SchemaService {

    /**
     * Verify if a jsonNode is valid against a schema
     * (This method is fail safe, if unexpected errors happens it will returns false)
     *
     * @param data   to validate
     * @param schemaId id of the schema used for the validation
     * @return true is the object is valid, false otherwise, false also in case of unexpected errors !
     */
    boolean isValid(String data, String schemaId);

    /**
     * Deprecate (since 2.2.0).
     * the eventType is now directly extracted from the event source
     * You can directly use sibling function: isEventValid(String event)
     */
    @Deprecated
    boolean isEventValid(String event, String eventType);

    /**
     * Verify if the event is valid
     * (This method is fail safe, if unexpected errors happens it will returns false)
     *
     * @param event the event to check validity
     * @return true is the event is valid, false otherwise, false also in case of unexpected errors !
     */
    boolean isEventValid(String event);

    /**
     * perform a validation on the given event
     *
     * @param event the event to validate
     * @return The list of validation errors in case there is some, empty list otherwise
     * @throws ValidationException in case something goes wrong and validation could not be performed.
     */
    Set<ValidationError> validateEvent(String event) throws ValidationException;

    /**
     * perform a validation of a list of the given events
     *
     * @param events the events to validate
     * @return The Map of validation errors group per event type in case there is some, empty map otherwise
     * @throws ValidationException in case something goes wrong and validation could not be performed.
     */
    Map<String,Set<ValidationError>> validateEvents(String events) throws ValidationException;

    /**
     * Get the list of installed Json Schema Ids
     *
     * @return A Set of JSON schema ids
     */
    Set<String> getInstalledJsonSchemaIds();

    /**
     * Get a schema matching by a schema id
     *
     * @param schemaId Id of the schema
     * @return A JSON schema
     */
    JsonSchemaWrapper getSchema(String schemaId);

    /**
     * Get a list a {@link JsonSchemaWrapper}
     *
     * @param target to filter the schemas
     * @return a list of JSONSchema
     */
    List<JsonSchemaWrapper> getSchemasByTarget(String target);

    /**
     * Get the schema that is able to validate the specific event type
     *
     * @param eventType the eventType
     * @return The JSON Schema able to validate the given event type or null if not found.
     */
    JsonSchemaWrapper getSchemaForEventType(String eventType) throws ValidationException;

    /**
     * Save a new schema or update a schema
     *
     * @param schema as a String value
     */
    void saveSchema(String schema);

    /**
     * Delete a schema according to its id
     *
     * @param schemaId id of the schema to delete
     * @return true if the schema has been deleted
     */
    boolean deleteSchema(String schemaId);

    /**
     * Load a predefined schema into memory
     *
     * @param schemaStream inputStream of the schema
     */
    void loadPredefinedSchema(InputStream schemaStream) throws IOException;

    /**
     * Unload a predefined schema into memory
     *
     * @param schemaStream inputStream of the schema to delete
     * @return true if the schema has been deleted
     */
    boolean unloadPredefinedSchema(InputStream schemaStream);
}
