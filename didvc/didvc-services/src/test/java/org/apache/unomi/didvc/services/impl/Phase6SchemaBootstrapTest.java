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

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Phase 6 schema bootstrap: the cargo and corporate logistics schemas,
 * with the whitelist rejecting consignment data and registry extracts.
 */
class Phase6SchemaBootstrapTest {

    private CredentialSchemaService bootstrap() {
        CredentialSchemaServiceImpl schemaService = new CredentialSchemaServiceImpl();
        schemaService.setPersistenceService(MockPersistence.create());
        Phase6SchemaBootstrap bootstrap = new Phase6SchemaBootstrap();
        bootstrap.setSchemaService(schemaService);
        bootstrap.activate();
        return schemaService;
    }

    @Test
    void registersBothSchemasOnActivation() {
        CredentialSchemaService schemaService = bootstrap();

        DidSchema cargo = schemaService.getSchema("hkt-cargo-v1");
        assertNotNull(cargo);
        assertEquals("hkt_cargo_v1", cargo.getVct());
        assertEquals(java.util.Set.of("hsCodeClass", "customsStatus", "aeoStatus", "originAttestation"),
                cargo.getAllowedClaims());

        DidSchema corporate = schemaService.getSchema("hkt-corporate-v1");
        assertNotNull(corporate);
        assertEquals("hkt_corporate_v1", corporate.getVct());
        assertEquals(java.util.Set.of("registrationNoHash", "jurisdiction", "licensedActivities", "lei"),
                corporate.getAllowedClaims());
        assertEquals("array", corporate.getClaimTypes().get("licensedActivities"));
    }

    @Test
    void cargoSchemaRejectsConsignmentData() {
        CredentialSchemaService schemaService = bootstrap();
        DidSchema schema = schemaService.getSchema("hkt-cargo-v1");

        Map<String, Object> claims = new HashMap<>();
        claims.put("hsCodeClass", "8542");
        claims.put("customsStatus", "cleared");
        claims.put("invoiceLines", java.util.List.of("10x widget @ 4.50 HKD"));
        assertThrows(IllegalArgumentException.class,
                () -> schemaService.validateClaims(schema, claims));
    }

    @Test
    void corporateSchemaRejectsRegistryExtracts() {
        CredentialSchemaService schemaService = bootstrap();
        DidSchema schema = schemaService.getSchema("hkt-corporate-v1");

        Map<String, Object> claims = new HashMap<>();
        claims.put("registrationNoHash", "sha256:1a2b3c");
        claims.put("jurisdiction", "HK");
        claims.put("licensedActivities", java.util.List.of("freight-forwarding"));
        claims.put("registrationNumber", "12345678");
        assertThrows(IllegalArgumentException.class,
                () -> schemaService.validateClaims(schema, claims));
    }
}
