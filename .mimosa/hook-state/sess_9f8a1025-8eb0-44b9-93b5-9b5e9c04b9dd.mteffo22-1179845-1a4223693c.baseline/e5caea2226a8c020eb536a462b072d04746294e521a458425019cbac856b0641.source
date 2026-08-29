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

package org.apache.unomi.didvc.edge.agent;

import org.apache.unomi.didvc.audit.AuditLogService;
import org.apache.unomi.didvc.edge.m2m.BearerCredentialVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent-plane gateway admission (FR-ID6): registers bound agents from
 * {@code hkt_agent_binding_v1} credentials and enforces admission per
 * call — the check re-verifies the binding credential live, so a
 * revoked/expired/untrusted binding is rejected at the next verified
 * call ("pseudonymous kill switch" semantics), and an unbound agent is
 * never admitted. Every admission decision appends an audit record.
 */
@RestController
public class AgentAdmissionController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentAdmissionController.class);

    private static final String AGENT_BINDING_VCT = "hkt_agent_binding_v1";

    private final BearerCredentialVerifier verifier;
    private final AuditLogService auditLogService;
    /** tenant → agentPubKeyHash → the binding credential presented at admission. */
    private final Map<String, Map<String, String>> admissions = new ConcurrentHashMap<>();

    public AgentAdmissionController(BearerCredentialVerifier verifier, AuditLogService auditLogService) {
        this.verifier = verifier;
        this.auditLogService = auditLogService;
    }

    /**
     * Admission request body.
     */
    public static class AdmitRequest {
        private String credential;

        public String getCredential() {
            return credential;
        }

        public void setCredential(String credential) {
            this.credential = credential;
        }
    }

    /**
     * Registers a bound agent from its binding credential.
     */
    @PostMapping("/{tenantId}/agents/admit")
    public Map<String, Object> admit(@PathVariable("tenantId") String tenantId,
                                     @RequestBody AdmitRequest request) {
        if (request == null || request.getCredential() == null || request.getCredential().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "credential is required");
        }
        BearerCredentialVerifier.Outcome outcome =
                verifier.verify(tenantId, request.getCredential(), true);
        if (!outcome.isValid()) {
            audit(tenantId, null, "admission-rejected: " + outcome.getReason());
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("admitted", false);
            response.put("reason", outcome.getReason());
            return response;
        }
        if (!AGENT_BINDING_VCT.equals(outcome.getVct())) {
            String reason = "credential is not an agent binding (vct=" + outcome.getVct() + ")";
            audit(tenantId, null, "admission-rejected: " + reason);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("admitted", false);
            response.put("reason", reason);
            return response;
        }
        Object agentPubKeyHash = outcome.getClaims().get("agentPubKeyHash");
        if (agentPubKeyHash == null || agentPubKeyHash.toString().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "binding credential carries no agentPubKeyHash");
        }
        admissions.computeIfAbsent(tenantId, t -> new ConcurrentHashMap<>())
                .put(agentPubKeyHash.toString(), request.getCredential());
        audit(tenantId, agentPubKeyHash.toString(), "admitted");
        LOGGER.info("Admitted bound agent {} for tenant {}", agentPubKeyHash, tenantId);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("admitted", true);
        response.put("agentPubKeyHash", agentPubKeyHash.toString());
        response.put("principalBindingLevel", outcome.getClaims().get("principalBindingLevel"));
        return response;
    }

    /**
     * The per-call admission check (the gateway's verified-call gate):
     * re-verifies the registered binding live — revocation takes effect
     * at this next check.
     */
    @GetMapping("/{tenantId}/agents/admission/{agentPubKeyHash}")
    public Map<String, Object> checkAdmission(@PathVariable("tenantId") String tenantId,
                                              @PathVariable("agentPubKeyHash") String agentPubKeyHash) {
        Map<String, String> tenantAdmissions = admissions.get(tenantId);
        String credential = tenantAdmissions == null ? null : tenantAdmissions.get(agentPubKeyHash);
        if (credential == null) {
            audit(tenantId, agentPubKeyHash, "admission-check: unbound");
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("admitted", false);
            response.put("reason", "agent is not bound");
            return response;
        }
        BearerCredentialVerifier.Outcome outcome = verifier.verify(tenantId, credential, false);
        if (!outcome.isValid()) {
            // Kill-switch semantics: drop the admission; the agent is
            // rejected at this verified call and every later one
            tenantAdmissions.remove(agentPubKeyHash);
            audit(tenantId, agentPubKeyHash, "admission-check: revoked (" + outcome.getReason() + ")");
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("admitted", false);
            response.put("reason", outcome.getReason());
            return response;
        }
        audit(tenantId, agentPubKeyHash, "admission-check: admitted");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("admitted", true);
        return response;
    }

    private void audit(String tenantId, String agentPubKeyHash, String outcome) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("agentPubKeyHash", agentPubKeyHash);
            payload.put("outcome", outcome);
            auditLogService.append("didvcAgentAdmission", tenantId,
                    agentPubKeyHash == null ? "unknown-agent" : agentPubKeyHash,
                    new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload));
        } catch (Exception e) {
            LOGGER.warn("Agent admission audit append failed for tenant {}", tenantId, e);
        }
    }
}
