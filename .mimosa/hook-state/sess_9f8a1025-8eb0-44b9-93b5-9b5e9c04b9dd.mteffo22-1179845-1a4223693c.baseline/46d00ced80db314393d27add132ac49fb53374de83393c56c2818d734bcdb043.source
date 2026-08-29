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

package org.apache.unomi.didvc.edge.m2m;

import org.apache.unomi.didvc.audit.AuditLogService;
import org.apache.unomi.didvc.edge.EdgeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Machine-to-machine verification API for logistics counterparties
 * (FR-L2/L3): stateless single and batch bearer-credential verification
 * behind API-key authentication. Production deployments terminate mTLS
 * at the ingress and forward the client certificate identity in
 * {@code X-Client-Cert-Sub}; the API key (provisioned out of band, read
 * from configuration — never committed) is the per-caller credential
 * this endpoint enforces. Every check appends to the immutable audit
 * log; responses are claim-level (booleans, credential type, expiry)
 * unless claim values are explicitly requested.
 */
@RestController
public class M2mVerificationController {

    private static final Logger LOGGER = LoggerFactory.getLogger(M2mVerificationController.class);

    private final EdgeProperties properties;
    private final BearerCredentialVerifier verifier;
    private final AuditLogService auditLogService;

    public M2mVerificationController(EdgeProperties properties, BearerCredentialVerifier verifier,
                                     AuditLogService auditLogService) {
        this.properties = properties;
        this.verifier = verifier;
        this.auditLogService = auditLogService;
    }

    /**
     * Verification request body.
     */
    public static class VerifyRequest {
        private String credential;
        private Boolean includeClaims;

        public String getCredential() {
            return credential;
        }

        public void setCredential(String credential) {
            this.credential = credential;
        }

        /**
         * Whether the response should carry disclosed claim values
         * (claim-level booleans otherwise).
         */
        public Boolean getIncludeClaims() {
            return includeClaims;
        }

        public void setIncludeClaims(Boolean includeClaims) {
            this.includeClaims = includeClaims;
        }
    }

    /**
     * Batch request body: N verification records, each with an optional
     * correlation id (e.g. the manifest line item).
     */
    public static class BatchVerifyRequest {
        private List<BatchEntry> records;

        public List<BatchEntry> getRecords() {
            return records;
        }

        public void setRecords(List<BatchEntry> records) {
            this.records = records;
        }
    }

    /**
     * One batch entry.
     */
    public static class BatchEntry {
        private String id;
        private String credential;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getCredential() {
            return credential;
        }

        public void setCredential(String credential) {
            this.credential = credential;
        }
    }

    /**
     * Verifies one bearer credential. Stateless and nonce-free: the
     * sub-second p95 target relies on no shared mutable state, so
     * instances autoscale behind a load balancer.
     */
    @PostMapping("/{tenantId}/m2m/verify")
    public Map<String, Object> verify(@PathVariable("tenantId") String tenantId,
                                      @RequestHeader(value = "X-Api-Key", required = false) String apiKey,
                                      @RequestBody VerifyRequest request) {
        requireApiKey(apiKey);
        if (request == null || request.getCredential() == null || request.getCredential().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "credential is required");
        }
        BearerCredentialVerifier.Outcome outcome =
                verifier.verify(tenantId, request.getCredential(), Boolean.TRUE.equals(request.getIncludeClaims()));
        audit(tenantId, request.getCredential(), outcome);
        return verifier.toResponse(outcome);
    }

    /**
     * Batch variant (FR-L3): verifies N credentials in one call and
     * returns per-record outcomes keyed by the caller's correlation id.
     */
    @PostMapping("/{tenantId}/m2m/verify-batch")
    public Map<String, Object> verifyBatch(@PathVariable("tenantId") String tenantId,
                                           @RequestHeader(value = "X-Api-Key", required = false) String apiKey,
                                           @RequestBody BatchVerifyRequest request) {
        requireApiKey(apiKey);
        if (request == null || request.getRecords() == null || request.getRecords().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "records are required");
        }
        List<Map<String, Object>> results = new ArrayList<>();
        for (BatchEntry entry : request.getRecords()) {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("id", entry.getId());
            if (entry.getCredential() == null || entry.getCredential().isEmpty()) {
                record.put("valid", false);
                record.put("reason", "credential is missing");
            } else {
                BearerCredentialVerifier.Outcome outcome = verifier.verify(tenantId, entry.getCredential(), false);
                record.putAll(verifier.toResponse(outcome));
                audit(tenantId, entry.getCredential(), outcome);
            }
            results.add(record);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("count", results.size());
        response.put("results", results);
        return response;
    }

    private void requireApiKey(String apiKey) {
        List<String> keys = properties.getM2mApiKeys();
        if (keys == null || keys.isEmpty()) {
            // No M2M keys configured: the endpoint is closed
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "M2M verification is not configured for this edge");
        }
        if (apiKey == null || apiKey.isEmpty() || !keys.contains(apiKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid M2M API key");
        }
    }

    private void audit(String tenantId, String credential, BearerCredentialVerifier.Outcome outcome) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("issuer", outcome.getIssuer());
            payload.put("vct", outcome.getVct());
            payload.put("valid", outcome.isValid());
            if (!outcome.isValid()) {
                payload.put("reason", outcome.getReason());
            }
            auditLogService.append("didvcM2mVerified", tenantId, verifier.subjectOf(credential),
                    new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload));
        } catch (Exception e) {
            LOGGER.warn("M2M audit append failed for tenant {}", tenantId, e);
        }
    }
}
