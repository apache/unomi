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

import com.nimbusds.jose.jwk.JWK;
import org.apache.unomi.didvc.edge.platform.PlatformApi;
import org.apache.unomi.didvc.sdjwt.SdJwtParser;
import org.apache.unomi.didvc.sdjwt.SdJwtPresentation;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bearer-credential verifier for machine-to-machine callers (FR-L2):
 * validates the credential's issuer signature, time validity, revocation
 * status and trust-registry entry, and returns the claim-level outcome
 * — {@code valid}, the credential type, the expiry, and per-claim
 * disclosed values only when the caller asked for them. M2M holders are
 * services presenting bearer credentials (no wallet key binding — that
 * is the OID4VP path); verification is fully stateless so instances
 * scale horizontally behind a load balancer.
 */
@Component
public class BearerCredentialVerifier {

    private final PlatformApi platformApi;

    public BearerCredentialVerifier(PlatformApi platformApi) {
        this.platformApi = platformApi;
    }

    /**
     * Verification outcome; {@code reason} is set when invalid, and the
     * disclosed/always-disclosed claim maps are present only when
     * {@code includeClaims} was requested.
     */
    public static class Outcome {
        private final boolean valid;
        private final String reason;
        private final String issuer;
        private final String vct;
        private final Long expiresAt;
        private final Map<String, Object> claims;

        private Outcome(boolean valid, String reason, String issuer, String vct, Long expiresAt,
                        Map<String, Object> claims) {
            this.valid = valid;
            this.reason = reason;
            this.issuer = issuer;
            this.vct = vct;
            this.expiresAt = expiresAt;
            this.claims = claims;
        }

        public boolean isValid() {
            return valid;
        }

        public String getReason() {
            return reason;
        }

        public String getIssuer() {
            return issuer;
        }

        public String getVct() {
            return vct;
        }

        public Long getExpiresAt() {
            return expiresAt;
        }

        public Map<String, Object> getClaims() {
            return claims;
        }

        public static Outcome invalid(String reason) {
            return new Outcome(false, reason, null, null, null, null);
        }

        /**
         * A positive outcome without claim detail (response rendering
         * only — the audit trail carries the specifics).
         */
        public static Outcome valid() {
            return new Outcome(true, null, null, null, null, null);
        }
    }

    /**
     * Verifies one bearer credential end to end.
     *
     * @param tenantId       the relying tenant (the M2M caller's tenant)
     * @param credential     the SD-JWT credential as issued
     * @param includeClaims  whether the response should carry disclosed
     *                       claim values (claim-level/boolean otherwise)
     * @return the outcome
     */
    public Outcome verify(String tenantId, String credential, boolean includeClaims) {
        SdJwtPresentation presentation;
        try {
            presentation = new SdJwtParser().parse(credential);
        } catch (Exception e) {
            return Outcome.invalid("credential is unreadable: " + e.getMessage());
        }
        Map<String, Object> claims = presentation.getClaims();
        String issuer = (String) claims.get("iss");
        String vct = (String) claims.get("vct");
        String kid = presentation.getCredential().getHeader().getKeyID();
        long now = System.currentTimeMillis() / 1000;

        // Issuer signature
        JWK issuerKey = platformApi.resolveIssuerKey(issuer, kid);
        if (issuerKey == null) {
            return Outcome.invalid("issuer key is unknown");
        }
        try {
            if (!presentation.verifySignature(issuerKey)) {
                return Outcome.invalid("credential signature is invalid");
            }
        } catch (Exception e) {
            return Outcome.invalid("credential signature check failed: " + e.getMessage());
        }
        // Time validity
        Number exp = (Number) claims.get("exp");
        Number nbf = (Number) claims.get("nbf");
        if (exp != null && now >= exp.longValue()) {
            return Outcome.invalid("credential has expired");
        }
        if (nbf != null && now < nbf.longValue()) {
            return Outcome.invalid("credential is not yet valid");
        }
        // Revocation (checked per verification — next-check semantics)
        Object status = claims.get("status");
        if (status instanceof Map && ((Map<?, ?>) status).get("status_list") instanceof Map) {
            Map<?, ?> statusList = (Map<?, ?>) ((Map<?, ?>) status).get("status_list");
            String uri = (String) statusList.get("uri");
            Number idx = (Number) statusList.get("idx");
            if (uri != null && idx != null && platformApi.isStatusRevoked(tenantId, uri, idx.intValue())) {
                return Outcome.invalid("credential is revoked");
            }
        }
        // Trust registry
        if (!platformApi.isTrusted(tenantId, issuer, vct)) {
            return Outcome.invalid("issuer is not trusted by this verifier");
        }

        Map<String, Object> disclosed = null;
        if (includeClaims) {
            disclosed = new LinkedHashMap<>();
            disclosed.putAll(presentation.getDisclosedClaims());
            for (Map.Entry<String, Object> entry : claims.entrySet()) {
                String key = entry.getKey();
                if (!key.equals("vct") && !key.equals("iss") && !key.equals("sub")
                        && !key.equals("iat") && !key.equals("nbf") && !key.equals("exp")
                        && !key.equals("status") && !key.equals("cnf")
                        && !key.equals("_sd") && !key.equals("_sd_alg")) {
                    disclosed.putIfAbsent(key, entry.getValue());
                }
            }
        }
        return new Outcome(true, null, issuer, vct, exp == null ? null : exp.longValue(), disclosed);
    }

    /**
     * Renders an outcome as the claim-level M2M response map (booleans
     * and identifiers only unless claim values were requested).
     */
    public Map<String, Object> toResponse(Outcome outcome) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("valid", outcome.isValid());
        if (outcome.getVct() != null) {
            response.put("vct", outcome.getVct());
        }
        if (outcome.getExpiresAt() != null) {
            response.put("expiresAt", outcome.getExpiresAt());
        }
        if (!outcome.isValid()) {
            response.put("reason", outcome.getReason());
        }
        if (outcome.getClaims() != null) {
            response.put("claims", outcome.getClaims());
        }
        return response;
    }

    /**
     * Extracts the credential subject reference for audit records — the
     * pairwise {@code sub}, never a raw identifier.
     */
    public String subjectOf(String credential) {
        try {
            SdJwtPresentation presentation = new SdJwtParser().parse(credential);
            return (String) presentation.getClaims().get("sub");
        } catch (Exception e) {
            return null;
        }
    }
}
