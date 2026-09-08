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
package org.apache.unomi.rest.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.unomi.rest.exception.InvalidRequestException;
import org.apache.unomi.schema.api.SchemaService;

/**
 * Shared identifier checks used by public tracker endpoints.
 */
public final class RequestIdentifierValidator {

    static final String REQUEST_IDS_SCHEMA = "https://unomi.apache.org/schemas/json/rest/requestIds/1-0-0";

    private RequestIdentifierValidator() {
    }

    /**
     * Validates a session id that arrived outside the JSON body (query parameter fallback).
     *
     * @param schemaService schema service used to apply the requestIds grammar
     * @param sessionId     session identifier, may be {@code null}
     */
    public static void requireValidSessionId(SchemaService schemaService, String sessionId) {
        if (sessionId == null) {
            return;
        }
        ObjectNode paramsAsJson = JsonNodeFactory.instance.objectNode();
        paramsAsJson.put("sessionId", sessionId);
        if (!schemaService.isValid(paramsAsJson.toString(), REQUEST_IDS_SCHEMA)) {
            throw new InvalidRequestException("Invalid sessionId query parameter", "Invalid received data");
        }
    }
}
