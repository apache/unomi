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
package org.apache.unomi.shell.dev.commands.properties;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.PropertyType;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.shell.dev.services.BaseCrudCommand;
import org.apache.unomi.shell.dev.services.CrudCommand;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component(service = CrudCommand.class, immediate = true)
public class PropertyTypeCrudCommand extends BaseCrudCommand {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final List<String> PROPERTY_NAMES = List.of(
        "itemId", "name", "description", "scope", "tags", "systemTags", "target", "valueTypeId",
        "defaultValue", "rank", "mergeStrategy", "multivalued", "protected"
    );

    @Reference
    private ProfileService profileService;

    @Override
    public String getObjectType() {
        return "propertytype";
    }

    @Override
    public String[] getHeaders() {
        return new String[] {
            "Identifier",
            "Name",
            "Description",
            "Target",
            "Value Type",
            "Default Value",
            "Multivalued",
            "Protected",
            "Rank",
            "Merge Strategy",
            "Scope",
            "Tags",
            "System Tags"
        };
    }

    @Override
    public PartialList<?> getItems(Query query) {
        List<PropertyType> propertyTypes = new ArrayList<>(profileService.getPropertyTypeByTag("*"));
        Integer start = query.getOffset();
        Integer size = query.getLimit();
        if (start == null) {
            start = 0;
        }
        if (size == null) {
            size = 50;
        }
        int end = Math.min(start + size, propertyTypes.size());
        List<PropertyType> pagedPropertyTypes = propertyTypes.subList(start, end);
        return new PartialList<>(pagedPropertyTypes, start, pagedPropertyTypes.size(), propertyTypes.size(), PartialList.Relation.EQUAL);
    }

    @Override
    protected String[] buildRow(Object item) {
        PropertyType propertyType = (PropertyType) item;
        return new String[] {
            propertyType.getItemId(),
            propertyType.getMetadata().getName(),
            propertyType.getMetadata().getDescription(),
            propertyType.getTarget(),
            propertyType.getValueTypeId(),
            propertyType.getDefaultValue(),
            String.valueOf(propertyType.isMultivalued()),
            String.valueOf(propertyType.isProtected()),
            propertyType.getRank() != null ? propertyType.getRank().toString() : "",
            propertyType.getMergeStrategy(),
            propertyType.getMetadata().getScope(),
            String.join(",", propertyType.getMetadata().getTags()),
            String.join(",", propertyType.getMetadata().getSystemTags())
        };
    }

    @Override
    public String create(Map<String, Object> properties) {
        PropertyType propertyType = OBJECT_MAPPER.convertValue(properties, PropertyType.class);
        profileService.setPropertyType(propertyType);
        return propertyType.getItemId();
    }

    @Override
    public Map<String, Object> read(String id) {
        PropertyType propertyType = profileService.getPropertyType(id);
        if (propertyType != null) {
            return OBJECT_MAPPER.convertValue(propertyType, Map.class);
        }
        return null;
    }

    @Override
    public void update(String id, Map<String, Object> properties) {
        PropertyType propertyType = OBJECT_MAPPER.convertValue(properties, PropertyType.class);
        propertyType.setItemId(id);
        profileService.setPropertyType(propertyType);
    }

    @Override
    public void delete(String id) {
        profileService.deletePropertyType(id);
    }

    @Override
    public String getPropertiesHelp() {
        return String.join("\n",
            "Required properties:",
            "- itemId: Identifier for the property type",
            "- name: Name of the property type",
            "- valueTypeId: Type of value (string, integer, long, date, etc.)",
            "",
            "Optional properties:",
            "- description: Description of the property type",
            "- target: Target type (e.g., 'profiles')",
            "- defaultValue: Default value for the property",
            "- rank: Numeric rank for ordering",
            "- mergeStrategy: Strategy for merging property values",
            "- multivalued: Whether the property can have multiple values (true/false)",
            "- protected: Whether the property is read-only (true/false)",
            "- scope: Scope of the property type",
            "- tags: List of tags",
            "- systemTags: List of system tags"
        );
    }

    @Override
    public List<String> completePropertyNames(String prefix) {
        return PROPERTY_NAMES.stream()
                .filter(name -> name.startsWith(prefix))
                .collect(Collectors.toList());
    }

    @Override
    public List<String> completePropertyValue(String propertyName, String prefix) {
        if ("valueTypeId".equals(propertyName)) {
            return List.of("string", "integer", "long", "date", "boolean", "json")
                    .stream()
                    .filter(type -> type.startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    public void setProfileService(ProfileService profileService) {
        this.profileService = profileService;
    }
}
