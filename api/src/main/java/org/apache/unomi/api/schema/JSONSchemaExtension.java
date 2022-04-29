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
package org.apache.unomi.api.schema;

import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.MetadataItem;

/**
 * Object which represents a JSON schema extensions stored in the persistence service
 */
public class JSONSchemaExtension extends MetadataItem {
    public static final String ITEM_TYPE = "jsonSchemaExtension";

    private String id;
    private Object extension;
    private double priority;
    private String schemaId;

    public JSONSchemaExtension() {
    }

    /**
     * Instantiates a new JSON schema with an id and a schema extension as string
     *
     * @param id        id of the extension
     * @param schemaId  id of the schema
     * @param extension as Object
     * @param priority  priority to process the extension
     */
    public JSONSchemaExtension(String id, String schemaId, Object extension, float priority) {
        super(new Metadata(id));
        this.id = id;
        this.extension = extension;
        this.priority = priority;
        this.schemaId = schemaId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Object getExtension() {
        return extension;
    }

    public void setExtension(Object extension) {
        this.extension = extension;
    }

    public double getPriority() {
        return priority;
    }

    public void setPriority(double priority) {
        this.priority = priority;
    }

    public String getSchemaId() {
        return schemaId;
    }

    public void setSchemaId(String schemaId) {
        this.schemaId = schemaId;
    }
}
