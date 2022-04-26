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
 * Object which represents a JSON schema stored in the persistence service
 */
public class JSONSchemaEntity extends MetadataItem {
    public static final String ITEM_TYPE = "jsonSchema";

    private String id;
    private String schema;
    private String target;

    public JSONSchemaEntity(){}

    /**
     * Instantiates a new JSON schema with an id and a schema as string
     *
     * @param id     id of the schema
     * @param schema as string
     * @param target of the schema
     */
    public JSONSchemaEntity(String id, String schema, String target) {
        super(new Metadata(id));
        this.id = id;
        this.schema = schema;
        this.target = target;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }
}
