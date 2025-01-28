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
package org.apache.unomi.shell.commands.schema;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.schema.api.JsonSchemaWrapper;
import org.apache.unomi.schema.api.SchemaService;
import org.apache.unomi.shell.dev.services.BaseCrudCommand;
import org.apache.unomi.shell.dev.services.CrudCommand;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.*;
import java.util.stream.Collectors;

@Component(service = CrudCommand.class, immediate = true)
public class SchemaCrudCommand extends BaseCrudCommand {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final List<String> PROPERTY_NAMES = List.of(
        "$id", "self.target", "self.name", "self.extends", "properties", "required", "allOf"
    );
    private static final List<String> TARGET_TYPES = List.of(
        "events", "profiles", "sessions", "rules", "segments"
    );

    @Reference
    private SchemaService schemaService;

    @Reference
    private TenantService tenantService;

    @Override
    public String getObjectType() {
        return "schema";
    }

    @Override
    public String create(Map<String, Object> properties) {
        try {
            String schema = OBJECT_MAPPER.writeValueAsString(properties);
            schemaService.saveSchema(schema);
            return properties.get("$id").toString();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error processing JSON schema", e);
        }
    }

    @Override
    public Map<String, Object> read(String id) {
        try {
            JsonSchemaWrapper schema = schemaService.getSchema(id);
            if (schema == null) {
                return null;
            }
            Map<String, Object> result = new HashMap<>();
            result.put("id", schema.getItemId());
            result.put("name", schema.getName());
            result.put("target", schema.getTarget());
            result.put("tenantId", schema.getTenantId());
            if (schema.getExtendsSchemaId() != null) {
                result.put("extends", schema.getExtendsSchemaId());
            }
            result.put("schema", OBJECT_MAPPER.readValue(schema.getSchema(), Map.class));
            return result;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error reading JSON schema", e);
        }
    }

    @Override
    public void update(String id, Map<String, Object> properties) {
        try {
            // Ensure the ID matches
            properties.put("$id", id);
            String schema = OBJECT_MAPPER.writeValueAsString(properties);
            schemaService.saveSchema(schema);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error updating JSON schema", e);
        }
    }

    @Override
    public void delete(String id) {
        schemaService.deleteSchema(id);
    }

    @Override
    public String getPropertiesHelp() {
        return "Required properties:\n" +
            "- $id: Schema ID (URI)\n" +
            "- self.target: Target type (e.g. \"events\", \"profiles\", \"sessions\", \"rules\", \"segments\")\n" +
            "- self.name: Schema name\n" +
            "\n" +
            "Optional properties:\n" +
            "- self.extends: ID of schema to extend\n" +
            "- properties: JSON Schema properties\n" +
            "- required: List of required properties\n" +
            "- allOf: List of schemas to extend";
    }

    @Override
    public List<String> completePropertyNames(String prefix) {
        return PROPERTY_NAMES.stream()
                .filter(name -> name.startsWith(prefix))
                .collect(Collectors.toList());
    }

    @Override
    public List<String> completePropertyValue(String propertyName, String prefix) {
        if ("self.target".equals(propertyName)) {
            return TARGET_TYPES.stream()
                    .filter(type -> type.startsWith(prefix))
                    .collect(Collectors.toList());
        } else if ("self.extends".equals(propertyName)) {
            return new ArrayList<>(schemaService.getInstalledJsonSchemaIds()).stream()
                    .filter(id -> id.startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    @Override
    public String[] getHeaders() {
        return new String[] {
            "ID",
            "Target",
            "Name",
            "Extends"
        };
    }

    @Override
    protected PartialList<?> getItems(Query query) {
        List<JsonSchemaWrapper> schemas = new ArrayList<>();
        Set<String> schemaIds = schemaService.getInstalledJsonSchemaIds();
        for (String schemaId : schemaIds) {
            JsonSchemaWrapper schema = schemaService.getSchema(schemaId);
            if (schema != null) {
                schemas.add(schema);
            }
        }
        int totalSize = schemas.size();
        int start = 0;
        int end = Math.min(query.getLimit(), totalSize);
        return new PartialList<JsonSchemaWrapper>(schemas.subList(start, end), start, end, totalSize, PartialList.Relation.EQUAL);
    }

    @Override
    protected Comparable[] buildRow(Object item) {
        JsonSchemaWrapper schema = (JsonSchemaWrapper) item;
        return new Comparable[] {
            schema.getItemId(),
            schema.getTarget(),
            schema.getName(),
            schema.getExtendsSchemaId() != null ? schema.getExtendsSchemaId() : ""
        };
    }
}
