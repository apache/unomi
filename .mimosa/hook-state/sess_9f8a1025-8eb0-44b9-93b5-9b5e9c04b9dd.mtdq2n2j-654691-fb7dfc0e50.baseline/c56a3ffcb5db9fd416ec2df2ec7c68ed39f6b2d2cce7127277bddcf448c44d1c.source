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

package org.apache.unomi.didvc.api.services;

import org.apache.unomi.didvc.api.items.DidSchema;

import java.util.List;
import java.util.Map;

/**
 * Credential-schema registry and claim validation. Claim validation enforces
 * the schema's allowed-claim whitelist — the claim-minimization gate that
 * prevents raw PII from entering credential payloads.
 */
public interface CredentialSchemaService {

    /**
     * Saves (creates or updates) a credential schema.
     *
     * @param schema the schema
     */
    void saveSchema(DidSchema schema);

    /**
     * Loads a schema by identifier.
     *
     * @param schemaId the schema identifier
     * @return the schema, or null if unknown
     */
    DidSchema getSchema(String schemaId);

    /**
     * Deletes a schema.
     *
     * @param schemaId the schema identifier
     */
    void deleteSchema(String schemaId);

    /**
     * Lists a tenant's schemas.
     *
     * @param tenantId the tenant
     * @return the tenant's schemas
     */
    List<DidSchema> getSchemas(String tenantId);

    /**
     * Validates claims against the schema: every claim must be in the allowed
     * set, required claims must be present, and claim values must match their
     * declared types. Throws {@link IllegalArgumentException} on violation.
     *
     * @param schema the schema
     * @param claims the claims to validate
     */
    void validateClaims(DidSchema schema, Map<String, Object> claims);
}
