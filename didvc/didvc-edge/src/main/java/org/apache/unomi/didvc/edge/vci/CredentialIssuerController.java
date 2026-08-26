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

package org.apache.unomi.didvc.edge.vci;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.unomi.didvc.edge.EdgeProperties;
import org.apache.unomi.didvc.edge.platform.PlatformApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OpenID4VCI 1.0 credential issuer: issuer metadata, internal offer
 * creation, both grant types (pre-authorized code and authorization code
 * with PKCE), credential (single, batch, deferred) and nonce endpoints.
 * Credential state lives in the platform; only ephemeral codes and tokens
 * are held here.
 */
@RestController
public class CredentialIssuerController {

    private static final Logger LOGGER = LoggerFactory.getLogger(CredentialIssuerController.class);
    private static final String PRE_AUTHORIZED_GRANT = "urn:ietf:params:oauth:grant-type:pre-authorized_code";
    private static final String AUTHORIZATION_CODE_GRANT = "authorization_code";
    private static final long CODE_TTL_MILLIS = 10 * 60 * 1000L;

    private final EdgeProperties properties;
    private final PlatformApi platformApi;

    private final Map<String, PreAuthContext> preAuthCodes = new ConcurrentHashMap<>();
    private final Map<String, AuthorizationCodeContext> authorizationCodes = new ConcurrentHashMap<>();
    private final Map<String, AccessTokenContext> accessTokens = new ConcurrentHashMap<>();

    public CredentialIssuerController(EdgeProperties properties, PlatformApi platformApi) {
        this.properties = properties;
        this.platformApi = platformApi;
    }

    /**
     * Offer creation request (internal admin API).
     */
    public static class OfferRequest {
        private String schemaId;
        private String vct;
        private String subjectId;
        private String subjectType;
        private String kid;
        private String verifierCategory;
        private String holderPublicJwkJson;
        private Integer validityDays;
        private Map<String, Object> alwaysDisclosedClaims;
        private Map<String, Object> selectivelyDisclosedClaims;

        public String getSchemaId() {
            return schemaId;
        }

        public void setSchemaId(String schemaId) {
            this.schemaId = schemaId;
        }

        /**
         * The credential configuration identifier (vct) to advertise in the
         * offer; falls back to the schema id.
         */
        public String getVct() {
            return vct;
        }

        public void setVct(String vct) {
            this.vct = vct;
        }

        public String getSubjectId() {
            return subjectId;
        }

        public void setSubjectId(String subjectId) {
            this.subjectId = subjectId;
        }

        public String getSubjectType() {
            return subjectType;
        }

        public void setSubjectType(String subjectType) {
            this.subjectType = subjectType;
        }

        public String getKid() {
            return kid;
        }

        public void setKid(String kid) {
            this.kid = kid;
        }

        public String getVerifierCategory() {
            return verifierCategory;
        }

        public void setVerifierCategory(String verifierCategory) {
            this.verifierCategory = verifierCategory;
        }

        public String getHolderPublicJwkJson() {
            return holderPublicJwkJson;
        }

        public void setHolderPublicJwkJson(String holderPublicJwkJson) {
            this.holderPublicJwkJson = holderPublicJwkJson;
        }

        public Integer getValidityDays() {
            return validityDays;
        }

        public void setValidityDays(Integer validityDays) {
            this.validityDays = validityDays;
        }

        public Map<String, Object> getAlwaysDisclosedClaims() {
            return alwaysDisclosedClaims;
        }

        public void setAlwaysDisclosedClaims(Map<String, Object> alwaysDisclosedClaims) {
            this.alwaysDisclosedClaims = alwaysDisclosedClaims;
        }

        public Map<String, Object> getSelectivelyDisclosedClaims() {
            return selectivelyDisclosedClaims;
        }

        public void setSelectivelyDisclosedClaims(Map<String, Object> selectivelyDisclosedClaims) {
            this.selectivelyDisclosedClaims = selectivelyDisclosedClaims;
        }
    }

