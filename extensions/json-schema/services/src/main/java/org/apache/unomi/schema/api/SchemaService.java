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
import java.util.Set;

/**
 * Service that allow to manage JSON schema. It allows to get, save and delete schemas
 */
public interface SchemaService {

    /**
     * Verify if a jsonNode is valid against a schema
     *
     * @param data   to validate
     * @param schemaId id of the schema used for the validation
     * @return true is the object is valid
     */
    boolean isValid(String data, String schemaId);

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
