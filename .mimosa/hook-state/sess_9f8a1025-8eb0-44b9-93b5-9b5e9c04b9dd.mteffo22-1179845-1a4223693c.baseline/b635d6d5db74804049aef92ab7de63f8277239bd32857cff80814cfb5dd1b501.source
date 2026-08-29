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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.unomi.didvc.audit.AuditLogService;
import org.apache.unomi.didvc.audit.AuditRecord;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Renders GBA SCC (Standard Contract) filing exports from the immutable
 * audit log (FR-D4). For a data-flow counterparty (the importer), the
 * exporter assembles the verification records of the window — when each
 * transfer attestation was verified, which credential type, with what
 * outcome — plus the personal-data-element categories the counterparty
 * requested. The export carries claim type names only; claim values and
 * any other subject data never leave the audit log, so the filing
 * satisfies the bilateral-filing obligation without transferring
 * personal data.
 */
public class SccFilingExporter {

    private static final String DIDVP_VERIFIED = "didvpVerified";

    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SccFilingExporter(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    /**
     * Builds the filing export for a counterparty over a time window.
     *
     * @param importerTenantId  the data-importing counterparty (verifier tenant)
     * @param contractReference the SCC registration/reference number (optional)
     * @param purpose           the purpose scope of the transfers (optional)
     * @param fromMillis        window start (inclusive)
     * @param toMillis          window end (inclusive)
     * @return the filing export
     */
    public SccFilingExport export(String importerTenantId, String contractReference, String purpose,
                                  long fromMillis, long toMillis) {
        SccFilingExport export = new SccFilingExport();
        export.setFilingDate(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        export.setImporter(importerTenantId);
        export.setContractReference(contractReference);
        export.setPurpose(purpose);

        Set<String> dataElements = new LinkedHashSet<>();
        List<SccFilingExport.VerificationRecord> records = new ArrayList<>();
        String exporter = null;
        for (AuditRecord record : auditLogService.readAll()) {
            if (!DIDVP_VERIFIED.equals(record.getEventType())) {
                continue;
            }
            if (!importerTenantId.equals(record.getActor())) {
                continue;
            }
            if (record.getCreatedAt() < fromMillis || record.getCreatedAt() > toMillis) {
                continue;
            }
            JsonNode payload = parsePayload(record.getPayload());
            if (payload == null) {
                continue;
            }
            String issuer = payload.path("issuer").asText(null);
            String vct = payload.path("vct").asText(null);
            if (exporter == null) {
                exporter = issuer;
            }
            SccFilingExport.VerificationRecord verification = new SccFilingExport.VerificationRecord();
            verification.setVerificationDate(DateTimeFormatter.ISO_INSTANT
                    .format(Instant.ofEpochMilli(record.getCreatedAt())));
            verification.setCredentialType(vct);
            verification.setOutcome("verified");
            records.add(verification);

            JsonNode claims = payload.get("claims");
            if (claims != null && claims.isObject()) {
                for (Iterator<String> it = claims.fieldNames(); it.hasNext(); ) {
                    // categories only — the claim names, never the values
                    dataElements.add(it.next());
                }
            }
        }
        export.setExporter(exporter);
        export.setDataElements(new ArrayList<>(dataElements));
        export.setVerificationRecords(records);
        return export;
    }

    private JsonNode parsePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readTree(payloadJson);
        } catch (Exception e) {
            return null;
        }
    }
}
