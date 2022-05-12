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

import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.PartialList;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Service that allow to manage JSON schema. It allows to get, save and delete schemas
 */
public interface SchemaService {

    /**
     * Retrieves json schema metadatas, ordered according to the specified {@code sortBy} String and and paged: only {@code size} of them
     * are retrieved, starting with the {@code
     * offset}-th one.
     *
     * @param offset zero or a positive integer specifying the position of the first element in the total ordered collection of matching elements
     * @param size   a positive integer specifying how many matching elements should be retrieved or {@code -1} if all of them should be retrieved
     * @param sortBy an optional ({@code null} if no sorting is required) String of comma ({@code ,}) separated property names on which ordering should be performed, ordering elements according to the property order in the
     *               String, considering each in turn and moving on to the next one in case of equality of all preceding ones. Each property name is optionally followed by
     *               a column ({@code :}) and an order specifier: {@code asc} or {@code desc}.
     * @return a {@link PartialList} of json schema metadata
     */
    PartialList<Metadata> getJsonSchemaMetadatas(int offset, int size, String sortBy);

    /**
     * Verify if a jsonNode is valid against a schema
     *
     * @param data   to validate
     * @param schemaId id of the schema used for the validation
     * @return true is the object is valid
     */
    boolean isValid(String data, String schemaId);

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
