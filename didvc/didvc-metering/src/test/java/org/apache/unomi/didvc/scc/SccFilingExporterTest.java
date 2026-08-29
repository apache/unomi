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

package org.apache.unomi.didvc.scc;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.unomi.didvc.audit.AuditLogService;
import org.apache.unomi.didvc.audit.InMemoryAuditLogStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GBA SCC filing exports: the template field set, the verification
 * records per counterparty and window, and the zero-PII property — the
 * export contains claim type names only, never claim values.
 */
class SccFilingExporterTest {

    private AuditLogService auditLogService;
    private SccFilingExporter exporter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        auditLogService = new AuditLogService(new InMemoryAuditLogStore());
        exporter = new SccFilingExporter(auditLogService);
    }

    private void appendVerification(String actor, String issuer, String vct, Map<String, Object> claims) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("issuer", issuer);
        payload.put("vct", vct);
        payload.put("claims", claims);
        try {
            auditLogService.append("didvpVerified", actor, "didvc:pairwise:holder-1",
                    objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void exportMatchesFilingTemplateFields() {
        appendVerification("mainland-bank", "did:web:issuers.example.hkt:hkt",
                "hkt_licensed_institution_v1",
                Map.of("licenseClass", "bank", "regulated", true, "licenseValidUntil", "2028-12-31"));
        appendVerification("mainland-bank", "did:web:issuers.example.hkt:hkt",
                "hkt_realname_v1", Map.of("realNameVerified", true));

        long now = System.currentTimeMillis();
        SccFilingExport export = exporter.export("mainland-bank", "SCC-2026-001",
                "GBA data-flow compliance attestation", now - 1000, now + 1000);

        // The filing-template field set
        assertEquals("mainland-bank", export.getImporter());
        assertEquals("did:web:issuers.example.hkt:hkt", export.getExporter());
        assertEquals("SCC-2026-001", export.getContractReference());
        assertEquals("GBA data-flow compliance attestation", export.getPurpose());
        assertEquals(2, export.getVerificationRecords().size());
        assertEquals("hkt_licensed_institution_v1",
                export.getVerificationRecords().get(0).getCredentialType());
        assertEquals("verified", export.getVerificationRecords().get(0).getOutcome());

        // Data elements are the claim-type categories only (order is not
        // a contract — compare as a set)
        assertEquals(java.util.Set.of("licenseClass", "regulated", "licenseValidUntil", "realNameVerified"),
                new java.util.LinkedHashSet<>(export.getDataElements()));
    }

    @Test
    void exportCarriesNoClaimValues() throws Exception {
        appendVerification("mainland-bank", "did:web:issuers.example.hkt:hkt",
                "hkt_kyc_v1",
                Map.of("kycLevel", "REMOTE_FULL", "givenName", "Yat", "nationality", "HK"));

        long now = System.currentTimeMillis();
        SccFilingExport export = exporter.export("mainland-bank", null, null, now - 1000, now + 1000);
        String json = objectMapper.writeValueAsString(export);

        // Claim type names appear; the values must not
        assertTrue(json.contains("givenName"));
        assertFalse(json.contains("Yat"));
        assertFalse(json.contains("REMOTE_FULL"));
        assertFalse(json.contains("\"HK\""));
    }

    @Test
    void exportIsScopedToCounterpartyAndWindow() {
        appendVerification("mainland-bank", "did:web:issuers.example.hkt:hkt",
                "hkt_licensed_institution_v1", Map.of("licenseClass", "bank"));
        appendVerification("other-counterparty", "did:web:issuers.example.hkt:hkt",
                "hkt_licensed_institution_v1", Map.of("licenseClass", "insurer"));

        long now = System.currentTimeMillis();
        // Window starts strictly after the appends (records are stamped
        // with millisecond resolution) — nothing falls inside it
        SccFilingExport export = exporter.export("mainland-bank", null, null, now + 1, now + 1000);
        assertTrue(export.getVerificationRecords().isEmpty());

        // Other counterparty's records are not attributed to this importer
        SccFilingExport full = exporter.export("mainland-bank", null, null, 0, now + 1000);
        assertEquals(1, full.getVerificationRecords().size());
        assertEquals(1, full.getDataElements().size());
    }

    @Test
    void nonVerificationEventsAreIgnored() {
        auditLogService.append("didvcIssued", "hkt", "didvc:pairwise:holder-1",
                "{\"issuer\":\"did:web:issuers.example.hkt:hkt\",\"vct\":\"hkt_kyc_v1\"}");
        long now = System.currentTimeMillis();
        SccFilingExport export = exporter.export("hkt", null, null, 0, now + 1000);
        assertTrue(export.getVerificationRecords().isEmpty());
        assertTrue(export.getDataElements().isEmpty());
    }
}
