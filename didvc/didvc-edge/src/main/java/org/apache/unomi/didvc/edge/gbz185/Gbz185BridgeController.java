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

package org.apache.unomi.didvc.edge.gbz185;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.Ed25519Verifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jwt.SignedJWT;
import org.apache.unomi.didvc.audit.AuditLogService;
import org.apache.unomi.didvc.edge.EdgeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * GB/Z 185 interop bridge (FR-ID6): the dual-pipeline's second adapter.
 * Mainland service gateways present GB/Z 185-style linkage VPs — signed
 * JWTs binding an agent identity code to a public key hash under a
 * policy scope. The bridge verifies the signature against the
 * per-tenant trusted-issuer key set, checks the agent identity code
 * shape and expiry, maps the issuer to this tenant's accepted policy
 * scopes, and appends one audit record per call (the accountability
 * surface). The HK-sovereign pipeline stays OID4VP; this bridge is the
 * only path that accepts the mainland format.
 */
@RestController
public class Gbz185BridgeController {

    private static final Logger LOGGER = LoggerFactory.getLogger(Gbz185BridgeController.class);

    /** Agent identity codes: 8-32 alphanumerics (SAMR/CESI code shapes). */
    static final Pattern AGENT_IDENTITY_CODE = Pattern.compile("[0-9A-Za-z]{8,32}");

    private final EdgeProperties properties;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Gbz185BridgeController(EdgeProperties properties, AuditLogService auditLogService) {
        this.properties = properties;
        this.auditLogService = auditLogService;
    }

    /**
     * Linkage-VP verification request.
     */
    public static class LinkageVpRequest {
        private String linkageVp;

        public String getLinkageVp() {
            return linkageVp;
        }

        public void setLinkageVp(String linkageVp) {
            this.linkageVp = linkageVp;
        }
    }

    /**
     * Verifies one linkage VP. Every call — accepted or rejected —
     * produces an audit record.
     */
    @PostMapping("/{tenantId}/gbz185/verify")
    public Map<String, Object> verify(@PathVariable("tenantId") String tenantId,
                                      @RequestBody LinkageVpRequest request) {
        if (request == null || request.getLinkageVp() == null || request.getLinkageVp().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "linkageVp is required");
        }
        Map<String, Object> result = verifyLinkageVp(tenantId, request.getLinkageVp());
        audit(tenantId, request.getLinkageVp(), result);
        return result;
    }

    private Map<String, Object> verifyLinkageVp(String tenantId, String linkageVp) {
        SignedJWT jwt;
        Map<String, Object> claims;
        try {
            jwt = SignedJWT.parse(linkageVp);
            claims = jwt.getJWTClaimsSet().toJSONObject();
        } catch (Exception e) {
            return invalid("linkage VP is unreadable");
        }
        String issuer = (String) claims.get("iss");
        String agentIdentityCode = String.valueOf(claims.get("agentIdentityCode"));
        String policyScope = (String) claims.get("policyScope");

        // Signature against the per-tenant trusted-issuer key set
        String jwkJson = properties.getGbz185IssuerJwks().get(issuer);
        if (jwkJson == null) {
            return invalid("issuer is not in the GB/Z 185 trusted key set");
        }
        try {
            JWK jwk = JWK.parse(jwkJson);
            JWSVerifier verifier = jwk instanceof OctetKeyPair
                    ? new Ed25519Verifier((OctetKeyPair) jwk)
                    : new ECDSAVerifier((ECKey) jwk);
            if (!jwt.verify(verifier)) {
                return invalid("linkage VP signature is invalid");
            }
        } catch (Exception e) {
            return invalid("linkage VP signature check failed");
        }
        // Expiry
        Number exp = (Number) claims.get("exp");
        if (exp == null || System.currentTimeMillis() / 1000 >= exp.longValue()) {
            return invalid("linkage VP has expired");
        }
        // Agent identity code shape
        if (agentIdentityCode == null || !AGENT_IDENTITY_CODE.matcher(agentIdentityCode).matches()) {
            return invalid("agent identity code is malformed");
        }
        // Per-tenant policy mapping: issuer → accepted scopes
        List<String> allowedScopes = properties.gbz185AllowedScopes(tenantId, issuer);
        if (allowedScopes == null) {
            return invalid("issuer is not mapped for this tenant");
        }
        if (policyScope == null || (!allowedScopes.isEmpty() && !allowedScopes.contains(policyScope))) {
            return invalid("policy scope is not accepted for this issuer");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("valid", true);
        result.put("issuer", issuer);
        result.put("agentIdentityCode", agentIdentityCode);
        result.put("agentPubKeyHash", claims.get("agentPubKeyHash"));
        result.put("policyScope", policyScope);
        return result;
    }

    private static Map<String, Object> invalid(String reason) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("valid", false);
        result.put("reason", reason);
        return result;
    }

    private void audit(String tenantId, String linkageVp, Map<String, Object> result) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>(result);
            payload.put("vpDigest", org.apache.unomi.didvc.sdjwt.SdJwtDigest.hashOfSdJwt(linkageVp));
            auditLogService.append("didvcGbz185Verified", tenantId,
                    String.valueOf(result.get("agentIdentityCode")),
                    objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            LOGGER.warn("GB/Z 185 audit append failed for tenant {}", tenantId, e);
        }
    }
}
