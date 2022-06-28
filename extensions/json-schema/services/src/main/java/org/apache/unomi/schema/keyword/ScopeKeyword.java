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
import com.networknt.schema.AbstractKeyword;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonValidator;
import com.networknt.schema.ValidationContext;
import org.apache.unomi.api.services.ScopeService;

public class ScopeKeyword extends AbstractKeyword {

    private ScopeService scopeService;

    public ScopeKeyword(ScopeService scopeService) {
        super("scope");
        this.scopeService = scopeService;
    }

    @Override
    public JsonValidator newValidator(String schemaPath, JsonNode schemaNode, JsonSchema parentSchema,
            ValidationContext validationContext) {
        return new ScopeValidator(scopeService);
    }
}
