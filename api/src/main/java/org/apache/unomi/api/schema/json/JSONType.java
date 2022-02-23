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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JSONType {

    String type;
    String name;
    List<String> required;
    String ref;
    List<JSONType> anyOf;
    List<JSONType> oneOf;

    Map<String,Object> customKeywords;

    protected Map<String,Object> schemaTree;

    protected JSONTypeFactory jsonTypeFactory;

    protected SchemaRegistry schemaRegistry;

    public JSONType(Map<String,Object> schemaTree, JSONTypeFactory jsonTypeFactory, SchemaRegistry schemaRegistry) {
        this.schemaTree = schemaTree;
        this.jsonTypeFactory = jsonTypeFactory;
        this.schemaRegistry = schemaRegistry;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Map<String, Object> getSchemaTree() {
        return schemaTree;
    }

    public JSONTypeFactory getJsonTypeFactory() {
        return jsonTypeFactory;
    }

    public SchemaRegistry getSchemaRegistry() {
        return schemaRegistry;
    }

    public String getRef() {
        ref = (String) schemaTree.get("$ref");
        return ref;
    }

    public List<JSONType> getAllOf() {
        List<Map<String,Object>> allOfTree = (List<Map<String,Object>>) schemaTree.get("allOf");
        List<JSONType> allOfTypes = new ArrayList<>();
        if (allOfTree != null) {
            for (Map<String,Object> allOfEntry : allOfTree) {
                List<JSONType> entryTypes = jsonTypeFactory.getTypes(allOfEntry);
                allOfTypes.addAll(entryTypes);
            }
        }
        return allOfTypes;
    }

    public List<JSONType> getAnyOf() {
        return anyOf;
    }

    public List<JSONType> getOneOf() {
        return oneOf;
    }

    public Map<String, Object> getCustomKeywords() {
        return customKeywords;
    }

    public boolean merge(JSONType anotherType) {
        if (!anotherType.getType().equals(getType())) {
            return false;
        }
        return true;
    }
}
