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
package org.apache.unomi.api.schema.json;

import org.apache.unomi.api.services.SchemaRegistry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JSONObjectType extends JSONType {

    Map<String,List<JSONType>> properties = new HashMap<>();
    JSONType additionalProperties;
    Map<String,List<JSONType>> patternProperties = new HashMap<>();
    JSONType propertyNames;

    int maxProperties;

    public JSONObjectType(Map<String, Object> schemaTree, JSONTypeFactory jsonTypeFactory, SchemaRegistry schemaRegistry) {
        super(schemaTree, jsonTypeFactory, schemaRegistry);
        setType("object");
        Map<String,Object> propertiesTree = (Map<String,Object>) schemaTree.get("properties");
        if (propertiesTree != null) {
            propertiesTree.entrySet().forEach(entry -> {
                properties.put(entry.getKey(), jsonTypeFactory.getTypes((Map<String,Object>)entry.getValue()));
            });
        }
    }

    public Map<String,List<JSONType>> getProperties() {
        return properties;
    }

    public void setProperties(Map<String,List<JSONType>> properties) {
        this.properties = properties;
    }
}
