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
import org.apache.unomi.didvc.services.MockPersistence;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Credential-schema CRUD and claim validation: the allowed-claim whitelist is
 * the claim-minimization gate — raw PII not mapped to a whitelisted claim is
 * rejected before any credential is built.
 */
class CredentialSchemaServiceImplTest {

    private PersistenceService persistenceService;
    private CredentialSchemaService schemaService;

    @BeforeEach
    void setUp() {
        persistenceService = MockPersistence.create();
        schemaService = new CredentialSchemaServiceImpl();
        ((CredentialSchemaServiceImpl) schemaService).setPersistenceService(persistenceService);
    }

    private DidSchema kycSchema(String tenantId) {
        DidSchema schema = new DidSchema(tenantId + "-kyc-v1");
        schema.setName("HKT Reusable KYC");
        schema.setVct("hkt_kyc_v1");
        schema.setTenantId(tenantId);
        schema.setAllowedClaims(new HashSet<>(Arrays.asList("kycLevel", "sanctionsClear", "nationality")));
        schema.setRequiredClaims(new HashSet<>(Arrays.asList("kycLevel", "sanctionsClear")));
        Map<String, String> claimTypes = new HashMap<>();
        claimTypes.put("kycLevel", "string");
        claimTypes.put("sanctionsClear", "boolean");
        claimTypes.put("nationality", "string");
        schema.setClaimTypes(claimTypes);
        return schema;
    }

    @Test
    void saveAndGet() {
        DidSchema saved = kycSchema("hkt");
        schemaService.saveSchema(saved);
        DidSchema loaded = schemaService.getSchema(saved.getItemId());
        assertEquals("hkt_kyc_v1", loaded.getVct());
        assertEquals("didvc:schema", loaded.getItemType());
        assertEquals("didvc", loaded.getScope());
        assertEquals(3, loaded.getAllowedClaims().size());
    }

    @Test
    void saveUpdatesExisting() {
        DidSchema schema = kycSchema("hkt");
        schemaService.saveSchema(schema);
        schema.setDescription("updated");
        schemaService.saveSchema(schema);
        assertEquals("updated", schemaService.getSchema(schema.getItemId()).getDescription());
    }

    @Test
    void delete() {
        DidSchema saved = kycSchema("hkt");
        schemaService.saveSchema(saved);
        schemaService.deleteSchema(saved.getItemId());
        assertNull(schemaService.getSchema(saved.getItemId()));
    }

    @Test
    void schemasScopedByTenant() {
        schemaService.saveSchema(kycSchema("hkt"));
        schemaService.saveSchema(kycSchema("bank-a"));
        List<DidSchema> hktSchemas = schemaService.getSchemas("hkt");
        assertEquals(1, hktSchemas.size());
        assertEquals("hkt", hktSchemas.get(0).getTenantId());
    }

    @Test
    void validClaimsPass() {
        DidSchema schema = kycSchema("hkt");
        Map<String, Object> claims = new HashMap<>();
        claims.put("kycLevel", "REMOTE_FULL");
        claims.put("sanctionsClear", true);
        claims.put("nationality", "HK");
        schemaService.validateClaims(schema, claims);
    }

    @Test
    void nonWhitelistedClaimRejected() {
        // The raw attribute must never slip into a credential payload
        DidSchema schema = kycSchema("hkt");
        Map<String, Object> claims = new HashMap<>();
        claims.put("kycLevel", "REMOTE_FULL");
        claims.put("sanctionsClear", true);
        claims.put("idDocumentNumber", "R123456(7)");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> schemaService.validateClaims(schema, claims));
        assertTrue(e.getMessage().contains("idDocumentNumber"));
        assertTrue(e.getMessage().contains("whitelisted"));
    }

    @Test
    void missingRequiredClaimRejected() {
        DidSchema schema = kycSchema("hkt");
        Map<String, Object> claims = new HashMap<>();
        claims.put("kycLevel", "REMOTE_FULL");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> schemaService.validateClaims(schema, claims));
        assertTrue(e.getMessage().contains("sanctionsClear"));
    }

    @Test
    void wrongClaimTypeRejected() {
        DidSchema schema = kycSchema("hkt");
        Map<String, Object> claims = new HashMap<>();
        claims.put("kycLevel", "REMOTE_FULL");
        claims.put("sanctionsClear", "yes");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> schemaService.validateClaims(schema, claims));
        assertTrue(e.getMessage().contains("boolean"));
    }

    @Test
    void nullSchemaOrClaimsRejected() {
        assertThrows(NullPointerException.class, () -> schemaService.validateClaims(null, new HashMap<>()));
        assertThrows(NullPointerException.class, () -> schemaService.validateClaims(kycSchema("hkt"), null));
    }
}
