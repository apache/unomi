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

package org.apache.unomi.didvc.edge.vp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;
import org.apache.unomi.didvc.audit.AuditLogService;
import org.apache.unomi.didvc.edge.EdgeProperties;
import org.apache.unomi.didvc.edge.platform.PlatformApi;
import org.apache.unomi.didvc.edge.store.NonceStore;
import org.apache.unomi.didvc.metering.MeteringService;
import org.apache.unomi.didvc.sdjwt.SdJwtParser;
import org.apache.unomi.didvc.sdjwt.SdJwtPresentation;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OpenID4VP 1.0 verifier: creates signed authorization requests with
 * claim queries and a per-request nonce, and verifies submitted SD-JWT
 * presentations end to end — credential signature (issuer DID resolution),
 * time validity, revocation status, trust registry, and key binding with
 * nonce/audience/replay protection. Every accepted verification is
 * appended to the immutable audit log and billed through the metering
 * service.
 */
@RestController
public class VerificationController {

    private static final Logger LOGGER = LoggerFactory.getLogger(VerificationController.class);
    private static final long REQUEST_TTL_MILLIS = 10 * 60 * 1000L;

    private final EdgeProperties properties;
    private final PlatformApi platformApi;
    private final AuditLogService auditLogService;
    private final MeteringService meteringService;
    private final NonceStore nonceStore;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DcqlQueryParser dcqlQueryParser = new DcqlQueryParser();

    private final Map<String, VpRequestContext> requests = new ConcurrentHashMap<>();

    public VerificationController(EdgeProperties properties, PlatformApi platformApi,
                                  AuditLogService auditLogService, MeteringService meteringService,
                                  NonceStore nonceStore) {
        this.properties = properties;
        this.platformApi = platformApi;
        this.auditLogService = auditLogService;
        this.meteringService = meteringService;
        this.nonceStore = nonceStore;
    }

    /**
     * Authorization request: the verifier asks for specific claims of a
     * credential type and pins a nonce for the presentation.
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class AuthorizeRequest {
        private String clientId;
        private String responseUri;
        private String nonce;
        private Map<String, List<String>> claims;
        private Object dcqlQuery;

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getResponseUri() {
            return responseUri;
        }

        public void setResponseUri(String responseUri) {
            this.responseUri = responseUri;
        }

        public String getNonce() {
            return nonce;
        }

        public void setNonce(String nonce) {
            this.nonce = nonce;
        }

        public Map<String, List<String>> getClaims() {
            return claims;
        }

        public void setClaims(Map<String, List<String>> claims) {
            this.claims = claims;
        }

        /**
         * A DCQL query as raw JSON — the preferred query format. When
         * present it replaces the plain claims map.
         */
        public Object getDcqlQuery() {
            return dcqlQuery;
        }

