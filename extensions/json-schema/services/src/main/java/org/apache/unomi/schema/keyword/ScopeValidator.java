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
package org.apache.unomi.schema.keyword;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.*;
import org.apache.unomi.api.services.ScopeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.util.LinkedHashSet;
import java.util.Set;

public class ScopeValidator extends BaseJsonValidator implements JsonValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScopeValidator.class);

    private ScopeService scopeService;

    public ScopeValidator(String schemaPath, JsonNode schemaNode, JsonSchema parentSchema, ValidationContext validationContext, ScopeService scopeService) {
        super(schemaPath, schemaNode, parentSchema, null, validationContext);
        this.scopeService = scopeService;
    }

    @Override
    public Set<ValidationMessage> validate(JsonNode node, JsonNode rootNode, String at) {
        LOGGER.debug("validate( {}, {}, {})", node, rootNode, at);
        Set<ValidationMessage> errors = new LinkedHashSet<>();
        if (scopeService.getScope(node.textValue()) == null) {
            ValidationMessage.Builder builder = new ValidationMessage.Builder();
            builder.customMessage("Unknown scope value at " + at + " for value " + node.textValue()).format(new MessageFormat("Not used pattern. Message format is required"));
            errors.add(builder.build());
        }
        return errors;
    }
}
