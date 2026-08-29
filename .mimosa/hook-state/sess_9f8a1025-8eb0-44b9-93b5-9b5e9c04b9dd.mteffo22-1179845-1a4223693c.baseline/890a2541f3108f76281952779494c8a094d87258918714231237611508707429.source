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
 * Phase 6 (Logistics flow) schema bootstrap: registers the cargo
 * compliance attestation ({@code hkt_cargo_v1}) and the corporate
 * identity credential ({@code hkt_corporate_v1}) used by port/customs
 * community partners. Minimization follows the same whitelist
 * discipline: HS-code classes and customs/AEO statuses instead of
 * consignment data, and hashed registration numbers instead of registry
 * extracts.
 */
@Component(service = Phase6SchemaBootstrap.class, immediate = true)
public class Phase6SchemaBootstrap {

    private static final Logger LOGGER = LoggerFactory.getLogger(Phase6SchemaBootstrap.class);

    @Reference
    private CredentialSchemaService schemaService;

    public void setSchemaService(CredentialSchemaService schemaService) {
        this.schemaService = schemaService;
    }

    @Activate
    public void activate() {
        ensureCargoSchema();
        ensureCorporateSchema();
    }

    private void ensureCargoSchema() {
        if (schemaService.getSchema("hkt-cargo-v1") != null) {
            return;
        }
        DidSchema schema = new DidSchema("hkt-cargo-v1");
        schema.setName("Cargo compliance attestation");
        schema.setVct("hkt_cargo_v1");
        schema.setDescription("Cargo/corporate compliance attestation for the Logistics flow: "
                + "HS-code class, customs status and AEO status per consignment — categories "
                + "and statuses, never consignment data or commercial documents.");
        schema.setAllowedClaims(new HashSet<>(Arrays.asList(
                "hsCodeClass", "customsStatus", "aeoStatus", "originAttestation")));
        schema.setRequiredClaims(new HashSet<>(Arrays.asList("hsCodeClass", "customsStatus")));
        Map<String, String> claimTypes = new LinkedHashMap<>();
        claimTypes.put("hsCodeClass", "string");
        claimTypes.put("customsStatus", "string");
        claimTypes.put("aeoStatus", "string");
        claimTypes.put("originAttestation", "string");
        schema.setClaimTypes(claimTypes);
        schema.setScope("didvc");
        schemaService.saveSchema(schema);
        LOGGER.info("Bootstrapped cargo schema hkt-cargo-v1 (vct={})", schema.getVct());
    }

    private void ensureCorporateSchema() {
        if (schemaService.getSchema("hkt-corporate-v1") != null) {
            return;
        }
        DidSchema schema = new DidSchema("hkt-corporate-v1");
        schema.setVct("hkt_corporate_v1");
        schema.setName("Corporate identity credential");
        schema.setDescription("Counterparty corporate identity for the Logistics flow: hashed "
                + "registration number, jurisdiction, licensed activities and LEI — the "
                + "verification surface for trade counterparties, not a registry extract.");
        schema.setAllowedClaims(new HashSet<>(Arrays.asList(
                "registrationNoHash", "jurisdiction", "licensedActivities", "lei")));
        schema.setRequiredClaims(new HashSet<>(Arrays.asList(
                "registrationNoHash", "jurisdiction", "licensedActivities")));
        Map<String, String> claimTypes = new LinkedHashMap<>();
        claimTypes.put("registrationNoHash", "string");
        claimTypes.put("jurisdiction", "string");
        claimTypes.put("licensedActivities", "array");
        claimTypes.put("lei", "string");
        schema.setClaimTypes(claimTypes);
        schema.setScope("didvc");
        schemaService.saveSchema(schema);
        LOGGER.info("Bootstrapped corporate schema hkt-corporate-v1 (vct={})", schema.getVct());
    }
}
