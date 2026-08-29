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

package org.apache.unomi.didvc.services.impl;

import org.apache.unomi.didvc.api.items.DidSchema;
import org.apache.unomi.didvc.api.services.CredentialSchemaService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Credential-schema registry with claim validation. Validation enforces the
 * schema's allowed-claim whitelist — the claim-minimization gate that keeps
 * raw PII out of credential payloads.
 */
@Component(service = CredentialSchemaService.class, immediate = true)
public class CredentialSchemaServiceImpl implements CredentialSchemaService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CredentialSchemaServiceImpl.class);

    @Reference
    private PersistenceService persistenceService;

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @Override
    public void saveSchema(DidSchema schema) {
        Objects.requireNonNull(schema, "schema must not be null");
        if (schema.getItemType() == null) {
            schema.setItemType(DidSchema.ITEM_TYPE);
        }
        if (schema.getScope() == null) {
            schema.setScope("didvc");
        }
        persistenceService.save(schema);
        LOGGER.info("Saved credential schema {} (vct={}, allowed claims={})",
                schema.getItemId(), schema.getVct(), schema.getAllowedClaims() == null ? 0 : schema.getAllowedClaims().size());
    }

    @Override
    public DidSchema getSchema(String schemaId) {
        return persistenceService.load(schemaId, DidSchema.class);
    }

    @Override
    public void deleteSchema(String schemaId) {
        persistenceService.remove(schemaId, DidSchema.class);
    }

    @Override
    public List<DidSchema> getSchemas(String tenantId) {
        List<DidSchema> result = new ArrayList<>();
        for (DidSchema schema : persistenceService.getAllItems(DidSchema.class)) {
            if (tenantId == null || tenantId.equals(schema.getTenantId())) {
                result.add(schema);
            }
        }
        return result;
    }

    @Override
    public void validateClaims(DidSchema schema, Map<String, Object> claims) {
        Objects.requireNonNull(schema, "schema must not be null");
        Objects.requireNonNull(claims, "claims must not be null");
        for (String claim : claims.keySet()) {
            if (schema.getAllowedClaims() == null || !schema.getAllowedClaims().contains(claim)) {
                throw new IllegalArgumentException("Claim '" + claim + "' is not in the allowed claim set of schema "
                        + schema.getItemId() + " — raw attributes must be mapped to whitelisted claims before issuance");
            }
        }
        if (schema.getRequiredClaims() != null) {
            for (String required : schema.getRequiredClaims()) {
                if (!claims.containsKey(required) || claims.get(required) == null) {
                    throw new IllegalArgumentException("Missing required claim '" + required + "' for schema "
                            + schema.getItemId());
                }
            }
        }
        if (schema.getClaimTypes() != null) {
            for (Map.Entry<String, Object> entry : claims.entrySet()) {
                String expectedType = schema.getClaimTypes().get(entry.getKey());
                if (expectedType != null && !matches(expectedType, entry.getValue())) {
                    throw new IllegalArgumentException("Claim '" + entry.getKey() + "' must be of type " + expectedType
                            + " for schema " + schema.getItemId());
                }
            }
        }
    }

    private boolean matches(String expectedType, Object value) {
        if (value == null) {
            return false;
        }
        switch (expectedType) {
            case "string":
                return value instanceof String;
            case "number":
                return value instanceof Number;
            case "boolean":
                return value instanceof Boolean;
            case "array":
                return value instanceof List;
            case "object":
                return value instanceof Map;
            default:
                throw new IllegalArgumentException("Unknown claim type '" + expectedType + "' declared in schema");
        }
    }
}
