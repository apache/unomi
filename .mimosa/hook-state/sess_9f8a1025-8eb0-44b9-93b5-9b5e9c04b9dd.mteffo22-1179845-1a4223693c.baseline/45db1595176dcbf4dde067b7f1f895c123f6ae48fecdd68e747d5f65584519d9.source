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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 5 schema bootstrap: the KYB licensed-institution and real-name
 * attestation schemas, with the acceptance criterion that validation
 * rejects embedded registry data (the whitelist is the enforcement
 * point).
 */
class Phase5SchemaBootstrapTest {

    private CredentialSchemaService bootstrap() {
        CredentialSchemaServiceImpl schemaService = new CredentialSchemaServiceImpl();
        schemaService.setPersistenceService(MockPersistence.create());
        Phase5SchemaBootstrap bootstrap = new Phase5SchemaBootstrap();
        bootstrap.setSchemaService(schemaService);
        bootstrap.activate();
        return schemaService;
    }

    @Test
    void registersBothSchemasOnActivation() {
        CredentialSchemaService schemaService = bootstrap();

        DidSchema licensed = schemaService.getSchema("hkt-licensed-institution-v1");
        assertNotNull(licensed);
        assertEquals("hkt_licensed_institution_v1", licensed.getVct());
        assertTrue(licensed.getAllowedClaims().contains("licenseClass"));
        assertTrue(licensed.getAllowedClaims().contains("regulated"));
        assertTrue(licensed.getAllowedClaims().contains("licenseValidUntil"));
        assertTrue(licensed.getRequiredClaims().containsAll(
                licensed.getAllowedClaims()));
        assertEquals("boolean", licensed.getClaimTypes().get("regulated"));

        DidSchema realname = schemaService.getSchema("hkt-realname-v1");
        assertNotNull(realname);
        assertEquals("hkt_realname_v1", realname.getVct());
        assertEquals(1, realname.getAllowedClaims().size());
        assertTrue(realname.getAllowedClaims().contains("realNameVerified"));
    }

    @Test
    void activationIsIdempotent() {
        CredentialSchemaService schemaService = bootstrap();
        Phase5SchemaBootstrap bootstrap = new Phase5SchemaBootstrap();
        bootstrap.setSchemaService(schemaService);
        bootstrap.activate();

        assertEquals(2, schemaService.getSchemas(null).size());
    }

    @Test
    void licensedInstitutionSchemaRejectsEmbeddedRegistryData() {
        CredentialSchemaService schemaService = bootstrap();
        DidSchema schema = schemaService.getSchema("hkt-licensed-institution-v1");

        // A Companies-Registry-style dump must not be smugglable through
        Map<String, Object> registryNumber = new HashMap<>();
        registryNumber.put("licenseClass", "bank");
        registryNumber.put("regulated", true);
        registryNumber.put("licenseValidUntil", "2028-12-31");
        registryNumber.put("companyRegistryNumber", "12345678");
        assertThrows(IllegalArgumentException.class,
                () -> schemaService.validateClaims(schema, registryNumber));

        Map<String, Object> directors = new HashMap<>();
        directors.put("licenseClass", "bank");
        directors.put("regulated", true);
        directors.put("licenseValidUntil", "2028-12-31");
        directors.put("directors", java.util.List.of("Director A", "Director B"));
        assertThrows(IllegalArgumentException.class,
                () -> schemaService.validateClaims(schema, directors));

        Map<String, Object> registryExtract = new HashMap<>();
        registryExtract.put("licenseClass", "bank");
        registryExtract.put("regulated", true);
        registryExtract.put("licenseValidUntil", "2028-12-31");
        registryExtract.put("registryExtract", "full-registry-dump");
        assertThrows(IllegalArgumentException.class,
                () -> schemaService.validateClaims(schema, registryExtract));
    }

    @Test
    void realnameSchemaAcceptsOnlyTheBooleanClaim() {
        CredentialSchemaService schemaService = bootstrap();
        DidSchema schema = schemaService.getSchema("hkt-realname-v1");

        Map<String, Object> valid = new HashMap<>();
        valid.put("realNameVerified", true);
        schemaService.validateClaims(schema, valid);

        Map<String, Object> withName = new HashMap<>();
        withName.put("realNameVerified", true);
        withName.put("fullName", "Chan Tai Man");
        assertThrows(IllegalArgumentException.class,
                () -> schemaService.validateClaims(schema, withName));

        Map<String, Object> withDocument = new HashMap<>();
        withDocument.put("realNameVerified", true);
        withDocument.put("identityDocumentNumber", "A123456(7)");
        assertThrows(IllegalArgumentException.class,
                () -> schemaService.validateClaims(schema, withDocument));
    }
}