    @GetMapping("/{tenantId}/.well-known/openid-credential-issuer")
    public Map<String, Object> issuerMetadata(@PathVariable("tenantId") String tenantId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("credential_issuer", properties.getIssuerBaseUrl() + "/" + tenantId);
        metadata.put("authorization_servers", List.of(properties.getIssuerBaseUrl() + "/" + tenantId));
        metadata.put("token_endpoint", properties.getIssuerBaseUrl() + "/" + tenantId + "/token");
        metadata.put("credential_endpoint", properties.getIssuerBaseUrl() + "/" + tenantId + "/credential");
        metadata.put("batch_credential_endpoint", properties.getIssuerBaseUrl() + "/" + tenantId + "/batch-credential");
        metadata.put("deferred_credential_endpoint", properties.getIssuerBaseUrl() + "/" + tenantId + "/deferred-credential");
        metadata.put("response_types_supported", List.of("code"));
        metadata.put("grant_types_supported", List.of(AUTHORIZATION_CODE_GRANT, PRE_AUTHORIZED_GRANT));
        metadata.put("code_challenge_methods_supported", List.of("S256"));
        metadata.put("credential_configurations_supported", Map.of(
                "hkt_kyc_v1", credentialConfiguration()));
        return metadata;
    }

    /**
     * RFC 8414 authorization-server metadata (the edge acts as its own
     * authorization server for OID4VCI).
     */
    @GetMapping("/{tenantId}/.well-known/oauth-authorization-server")
    public Map<String, Object> authorizationServerMetadata(@PathVariable("tenantId") String tenantId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("issuer", properties.getIssuerBaseUrl() + "/" + tenantId);
        metadata.put("authorization_endpoint", properties.getIssuerBaseUrl() + "/" + tenantId + "/authorize");
        metadata.put("token_endpoint", properties.getIssuerBaseUrl() + "/" + tenantId + "/token");
        metadata.put("response_types_supported", List.of("code"));
        metadata.put("grant_types_supported", List.of(AUTHORIZATION_CODE_GRANT, PRE_AUTHORIZED_GRANT));
        metadata.put("code_challenge_methods_supported", List.of("S256"));
        return metadata;
    }