        public void setDcqlQuery(Object dcqlQuery) {
            this.dcqlQuery = dcqlQuery;
        }
    }

    /**
     * Submitted presentation.
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class VpSubmission {
        private String state;
        private String nonce;
        private String vpToken;

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }

        public String getNonce() {
            return nonce;
        }

        public void setNonce(String nonce) {
            this.nonce = nonce;
        }

        public String getVpToken() {
            return vpToken;
        }

        public void setVpToken(String vpToken) {
            this.vpToken = vpToken;
        }
    }

    @PostMapping("/{tenantId}/vp/authorize")
    public Map<String, Object> authorize(@PathVariable("tenantId") String tenantId,
                                         @RequestBody AuthorizeRequest request) {
        if (request.getClientId() == null || request.getNonce() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "clientId and nonce are required");
        }
        DcqlQueryParser.Query dcql = null;
        if (request.getDcqlQuery() != null) {
            try {
                dcql = dcqlQueryParser.parse(objectMapper.writeValueAsString(request.getDcqlQuery()));
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unreadable dcql_query: " + e.getMessage());
            }
        }
        if ((request.getClaims() == null || request.getClaims().isEmpty()) && dcql == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "claims or dcql_query are required");
        }
        // Issue the nonce into the (possibly shared) nonce store so
        // presentations can only be consumed once, fleet-wide
        nonceStore.issue(tenantId + ":" + request.getNonce(), REQUEST_TTL_MILLIS / 1000);
        String requestId = UUID.randomUUID().toString();
        requests.put(requestId, new VpRequestContext(tenantId, request.getClientId(), request.getResponseUri(),
                request.getNonce(), request.getClaims(), dcql, request.getDcqlQuery(),
                System.currentTimeMillis() + REQUEST_TTL_MILLIS));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("request_uri", properties.getIssuerBaseUrl() + "/" + tenantId + "/vp/request/" + requestId);
        return response;
    }

    @GetMapping("/{tenantId}/vp/request/{requestId}")
    public String requestObject(@PathVariable("tenantId") String tenantId,
                                @PathVariable("requestId") String requestId) {
        VpRequestContext context = requireContext(tenantId, requestId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("client_id", context.clientId);
        payload.put("response_uri", context.responseUri);
        payload.put("nonce", context.nonce);
        if (context.dcqlQueryJson != null) {
            payload.put("dcql_query", context.dcqlQueryJson);
        } else {
            payload.put("claims", context.claims);
        }
        payload.put("iat", System.currentTimeMillis() / 1000);
        payload.put("exp", context.expiresAt / 1000);
        try {
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.HS256)
                    .type(new com.nimbusds.jose.JOSEObjectType("oauth-authz-req+jwt"))
                    .build();
            com.nimbusds.jose.JWSObject jws = new com.nimbusds.jose.JWSObject(header,
                    new Payload(objectMapper.writeValueAsString(payload)));
            jws.sign(new MACSigner(properties.getRequestSigningSecret().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            return jws.serialize();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "failed to sign request object");
        }
    }

    @PostMapping("/{tenantId}/vp/direct_post")
    public Map<String, Object> verifyPresentation(@PathVariable("tenantId") String tenantId,
                                                  @RequestBody VpSubmission submission) {
        VpRequestContext context = requests.remove(submission == null ? null : submission.getState());
        if (context == null || !tenantId.equals(context.tenantId) || context.expiresAt < System.currentTimeMillis()) {
            throw invalid("invalid or expired authorization request state");
        }
        if (!context.nonce.equals(submission.getNonce())) {
            throw invalid("nonce does not match the authorization request");
        }
        // Fleet-wide single-use enforcement: the nonce is consumed here and
        // never accepted again, even if the request context were replayed
        if (!nonceStore.consume(tenantId + ":" + submission.getNonce())) {
            throw invalid("nonce was not issued or has already been consumed");
        }
        if (submission.getVpToken() == null) {
            throw invalid("vp_token is missing");
        }
        try {
            SdJwtPresentation presentation = new SdJwtParser().parse(submission.getVpToken());
            return verifyPresentation(tenantId, context, presentation);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.warn("Presentation rejected: {}", e.getMessage());
            throw invalid(e.getMessage());
        }
    }

    private Map<String, Object> verifyPresentation(String tenantId, VpRequestContext context,
                                                   SdJwtPresentation presentation) throws Exception {
        Map<String, Object> claims = presentation.getClaims();
        String iss = (String) claims.get("iss");
        String vct = (String) claims.get("vct");
        String kid = presentation.getCredential().getHeader().getKeyID();
        long now = System.currentTimeMillis() / 1000;

        // Signature against the issuer's DID document
        com.nimbusds.jose.jwk.JWK issuerKey = platformApi.resolveIssuerKey(iss, kid);
        if (issuerKey == null || !presentation.verifySignature(issuerKey)) {
            throw invalid("credential signature is invalid or issuer key is unknown");
        }
        // Time validity
        Number exp = (Number) claims.get("exp");
        Number nbf = (Number) claims.get("nbf");
        if (exp != null && now >= exp.longValue()) {
            throw invalid("credential has expired");
        }
        if (nbf != null && now < nbf.longValue()) {
            throw invalid("credential is not yet valid");
        }
        // Requested credential type (plain claims map or DCQL query)
        boolean vctRequested = (context.claims != null && context.claims.containsKey(vct))
                || (context.dcql != null && context.dcql.matchesVct(vct));
        if (!vctRequested) {
            throw invalid("credential type " + vct + " was not requested");
        }
        // Revocation status (checked per verification)
        @SuppressWarnings("unchecked")
        Map<String, Object> status = (Map<String, Object>) claims.get("status");
        if (status != null && status.get("status_list") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> statusList = (Map<String, Object>) status.get("status_list");
            String uri = (String) statusList.get("uri");
            Number idx = (Number) statusList.get("idx");
            if (uri != null && idx != null) {
                if (platformApi.isStatusRevoked(tenantId, uri, idx.intValue())) {
                    throw invalid("credential is revoked");
                }
            }
        }
        // Trust registry
        if (!platformApi.isTrusted(tenantId, iss, vct)) {
            throw invalid("issuer is not trusted by this verifier");
        }
        // Key binding: holder possession, sd_hash, nonce, audience, freshness
        presentation.verifyKeyBinding(context.nonce, properties.getIssuerBaseUrl(), now);

        // DCQL claim matching: path resolution and expected values
        if (context.dcql != null) {
            Map<String, Object> combinedClaims = new LinkedHashMap<>();
            combinedClaims.putAll(extractAlwaysDisclosed(claims));
            combinedClaims.putAll(presentation.getDisclosedClaims());
            verifyDcqlClaims(context.dcql, vct, combinedClaims);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("valid", true);
        result.put("issuer", iss);
        result.put("vct", vct);
        result.put("subject", claims.get("sub"));
        result.put("claims", presentation.getDisclosedClaims());
        result.put("alwaysDisclosed", extractAlwaysDisclosed(claims));

        audit(tenantId, iss, vct, (String) claims.get("sub"), result);
        return result;
    }

    private void verifyDcqlClaims(DcqlQueryParser.Query dcql, String vct, Map<String, Object> combinedClaims) {
        for (DcqlQueryParser.CredentialQuery credential : dcql.getCredentials()) {
            if (!vct.equals(credential.getVct())) {
                continue;
            }
            for (DcqlQueryParser.ClaimQuery claim : credential.getClaims()) {
                String claimPath = String.join(".", claim.getPath());
                Object actual = resolvePath(combinedClaims, claim.getPath());
                if (actual == null) {
                    throw invalid("required claim " + claimPath + " is not disclosed");
                }
                if (claim.getValues() != null && !claim.getValues().isEmpty()) {
                    boolean matched = false;
                    for (Object expected : claim.getValues()) {
                        if (valuesEqual(expected, actual)) {
                            matched = true;
                            break;
                        }
                    }
                    if (!matched) {
                        throw invalid("claim " + claimPath + " does not match the expected values");
                    }
                }
            }
        }
    }

    private Object resolvePath(Map<String, Object> claims, List<String> path) {
        Object current = claims;
        for (String segment : path) {
            if (!(current instanceof Map)) {
                return null;
            }
            current = ((Map<?, ?>) current).get(segment);
        }
        return current;
    }

    private boolean valuesEqual(Object expected, Object actual) {
        if (expected == null || actual == null) {
            return expected == actual;
        }
        if (expected instanceof Number && actual instanceof Number) {
            return ((Number) expected).doubleValue() == ((Number) actual).doubleValue();
        }
        return expected.equals(actual);
    }

    private Map<String, Object> extractAlwaysDisclosed(Map<String, Object> claims) {
        Map<String, Object> always = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : claims.entrySet()) {
            String key = entry.getKey();
            if (!key.equals("vct") && !key.equals("iss") && !key.equals("sub")
                    && !key.equals("iat") && !key.equals("nbf") && !key.equals("exp")
                    && !key.equals("status") && !key.equals("cnf")
                    && !key.equals("_sd") && !key.equals("_sd_alg")) {
                always.put(key, entry.getValue());
            }
        }
        return always;
    }

    private void audit(String tenantId, String issuerDid, String vct, String subjectRef,
                       Map<String, Object> result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("issuer", issuerDid);
        payload.put("vct", vct);
        payload.put("claims", result.get("claims"));
        try {
            auditLogService.append("didvpVerified", tenantId, subjectRef, objectMapper.writeValueAsString(payload));
            meteringService.recordVerification(tenantId, issuerDid, vct, subjectRef,
                    properties.getVerificationFeeMinorUnits(), properties.getVerificationFeeCurrency());
        } catch (Exception e) {
            LOGGER.warn("Audit or metering failed for verification by {}", tenantId, e);
        }
    }

    private VpRequestContext requireContext(String tenantId, String requestId) {
        VpRequestContext context = requests.get(requestId);
        if (context == null || !tenantId.equals(context.tenantId) || context.expiresAt < System.currentTimeMillis()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown authorization request");
        }
        return context;
    }

    private ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static final class VpRequestContext {
        private final String tenantId;
        private final String clientId;
        private final String responseUri;
        private final String nonce;
        private final Map<String, List<String>> claims;
        private final DcqlQueryParser.Query dcql;
        private final Object dcqlQueryJson;
        private final long expiresAt;

        private VpRequestContext(String tenantId, String clientId, String responseUri, String nonce,
                                 Map<String, List<String>> claims, DcqlQueryParser.Query dcql,
                                 Object dcqlQueryJson, long expiresAt) {
            this.tenantId = tenantId;
            this.clientId = clientId;
            this.responseUri = responseUri;
            this.nonce = nonce;
            this.claims = claims;
            this.dcql = dcql;
            this.dcqlQueryJson = dcqlQueryJson;
            this.expiresAt = expiresAt;
        }
    }
}
