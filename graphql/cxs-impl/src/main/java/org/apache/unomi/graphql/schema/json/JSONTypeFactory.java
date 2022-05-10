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
package org.apache.unomi.graphql.schema.json;

import org.apache.unomi.graphql.schema.GraphQLSchemaProvider;
import org.apache.unomi.schema.api.JsonSchemaWrapper;
import org.apache.unomi.schema.api.SchemaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JSONTypeFactory {

    private static final Logger logger = LoggerFactory.getLogger(JSONTypeFactory.class);

    Map<String, Class<? extends JSONType>> jsonTypes = new HashMap<>();

    SchemaService schemaService;

    public JSONTypeFactory(SchemaService schemaService) {
        this.schemaService = schemaService;
        jsonTypes.put("object", JSONObjectType.class);
        jsonTypes.put("string", JSONStringType.class);
        jsonTypes.put("array", JSONArrayType.class);
        jsonTypes.put("number", JSONNumberType.class);
        jsonTypes.put("integer", JSONIntegerType.class);
        jsonTypes.put("boolean", JSONBooleanType.class);
        jsonTypes.put("null", JSONNullType.class);
    }

    List<JSONType> getTypes(Map<String, Object> schemaTree) {
        if (schemaTree.containsKey("$ref")) {
            String schemaId = (String) schemaTree.get("$ref");
            JsonSchemaWrapper refSchema = schemaService.getSchema(schemaId);
            if (refSchema != null) {
                schemaTree = GraphQLSchemaProvider.buildJSONSchema(refSchema, schemaService).getSchemaTree();
            } else {
                System.err.println("Couldn't find schema for ref " + schemaId);
            }
        }
        if (schemaTree.containsKey("enum")) {
            List<JSONType> result = new ArrayList<>();
            result.add(new JSONEnumType(schemaTree, this));
            return result;
        }
        Object typeObject = schemaTree.get("type");
        if (typeObject == null) {
            return new ArrayList<>();
        }
        List<String> types = null;
        if (typeObject instanceof String) {
            types = new ArrayList<>();
            types.add((String) typeObject);
        } else {
            types = (List<String>) typeObject;
        }
        List<JSONType> resultJsonTypes = new ArrayList<>();
        for (String type : types) {
            if (type == null) {
                continue;
            }
            if (!jsonTypes.containsKey(type)) {
                continue;
            }
            Class<? extends JSONType> typeClass = jsonTypes.get(type);
            Constructor<? extends JSONType> constructor;
            try {
                constructor = typeClass.getConstructor(Map.class, JSONTypeFactory.class);
                resultJsonTypes.add(constructor.newInstance(schemaTree, this));
            } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
                logger.error("Error while building object type", e);
            }
        }
        return resultJsonTypes;
    }

}