    /**
     * Authorization endpoint for the OID4VCI authorization-code flow
     * (OAuth 2.0 authorization code grant with PKCE). In production this
     * endpoint authenticates the subject through the platform IdP; this
     * implementation issues the code directly, with the subject binding
     * carried in the {@code subject_id}/{@code schema_id}/{@code kid}
     * parameters, so the full wallet flow can be exercised end to end.
     */
    @GetMapping("/{tenantId}/authorize")
    public ResponseEntity<Void> authorize(@PathVariable("tenantId") String tenantId,
                                          @RequestParam("response_type") String responseType,
                                          @RequestParam("client_id") String clientId,
                                          @RequestParam("redirect_uri") String redirectUri,
                                          @RequestParam(value = "state", required = false) String state,
                                          @RequestParam(value = "code_challenge", required = false) String codeChallenge,
                                          @RequestParam(value = "code_challenge_method", required = false) String codeChallengeMethod,
                                          @RequestParam(value = "subject_id", required = false) String subjectId,
                                          @RequestParam(value = "schema_id", required = false) String schemaId,
                                          @RequestParam(value = "kid", required = false) String kid) {
        if (!"code".equals(responseType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported response_type");
        }
        if (clientId == null || redirectUri == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "client_id and redirect_uri are required");
        }
        if (codeChallenge != null && codeChallengeMethod == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "code_challenge_method is required");
        }
        String code = UUID.randomUUID().toString();
        authorizationCodes.put(code, new AuthorizationCodeContext(tenantId, clientId, redirectUri,
                codeChallenge, codeChallengeMethod, new IssueContext(subjectId, schemaId, kid),
                System.currentTimeMillis() + CODE_TTL_MILLIS));
        String location = redirectUri + (redirectUri.contains("?") ? "&" : "?") + "code=" + code;
        if (state != null) {
            location += "&state=" + state;
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, URI.create(location).toString())
                .build();
    }

    @PostMapping("/{tenantId}/internal/offers")
    public Map<String, Object> createOffer(@PathVariable("tenantId") String tenantId,
                                           @RequestHeader(value = "X-Api-Key", required = false) String apiKey,
                                           @RequestBody OfferRequest request) {
        if (properties.getInternalApiKey() != null && !properties.getInternalApiKey().isEmpty()
                && !properties.getInternalApiKey().equals(apiKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid internal API key");
        }
        if (request.getSchemaId() == null || request.getSubjectId() == null || request.getKid() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "schemaId, subjectId and kid are required");
        }
        PlatformApi.IssueRequest issueRequest = new PlatformApi.IssueRequest();
        issueRequest.setTenantId(tenantId);
        issueRequest.setSchemaId(request.getSchemaId());
        issueRequest.setSubjectId(request.getSubjectId());
        issueRequest.setSubjectType(request.getSubjectType());
        issueRequest.setKid(request.getKid());
        issueRequest.setVerifierCategory(request.getVerifierCategory());
        issueRequest.setHolderPublicJwkJson(request.getHolderPublicJwkJson());
        issueRequest.setValidityDays(request.getValidityDays());
        issueRequest.setAlwaysDisclosedClaims(request.getAlwaysDisclosedClaims());
        issueRequest.setSelectivelyDisclosedClaims(request.getSelectivelyDisclosedClaims());
        PlatformApi.IssuedCredential issued;
        try {
            issued = platformApi.issueCredential(tenantId, issueRequest);
        } catch (RuntimeException e) {
            LOGGER.warn("Issuance failed for tenant {} schema {}: {}", tenantId, request.getSchemaId(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "issuance failed: " + e.getMessage());
        }
        String preAuthCode = UUID.randomUUID().toString();
        preAuthCodes.put(preAuthCode, new PreAuthContext(tenantId, issued.getItemId(),
                System.currentTimeMillis() + CODE_TTL_MILLIS));

        Map<String, Object> grant = new LinkedHashMap<>();
        grant.put("pre-authorized_code", preAuthCode);
        Map<String, Object> grants = new LinkedHashMap<>();
        grants.put(PRE_AUTHORIZED_GRANT, grant);

        Map<String, Object> offer = new LinkedHashMap<>();
        offer.put("credential_issuer", properties.getIssuerBaseUrl() + "/" + tenantId);
        String credentialConfigurationId = request.getVct() == null ? request.getSchemaId() : request.getVct();
        offer.put("credentials", List.of(credentialConfigurationId));
        offer.put("grants", grants);
        return offer;
    }

    @PostMapping("/{tenantId}/token")
    public Map<String, Object> token(@PathVariable("tenantId") String tenantId,
                                     @RequestParam("grant_type") String grantType,
                                     @RequestParam(value = "pre-authorized_code", required = false) String preAuthCode,
                                     @RequestParam(value = "code", required = false) String code,
                                     @RequestParam(value = "redirect_uri", required = false) String redirectUri,
                                     @RequestParam(value = "code_verifier", required = false) String codeVerifier) {
        if (PRE_AUTHORIZED_GRANT.equals(grantType)) {
            PreAuthContext context = preAuthCodes.remove(preAuthCode);
            if (context == null || context.expiresAt < System.currentTimeMillis()
                    || !tenantId.equals(context.tenantId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid pre-authorized_code");
            }
            return tokenResponse(tenantId, new AccessTokenContext(context.tenantId, context.recordId, null));
        }
        if (AUTHORIZATION_CODE_GRANT.equals(grantType)) {
            AuthorizationCodeContext context = authorizationCodes.remove(code);
            if (context == null || context.expiresAt < System.currentTimeMillis()
                    || !tenantId.equals(context.tenantId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid authorization code");
            }
            if (redirectUri == null || !redirectUri.equals(context.redirectUri)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "redirect_uri mismatch");
            }
            if (!verifyPkce(context.codeChallenge, context.codeChallengeMethod, codeVerifier)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PKCE verification failed");
            }
            return tokenResponse(tenantId, new AccessTokenContext(context.tenantId, null, context.issue));
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported grant_type");
    }

    private Map<String, Object> tokenResponse(String tenantId, AccessTokenContext context) {
        String accessToken = UUID.randomUUID().toString();
        accessTokens.put(accessToken, context);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("access_token", accessToken);
        response.put("token_type", "bearer");
        response.put("expires_in", 600);
        response.put("c_nonce", UUID.randomUUID().toString());
        response.put("c_nonce_expires_in", 3600);
        return response;
    }

    @PostMapping("/{tenantId}/credential")
    public Map<String, Object> credential(@PathVariable("tenantId") String tenantId,
                                          @RequestHeader("Authorization") String authorization,
                                          @RequestBody(required = false) Map<String, Object> body) {
        AccessTokenContext context = requireAccessToken(tenantId, authorization);
        if (context.recordId != null) {
            // Pre-authorized-code path: deliver the previously issued credential
            PlatformApi.IssuedCredential issued = platformApi.getCredential(context.tenantId, context.recordId);
            if (issued == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "credential not found");
            }
            // OID4VCI key binding: when the request proof carries the wallet's
            // key, re-issue the credential bound to it (cnf.jwk)
            String holderJwkJson = extractHolderJwkFromProof(body);
            if (holderJwkJson != null) {
                issued = platformApi.rebindCredential(context.tenantId, context.recordId, holderJwkJson);
                if (issued == null) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "credential not found");
                }
            }
            return credentialResponse(issued);
        }
        // Authorization-code path: the token is bound to the authenticated
        // subject — issue the credential on demand from the request claims
        PlatformApi.IssueRequest issueRequest = new PlatformApi.IssueRequest();
        issueRequest.setTenantId(context.tenantId);
        issueRequest.setSubjectId(context.issue.subjectId);
        issueRequest.setSubjectType("pairwise");
        issueRequest.setSchemaId(context.issue.schemaId);
        issueRequest.setKid(context.issue.kid);
        if (body != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> claims = (Map<String, Object>) body.getOrDefault("claims", Map.of());
            List<String> selective = body.get("selectiveClaims") instanceof List
                    ? (List<String>) body.get("selectiveClaims") : List.of();
            Map<String, Object> always = new LinkedHashMap<>();
            Map<String, Object> selectively = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : claims.entrySet()) {
                if (selective.contains(entry.getKey())) {
                    selectively.put(entry.getKey(), entry.getValue());
                } else {
                    always.put(entry.getKey(), entry.getValue());
                }
            }
            issueRequest.setAlwaysDisclosedClaims(always);
            issueRequest.setSelectivelyDisclosedClaims(selectively);
        }
        try {
            return credentialResponse(platformApi.issueCredential(context.tenantId, issueRequest));
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "issuance failed: " + e.getMessage());
        }
    }

    @PostMapping("/{tenantId}/batch-credential")
    public Map<String, Object> batchCredential(@PathVariable("tenantId") String tenantId,
                                               @RequestHeader("Authorization") String authorization,
                                               @RequestBody Map<String, Object> body) {
        AccessTokenContext context = requireAccessToken(tenantId, authorization);
        if (context.recordId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "batch issuance is only available for pre-authorized-code tokens");
        }
        PlatformApi.IssuedCredential issued = platformApi.getCredential(context.tenantId, context.recordId);
        if (issued == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "credential not found");
        }
        Map<String, Object> response = new LinkedHashMap<>();
        List<Object> credentialResponses = new ArrayList<>();
        if (body != null && body.get("credential_requests") instanceof List) {
            for (Object ignored : (List<?>) body.get("credential_requests")) {
                credentialResponses.add(credentialResponse(issued));
            }
        }
        response.put("credential_responses", credentialResponses);
        return response;
    }

    @PostMapping("/{tenantId}/deferred-credential")
    public Map<String, Object> deferredCredential(@PathVariable("tenantId") String tenantId,
                                                  @RequestHeader("Authorization") String authorization) {
        // Issuance is synchronous, so the deferred endpoint returns the
        // credential directly instead of a transaction id.
        AccessTokenContext context = requireAccessToken(tenantId, authorization);
        if (context.recordId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "deferred issuance is only available for pre-authorized-code tokens");
        }
        PlatformApi.IssuedCredential issued = platformApi.getCredential(context.tenantId, context.recordId);
        if (issued == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "credential not found");
        }
        return credentialResponse(issued);
    }

    @PostMapping("/{tenantId}/nonce")
    public Map<String, Object> nonce() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("c_nonce", UUID.randomUUID().toString());
        response.put("c_nonce_expires_in", 3600);
        return response;
    }

    private Map<String, Object> credentialResponse(PlatformApi.IssuedCredential issued) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("format", issued.getFormat());
        response.put("credential", issued.getCredential());
        response.put("c_nonce", UUID.randomUUID().toString());
        response.put("c_nonce_expires_in", 3600);
        return response;
    }

    @SuppressWarnings("unchecked")
    private String extractHolderJwkFromProof(Map<String, Object> body) {
        if (body == null || body.get("proof") == null) {
            return null;
        }
        Object proofValue = body.get("proof");
        if (!(proofValue instanceof Map)) {
            return null;
        }
        Object jwt = ((Map<String, Object>) proofValue).get("jwt");
        if (!(jwt instanceof String)) {
            return null;
        }
        try {
            String headerJson = new String(Base64.getUrlDecoder().decode(((String) jwt).split("\\.")[0]),
                    StandardCharsets.UTF_8);
            JsonNode header = new ObjectMapper().readTree(headerJson);
            JsonNode jwkNode = header.get("jwk");
            if (jwkNode != null) {
                ObjectNode publicJwk = jwkNode.deepCopy();
                publicJwk.remove("d");
                return publicJwk.toString();
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to extract holder key from credential request proof", e);
        }
        return null;
    }

    private AccessTokenContext requireAccessToken(String tenantId, String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "missing bearer token");
        }
        AccessTokenContext context = accessTokens.get(authorization.substring("Bearer ".length()));
        if (context == null || !tenantId.equals(context.tenantId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid access token");
        }
        return context;
    }

    private boolean verifyPkce(String codeChallenge, String codeChallengeMethod, String codeVerifier) {
        if (codeChallenge == null || codeChallenge.isEmpty()) {
            return true;
        }
        if (codeVerifier == null || codeVerifier.isEmpty()) {
            return false;
        }
        if ("S256".equalsIgnoreCase(codeChallengeMethod)) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                String computed = Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII)));
                return computed.equals(codeChallenge);
            } catch (Exception e) {
                return false;
            }
        }
        if ("plain".equalsIgnoreCase(codeChallengeMethod)) {
            return codeVerifier.equals(codeChallenge);
        }
        return false;
    }

    private Map<String, Object> credentialConfiguration() {
        Map<String, Object> configuration = new LinkedHashMap<>();
        configuration.put("format", "vc+sd-jwt");
        configuration.put("vct", "hkt_kyc_v1");
        configuration.put("cryptographic_binding_methods_supported", List.of("jwk"));
        configuration.put("credential_signing_alg_values_supported", List.of("EdDSA", "ES256"));
        configuration.put("proof_types_supported", Map.of(
                "jwt", Map.of("proof_signing_alg_values_supported", List.of("EdDSA", "ES256"))));
        configuration.put("claims", Map.of(
                "kycLevel", Map.of("mandatory", true),
                "sanctionsClear", Map.of("mandatory", true),
                "givenName", Map.of(),
                "nationality", Map.of()));
        return configuration;
    }

    private static final class PreAuthContext {
        private final String tenantId;
        private final String recordId;
        private final long expiresAt;

        private PreAuthContext(String tenantId, String recordId, long expiresAt) {
            this.tenantId = tenantId;
            this.recordId = recordId;
            this.expiresAt = expiresAt;
        }
    }

    private static final class AuthorizationCodeContext {
        private final String tenantId;
        private final String clientId;
        private final String redirectUri;
        private final String codeChallenge;
        private final String codeChallengeMethod;
        private final IssueContext issue;
        private final long expiresAt;

        private AuthorizationCodeContext(String tenantId, String clientId, String redirectUri,
                                         String codeChallenge, String codeChallengeMethod,
                                         IssueContext issue, long expiresAt) {
            this.tenantId = tenantId;
            this.clientId = clientId;
            this.redirectUri = redirectUri;
            this.codeChallenge = codeChallenge;
            this.codeChallengeMethod = codeChallengeMethod;
            this.issue = issue;
            this.expiresAt = expiresAt;
        }
    }

    private static final class IssueContext {
        private final String subjectId;
        private final String schemaId;
        private final String kid;

        private IssueContext(String subjectId, String schemaId, String kid) {
            this.subjectId = subjectId;
            this.schemaId = schemaId;
            this.kid = kid;
        }
    }

    private static final class AccessTokenContext {
        private final String tenantId;
        private final String recordId;
        private final IssueContext issue;

        private AccessTokenContext(String tenantId, String recordId, IssueContext issue) {
            this.tenantId = tenantId;
            this.recordId = recordId;
            this.issue = issue;
        }
    }
}
