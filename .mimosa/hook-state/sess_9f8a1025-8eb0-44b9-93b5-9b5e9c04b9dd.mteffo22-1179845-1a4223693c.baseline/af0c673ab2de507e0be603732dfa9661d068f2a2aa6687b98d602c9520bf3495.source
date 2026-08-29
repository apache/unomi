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
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Phase 5 (Data flow) schema bootstrap: registers the KYB "licensed HK
 * institution" attestation ({@code hkt_licensed_institution_v1}) and the
 * "real-name verified" attestation ({@code hkt_realname_v1}). Both are
 * minimal-claim credentials for GBA data-flow counterparties (FR-D1/D2):
 * a KYB counterpart verifies license class and validity — never a
 * Companies-Registry extract — and the real-name credential is a single
 * boolean claim, the strongest minimization available. The allowed-claim
 * whitelist is the enforcement point: anything not listed (registry
 * dumps, director lists, document numbers) is rejected at issuance.
 */
@Component(service = Phase5SchemaBootstrap.class, immediate = true)
public class Phase5SchemaBootstrap {

    private static final Logger LOGGER = LoggerFactory.getLogger(Phase5SchemaBootstrap.class);

    @Reference
    private CredentialSchemaService schemaService;

    public void setSchemaService(CredentialSchemaService schemaService) {
        this.schemaService = schemaService;
    }

    @Activate
    public void activate() {
        ensureLicensedInstitutionSchema();
        ensureRealnameSchema();
    }

    private void ensureLicensedInstitutionSchema() {
        if (schemaService.getSchema("hkt-licensed-institution-v1") != null) {
            return;
        }
        DidSchema schema = new DidSchema("hkt-licensed-institution-v1");
        schema.setName("Licensed HK institution attestation");
        schema.setVct("hkt_licensed_institution_v1");
        schema.setDescription("KYB attestation for GBA data-flow counterparties: the holder "
                + "is a licensed HK institution (license class and validity window). "
                + "Minimization: counterparties verify license class and validity, never "
                + "registry extracts — anything outside the whitelist is rejected.");
        schema.setAllowedClaims(new HashSet<>(Arrays.asList(
                "licenseClass", "regulated", "licenseValidUntil")));
        schema.setRequiredClaims(new HashSet<>(Arrays.asList(
                "licenseClass", "regulated", "licenseValidUntil")));
        Map<String, String> claimTypes = new LinkedHashMap<>();
        claimTypes.put("licenseClass", "string");
        claimTypes.put("regulated", "boolean");
        claimTypes.put("licenseValidUntil", "string");
        schema.setClaimTypes(claimTypes);
        schema.setScope("didvc");
        schemaService.saveSchema(schema);
        LOGGER.info("Bootstrapped licensed-institution schema hkt-licensed-institution-v1 (vct={})",
                schema.getVct());
    }

    private void ensureRealnameSchema() {
        if (schemaService.getSchema("hkt-realname-v1") != null) {
            return;
        }
        DidSchema schema = new DidSchema("hkt-realname-v1");
        schema.setName("Real-name verified attestation");
        schema.setVct("hkt_realname_v1");
        schema.setDescription("Real-name verification attestation for GBA data-flow "
                + "counterparties: a single boolean claim — the strongest minimization "
                + "available. No names, no document references, no registry data.");
        schema.setAllowedClaims(new HashSet<>(Arrays.asList("realNameVerified")));
        schema.setRequiredClaims(new HashSet<>(Arrays.asList("realNameVerified")));
        Map<String, String> claimTypes = new LinkedHashMap<>();
        claimTypes.put("realNameVerified", "boolean");
        schema.setClaimTypes(claimTypes);
        schema.setScope("didvc");
        schemaService.saveSchema(schema);
        LOGGER.info("Bootstrapped real-name schema hkt-realname-v1 (vct={})", schema.getVct());
    }
}
