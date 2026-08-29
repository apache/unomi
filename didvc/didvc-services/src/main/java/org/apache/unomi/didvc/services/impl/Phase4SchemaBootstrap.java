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
 * Phase 4 (People flow) schema bootstrap: registers the professional
 * qualification ({@code hkt_profcred_v1}) and residency
 * ({@code hkt_residency_v1}) credential schemas if they are not already
 * present, so professional-body issuer tenants can start issuing on
 * activation. Claim whitelists follow the data-minimization discipline:
 * nothing outside the listed claims can enter a credential payload.
 */
@Component(service = Phase4SchemaBootstrap.class, immediate = true)
public class Phase4SchemaBootstrap {

    private static final Logger LOGGER = LoggerFactory.getLogger(Phase4SchemaBootstrap.class);

    @Reference
    private CredentialSchemaService schemaService;

    public void setSchemaService(CredentialSchemaService schemaService) {
        this.schemaService = schemaService;
    }

    @Activate
    public void activate() {
        ensureProfcredSchema();
        ensureResidencySchema();
    }

    private void ensureProfcredSchema() {
        if (schemaService.getSchema("hkt-profcred-v1") != null) {
            return;
        }
        DidSchema schema = new DidSchema("hkt-profcred-v1");
        schema.setName("Professional qualification credential");
        schema.setVct("hkt_profcred_v1");
        schema.setDescription("A professional-body issued qualification credential for the "
                + "People flow (FR-P1). Coded references (qualificationCode, issuingBody) "
                + "instead of free-text transcripts; no holder PII beyond the pairwise sub.");
        schema.setAllowedClaims(new HashSet<>(Arrays.asList(
                "qualificationCode", "issuingBody", "gradeLevel", "validUntilYear", "registrationRegion")));
        schema.setRequiredClaims(new HashSet<>(Arrays.asList(
                "qualificationCode", "issuingBody", "validUntilYear")));
        Map<String, String> claimTypes = new LinkedHashMap<>();
        claimTypes.put("qualificationCode", "string");
        claimTypes.put("issuingBody", "string");
        claimTypes.put("gradeLevel", "string");
        claimTypes.put("validUntilYear", "number");
        claimTypes.put("registrationRegion", "string");
        schema.setClaimTypes(claimTypes);
        schema.setScope("didvc");
        schemaService.saveSchema(schema);
        LOGGER.info("Bootstrapped professional qualification schema hkt-profcred-v1 (vct={})", schema.getVct());
    }

    private void ensureResidencySchema() {
        if (schemaService.getSchema("hkt-residency-v1") != null) {
            return;
        }
        DidSchema schema = new DidSchema("hkt-residency-v1");
        schema.setName("Residency status credential");
        schema.setVct("hkt_residency_v1");
        schema.setDescription("Residency status credential for the People flow: statuses as a "
                + "controlled vocabulary (e.g. permanent-resident, valid-work-visa) — never "
                + "visa numbers or identity-document copies.");
        schema.setAllowedClaims(new HashSet<>(Arrays.asList(
                "residencyStatus", "jurisdiction", "validUntil")));
        schema.setRequiredClaims(new HashSet<>(Arrays.asList(
                "residencyStatus", "jurisdiction", "validUntil")));
        Map<String, String> claimTypes = new LinkedHashMap<>();
        claimTypes.put("residencyStatus", "string");
        claimTypes.put("jurisdiction", "string");
        claimTypes.put("validUntil", "string");
        schema.setClaimTypes(claimTypes);
        schema.setScope("didvc");
        schemaService.saveSchema(schema);
        LOGGER.info("Bootstrapped residency schema hkt-residency-v1 (vct={})", schema.getVct());
    }
}
