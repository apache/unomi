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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 4 schema bootstrap: registers the professional qualification and
 * residency schemas exactly once, with minimization whitelists.
 */
class Phase4SchemaBootstrapTest {

    @Test
    void registersBothSchemasOnActivation() {
        CredentialSchemaServiceImpl schemaService = new CredentialSchemaServiceImpl();
        schemaService.setPersistenceService(MockPersistence.create());
        Phase4SchemaBootstrap bootstrap = new Phase4SchemaBootstrap();
        bootstrap.setSchemaService(schemaService);
        bootstrap.activate();

        DidSchema profcred = schemaService.getSchema("hkt-profcred-v1");
        assertNotNull(profcred);
        assertEquals("hkt_profcred_v1", profcred.getVct());
        assertTrue(profcred.getAllowedClaims().contains("qualificationCode"));
        assertTrue(profcred.getAllowedClaims().contains("issuingBody"));
        assertTrue(profcred.getRequiredClaims().contains("qualificationCode"));
        assertTrue(profcred.getRequiredClaims().contains("validUntilYear"));
        assertEquals("string", profcred.getClaimTypes().get("qualificationCode"));
        assertEquals("number", profcred.getClaimTypes().get("validUntilYear"));

        DidSchema residency = schemaService.getSchema("hkt-residency-v1");
        assertNotNull(residency);
        assertEquals("hkt_residency_v1", residency.getVct());
        assertTrue(residency.getAllowedClaims().contains("residencyStatus"));
        assertTrue(residency.getAllowedClaims().contains("jurisdiction"));
        assertTrue(residency.getRequiredClaims().contains("residencyStatus"));
    }

    @Test
    void activationIsIdempotent() {
        CredentialSchemaServiceImpl schemaService = new CredentialSchemaServiceImpl();
        schemaService.setPersistenceService(MockPersistence.create());
        Phase4SchemaBootstrap bootstrap = new Phase4SchemaBootstrap();
        bootstrap.setSchemaService(schemaService);
        bootstrap.activate();
        bootstrap.activate();

        assertEquals(2, schemaService.getSchemas(null).size());
        assertEquals("hkt_profcred_v1", schemaService.getSchema("hkt-profcred-v1").getVct());
    }

    @Test
    void whitelistRejectsRawPii() {
        CredentialSchemaServiceImpl schemaService = new CredentialSchemaServiceImpl();
        schemaService.setPersistenceService(MockPersistence.create());
        Phase4SchemaBootstrap bootstrap = new Phase4SchemaBootstrap();
        bootstrap.setSchemaService(schemaService);
        bootstrap.activate();

        java.util.Map<String, Object> claims = new java.util.HashMap<>();
        claims.put("qualificationCode", "CIV-STRUCT-3");
        claims.put("issuingBody", "HKIE");
        claims.put("validUntilYear", 2030);
        claims.put("idCardNumber", "A123456(7)"); // raw PII — not whitelisted
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> schemaService.validateClaims(schemaService.getSchema("hkt-profcred-v1"), claims));
    }
}
