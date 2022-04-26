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
package org.apache.unomi.services.impl.schemas;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.AbstractJsonValidator;
import com.networknt.schema.AbstractKeyword;
import com.networknt.schema.CustomErrorMessageType;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaException;
import com.networknt.schema.JsonValidator;
import com.networknt.schema.ValidationContext;
import com.networknt.schema.ValidationMessage;
import org.apache.unomi.api.PropertyType;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.api.services.SchemaService;

import java.text.MessageFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

class UnomiPropertyTypeKeyword extends AbstractKeyword {

    private final ProfileService profileService;
    private final SchemaServiceImpl schemaService;

    private static final class UnomiPropertyTypeJsonValidator extends AbstractJsonValidator {

        String schemaPath;
        JsonNode schemaNode;
        JsonSchema parentSchema;
        ValidationContext validationContext;
        ProfileService profileService;
        SchemaServiceImpl schemaService;

        public UnomiPropertyTypeJsonValidator(String keyword, String schemaPath, JsonNode schemaNode, JsonSchema parentSchema,
                ValidationContext validationContext, ProfileService profileService, SchemaServiceImpl schemaService) {
            super(keyword);
            this.schemaPath = schemaPath;
            this.schemaNode = schemaNode;
            this.parentSchema = parentSchema;
            this.validationContext = validationContext;
            this.profileService = profileService;
            this.schemaService = schemaService;
        }

        @Override
        public Set<ValidationMessage> validate(JsonNode node, JsonNode rootNode, String at) {
            Set<ValidationMessage> validationMessages = new HashSet<>();
            Iterator<String> fieldNames = node.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                PropertyType propertyType = getPropertyType(fieldName);
                if (propertyType == null) {
                    validationMessages.add(buildValidationMessage(CustomErrorMessageType
                            .of("property-not-found", new MessageFormat("{0} : Couldn''t find property type with id={1}")), at, fieldName));
                } else {
                    // @todo further validation, if it can be used in this context (event, profile, session)
                    String valueTypeId = propertyType.getValueTypeId();
                    JsonSchema jsonSchema = schemaService
                            .getJsonSchema("https://unomi.apache.org/schemas/json/values/" + valueTypeId + ".json");
                    if (jsonSchema == null) {
                        validationMessages.add(buildValidationMessage(CustomErrorMessageType
                                        .of("value-schema-not-found", new MessageFormat("{0} : Couldn''t find schema type with id={1}")), at,
                                "https://unomi.apache.org/schemas/json/values/" + valueTypeId + ".json"));
                    } else {
                        Set<ValidationMessage> propertyValidationMessages = jsonSchema.validate(node.get(fieldName));
                        if (propertyValidationMessages != null) {
                            validationMessages.addAll(propertyValidationMessages);
                        }
                    }
                }
            }
            return validationMessages;
        }

        private PropertyType getPropertyType(String fieldName) {
            Map<String, PropertyType> propertyTypes = new HashMap<>();
            if (schemaNode.size() > 0) {
                for (Iterator<JsonNode> it = schemaNode.iterator(); it.hasNext(); ) {
                    JsonNode target = it.next();
                    if ("_all".equals(target.asText())) {
                        return profileService.getPropertyType(fieldName);
                    } else {
                        Collection<PropertyType> targetPropertyTypes = profileService.getTargetPropertyTypes(target.asText());
                        targetPropertyTypes.stream().map(propertyType -> propertyTypes.put(propertyType.getItemId(), propertyType));
                    }
                }
                return propertyTypes.get(fieldName);
            } else {
                return profileService.getPropertyType(fieldName);
            }
        }
    }

    public UnomiPropertyTypeKeyword(ProfileService profileService, SchemaServiceImpl schemaService) {
        super("unomiPropertyTypes");
        this.profileService = profileService;
        this.schemaService = schemaService;
    }

    @Override
    public JsonValidator newValidator(String schemaPath, JsonNode schemaNode, JsonSchema parentSchema, ValidationContext validationContext)
            throws JsonSchemaException, Exception {
        return new UnomiPropertyTypeJsonValidator(this.getValue(), schemaPath, schemaNode, parentSchema, validationContext, profileService,
                schemaService);
    }
}
