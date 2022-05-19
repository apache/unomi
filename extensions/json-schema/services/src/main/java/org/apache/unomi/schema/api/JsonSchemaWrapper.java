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

import org.apache.unomi.api.Item;
import org.apache.unomi.api.TimestampedItem;

import java.util.Date;

/**
 * Object which represents a JSON schema, it's a wrapper because it contains some additional info used by the
 * Service layer of Unomi like the id and the target.
 * The JSON schema is store as String to avoid transformation during JSON schema resolution in the Unomi SchemaService.
 * Also, it's extending  MetadataItem so that it can be persisted like that in Unomi storage system.
 */
public class JsonSchemaWrapper extends Item implements TimestampedItem {
    public static final String ITEM_TYPE = "jsonSchema";

    private String schema;
    private String target;
    private String extendsSchema;
    private Date timeStamp;

    /**
     * Instantiates a new JSON schema
     */
    public JsonSchemaWrapper() {
    }

    /**
     * Instantiates a new JSON schema
     *
     * @param id     id of the schema
     * @param schema as string
     * @param target of the schema (optional)
     * @param extendsSchema is the URI of another Schema to be extended by current one. (optional)
     * @param timeStamp of the schema
     */
    public JsonSchemaWrapper(String id, String schema, String target, String extendsSchema, Date timeStamp) {
        super(id);
        this.schema = schema;
        this.target = target;
        this.extendsSchema = extendsSchema;
        this.timeStamp = timeStamp;
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

    public String getExtendsSchema() {
        return extendsSchema;
    }

    public void setExtendsSchema(String extendsSchema) {
        this.extendsSchema = extendsSchema;
    }

    @Override
    public Date getTimeStamp() {
        return timeStamp;
    }
}