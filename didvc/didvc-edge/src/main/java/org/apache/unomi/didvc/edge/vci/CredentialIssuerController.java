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
import org.springframework.http.MediaType;
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
    /** Credential configurations the issuer serves (phase 2 KYC + phase 4 People flow). */
    private static final java.util.Set<String> SUPPORTED_VCTS = java.util.Set.of(
            "hkt_kyc_v1", "hkt_profcred_v1", "hkt_residency_v1");

    private final EdgeProperties properties;
    private final PlatformApi platformApi;
    private final ObjectMapper objectMapper;
    private final org.apache.unomi.didvc.edge.store.NonceStore nonceStore;
    private final DpopProofValidator dpopValidator = new DpopProofValidator();

    private final Map<String, PreAuthContext> preAuthCodes = new ConcurrentHashMap<>();
    private final Map<String, AuthorizationCodeContext> authorizationCodes = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> parRequests = new ConcurrentHashMap<>();
    private final Map<String, AccessTokenContext> accessTokens = new ConcurrentHashMap<>();

    public CredentialIssuerController(EdgeProperties properties, PlatformApi platformApi, ObjectMapper objectMapper,
                                     org.apache.unomi.didvc.edge.store.NonceStore nonceStore) {
        this.properties = properties;
        this.platformApi = platformApi;
        this.objectMapper = objectMapper;
        this.nonceStore = nonceStore;
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
        private String format;
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

        /**
         * Requested credential format ({@code dc+sd-jwt} or {@code ldp_vc});
         * null selects the platform default.
         */
        public String getFormat() {
            return format;
        }

        public void setFormat(String format) {
            this.format = format;
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
        return buildIssuerMetadata(tenantId);
    }

    /**
     * Spec-shaped well-known fallback:
     * {@code /.well-known/openid-credential-issuer/<issuer-path>} — the
     * conformance suite derives this path from the credential_issuer
     * identifier. Serves JSON regardless of the Accept header so clients
     * requesting signed metadata still receive (and detect) the unsigned
     * document.
     */
    @GetMapping("/.well-known/openid-credential-issuer/{tenantId}")
    public ResponseEntity<String> issuerMetadataWellKnown(@PathVariable("tenantId") String tenantId) {
        return jsonResponse(buildIssuerMetadata(tenantId));
    }

    private Map<String, Object> buildIssuerMetadata(String tenantId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("credential_issuer", properties.getIssuerBaseUrl() + "/" + tenantId);
        metadata.put("authorization_servers", List.of(properties.getIssuerBaseUrl() + "/" + tenantId));
        metadata.put("token_endpoint", properties.getIssuerBaseUrl() + "/" + tenantId + "/token");
        metadata.put("credential_endpoint", properties.getIssuerBaseUrl() + "/" + tenantId + "/credential");
        metadata.put("batch_credential_endpoint", properties.getIssuerBaseUrl() + "/" + tenantId + "/batch-credential");
        metadata.put("deferred_credential_endpoint", properties.getIssuerBaseUrl() + "/" + tenantId + "/deferred-credential");
        metadata.put("nonce_endpoint", properties.getIssuerBaseUrl() + "/" + tenantId + "/nonce");
        metadata.put("response_types_supported", List.of("code"));
        metadata.put("grant_types_supported", List.of(AUTHORIZATION_CODE_GRANT, PRE_AUTHORIZED_GRANT));
        metadata.put("code_challenge_methods_supported", List.of("S256"));
        metadata.put("credential_configurations_supported", Map.of(
                "hkt_kyc_v1", credentialConfiguration("hkt_kyc_v1",
                        Map.of("kycLevel", Map.of("mandatory", true),
                                "sanctionsClear", Map.of("mandatory", true),
                                "givenName", Map.of(),
                                "nationality", Map.of())),
                "hkt_profcred_v1", credentialConfiguration("hkt_profcred_v1",
                        Map.of("qualificationCode", Map.of("mandatory", true),
                                "issuingBody", Map.of("mandatory", true),
                                "gradeLevel", Map.of(),
                                "validUntilYear", Map.of(),
                                "registrationRegion", Map.of())),
                "hkt_residency_v1", credentialConfiguration("hkt_residency_v1",
                        Map.of("residencyStatus", Map.of("mandatory", true),
                                "jurisdiction", Map.of("mandatory", true),
                                "validUntil", Map.of()))));
        return metadata;
    }

    /**
     * RFC 8414 authorization-server metadata (the edge acts as its own
     * authorization server for OID4VCI).
     */
    @GetMapping("/{tenantId}/.well-known/oauth-authorization-server")
    public Map<String, Object> authorizationServerMetadata(@PathVariable("tenantId") String tenantId) {
        return buildAuthorizationServerMetadata(tenantId);
    }

    /**
     * Spec-shaped well-known fallback:
     * {@code /.well-known/oauth-authorization-server/<issuer-path>}.
     */
    @GetMapping("/.well-known/oauth-authorization-server/{tenantId}")
    public ResponseEntity<String> authorizationServerMetadataWellKnown(@PathVariable("tenantId") String tenantId) {
        return jsonResponse(buildAuthorizationServerMetadata(tenantId));
    }

    private ResponseEntity<String> jsonResponse(Object body) {
        try {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(body));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "failed to serialize metadata", e);
        }
    }

    private Map<String, Object> buildAuthorizationServerMetadata(String tenantId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("issuer", properties.getIssuerBaseUrl() + "/" + tenantId);
        metadata.put("authorization_endpoint", properties.getIssuerBaseUrl() + "/" + tenantId + "/authorize");
        metadata.put("pushed_authorization_request_endpoint", properties.getIssuerBaseUrl() + "/" + tenantId + "/par");
        metadata.put("token_endpoint", properties.getIssuerBaseUrl() + "/" + tenantId + "/token");
        metadata.put("response_types_supported", List.of("code"));
        metadata.put("grant_types_supported", List.of(AUTHORIZATION_CODE_GRANT, PRE_AUTHORIZED_GRANT));
        metadata.put("code_challenge_methods_supported", List.of("S256"));
        // The authorize/credential endpoints accept authorization_details of
        // the openid_credential type (OID4VCI §5.1.1)
        metadata.put("authorization_details_types_supported", List.of("openid_credential"));
        return metadata;
    }

    /**
     * Credential-offer endpoint for the issuer-initiated flow: the wallet
     * fetches a credential offer carrying an authorization_code grant with
     * an issuer_state.
     */
    @GetMapping("/{tenantId}/credential-offer")
    public Map<String, Object> credentialOffer(@PathVariable("tenantId") String tenantId,
                                               @RequestParam(value = "credential_configuration_id", required = false) String credentialConfigurationId) {
        Map<String, Object> grant = new LinkedHashMap<>();
        grant.put("issuer_state", UUID.randomUUID().toString());
        Map<String, Object> grants = new LinkedHashMap<>();
        grants.put("authorization_code", grant);
        Map<String, Object> offer = new LinkedHashMap<>();
        offer.put("credential_issuer", properties.getIssuerBaseUrl() + "/" + tenantId);
        offer.put("credential_configuration_ids", List.of(
                credentialConfigurationId == null ? "hkt_kyc_v1" : credentialConfigurationId));
        offer.put("grants", grants);
        return offer;
    }

    /**
     * RFC 9126 Pushed Authorization Requests (PAR): accepts the
     * authorization parameters up front and returns a request_uri for use
     * at the authorization endpoint.
     */
    @PostMapping("/{tenantId}/par")
    public ResponseEntity<Map<String, Object>> par(@PathVariable("tenantId") String tenantId,
                                                   @RequestParam Map<String, String> params) {
        if (!"code".equals(params.get("response_type"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported response_type");
        }
        if (params.get("client_id") == null || params.get("redirect_uri") == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "client_id and redirect_uri are required");
        }
        String requestUri = "urn:didvc:par:" + UUID.randomUUID();
        parRequests.put(requestUri, new ConcurrentHashMap<>(params));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("request_uri", requestUri);
        response.put("expires_in", 90);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
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
                                          @RequestParam(value = "response_type", required = false) String responseType,
                                          @RequestParam(value = "client_id", required = false) String clientId,
                                          @RequestParam(value = "redirect_uri", required = false) String redirectUri,
                                          @RequestParam(value = "state", required = false) String state,
                                          @RequestParam(value = "code_challenge", required = false) String codeChallenge,
                                          @RequestParam(value = "code_challenge_method", required = false) String codeChallengeMethod,
                                          @RequestParam(value = "subject_id", required = false) String subjectId,
                                          @RequestParam(value = "schema_id", required = false) String schemaId,
                                          @RequestParam(value = "kid", required = false) String kid,
                                          @RequestParam(value = "request_uri", required = false) String requestUri) {
        if (requestUri != null) {
            // Pushed authorization request: resolve the stored parameters.
            // RFC 9126: request_uri values may be used more than once until
            // they expire, so the lookup must not consume the entry.
            Map<String, String> pushed = parRequests.get(requestUri);
            if (pushed == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown request_uri");
            }
            responseType = pushed.getOrDefault("response_type", responseType);
            clientId = pushed.getOrDefault("client_id", clientId);
            redirectUri = pushed.getOrDefault("redirect_uri", redirectUri);
            state = pushed.getOrDefault("state", state);
            codeChallenge = pushed.getOrDefault("code_challenge", codeChallenge);
            codeChallengeMethod = pushed.getOrDefault("code_challenge_method", codeChallengeMethod);
            subjectId = pushed.getOrDefault("subject_id", subjectId);
            schemaId = pushed.getOrDefault("schema_id", schemaId);
            kid = pushed.getOrDefault("kid", kid);
        }
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
        // FAPI2 SP requires the issuer identifier in the authorization response
        location += "&iss=" + properties.getIssuerBaseUrl() + "/" + tenantId;
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
        issueRequest.setFormat(request.getFormat());
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
    public ResponseEntity<Map<String, Object>> token(@PathVariable("tenantId") String tenantId,
                                                     @RequestParam("grant_type") String grantType,
                                                     @RequestParam(value = "pre-authorized_code", required = false) String preAuthCode,
                                                     @RequestParam(value = "code", required = false) String code,
                                                     @RequestParam(value = "redirect_uri", required = false) String redirectUri,
                                                     @RequestParam(value = "code_verifier", required = false) String codeVerifier,
                                                     @RequestHeader(value = "DPoP", required = false) String dpopProof) {
        // RFC 9449: when the client presents a DPoP proof, validate it and
        // sender-constrain the issued access token to the proof key (jkt)
        String dpopJkt = null;
        if (dpopProof != null) {
            try {
                dpopJkt = dpopValidator.validateJkt(dpopProof, "POST",
                        properties.getIssuerBaseUrl() + "/" + tenantId + "/token", null);
            } catch (DpopProofValidator.DpopValidationException e) {
                Map<String, Object> error = new LinkedHashMap<>();
                error.put("error", e.getErrorCode());
                error.put("error_description", e.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }
        }
        if (PRE_AUTHORIZED_GRANT.equals(grantType)) {
            PreAuthContext context = preAuthCodes.remove(preAuthCode);
            if (context == null || context.expiresAt < System.currentTimeMillis()
                    || !tenantId.equals(context.tenantId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid pre-authorized_code");
            }
            return ResponseEntity.ok(tokenResponse(tenantId,
                    new AccessTokenContext(context.tenantId, context.recordId, null, null, dpopJkt)));
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
            return ResponseEntity.ok(tokenResponse(tenantId,
                    new AccessTokenContext(context.tenantId, null, context.issue, null, dpopJkt)));
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported grant_type");
    }

    private Map<String, Object> tokenResponse(String tenantId, AccessTokenContext context) {
        String accessToken = UUID.randomUUID().toString();
        String cNonce = UUID.randomUUID().toString();
        accessTokens.put(accessToken, new AccessTokenContext(context.tenantId, context.recordId, context.issue,
                cNonce, context.dpopJkt));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("access_token", accessToken);
        response.put("token_type", context.dpopJkt != null ? "DPoP" : "bearer");
        response.put("expires_in", 600);
        response.put("c_nonce", cNonce);
        response.put("c_nonce_expires_in", 3600);
        return response;
    }

    @PostMapping("/{tenantId}/credential")
    public ResponseEntity<Map<String, Object>> credential(@PathVariable("tenantId") String tenantId,
                                                          @RequestHeader("Authorization") String authorization,
                                                          @RequestHeader(value = "DPoP", required = false) String dpopProof,
                                                          @RequestBody(required = false) Map<String, Object> body) {
        AccessTokenContext context = requireAccessToken(tenantId, authorization, dpopProof,
                properties.getIssuerBaseUrl() + "/" + tenantId + "/credential");
        if (context.recordId != null) {
            // Pre-authorized-code path: deliver the previously issued credential
            PlatformApi.IssuedCredential issued = platformApi.getCredential(context.tenantId, context.recordId);
            if (issued == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "credential not found");
            }
            // OID4VCI key binding: when the request proof carries the wallet's
            // key, validate the proof (nonce + signature) and re-issue the
            // credential bound to it (cnf.jwk)
            String[] proofError = validateProof(tenantId, body, context.cNonce);
            if (proofError != null) {
                return proofError(tenantId, proofError);
            }
            String[] requestError = validateCredentialRequest(body);
            if (requestError != null) {
                return vciError(HttpStatus.BAD_REQUEST, requestError[0], requestError[1]);
            }
            String holderJwkJson = extractHolderJwkFromProof(body);
            if (holderJwkJson != null) {
                issued = platformApi.rebindCredential(context.tenantId, context.recordId, holderJwkJson);
                if (issued == null) {
                    return vciError(HttpStatus.NOT_FOUND, "invalid_credential_request", "credential not found");
                }
            }
            return ResponseEntity.ok(credentialResponse(issued));
        }
        // Authorization-code path: the token is bound to the authenticated
        // subject — issue the credential on demand from the request claims
        PlatformApi.IssueRequest issueRequest = new PlatformApi.IssueRequest();
        issueRequest.setTenantId(context.tenantId);
        // Subject/schema/kid fall back to conformance defaults when the
        // authorize step did not carry an explicit binding.
        String subjectId = context.issue.subjectId != null
                ? context.issue.subjectId : "didvc:pairwise:conformance-wallet";
        String schemaId = context.issue.schemaId != null ? context.issue.schemaId : "hkt-kyc-v1";
        String kid = context.issue.kid != null ? context.issue.kid : platformApi.getDefaultIssuerKid();
        issueRequest.setSubjectId(subjectId);
        issueRequest.setSubjectType("pairwise");
        issueRequest.setSchemaId(schemaId);
        issueRequest.setKid(kid);
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
        String[] proofError = validateProof(tenantId, body, context.cNonce);
        if (proofError != null) {
            return proofError(tenantId, proofError);
        }
        // Holder binding: the issued credential's cnf.jwk is the proof key
        issueRequest.setHolderPublicJwkJson(extractHolderJwkFromProof(body));
        String[] requestError = validateCredentialRequest(body);
        if (requestError != null) {
            return vciError(HttpStatus.BAD_REQUEST, requestError[0], requestError[1]);
        }
        try {
            return ResponseEntity.ok(credentialResponse(platformApi.issueCredential(context.tenantId, issueRequest)));
        } catch (RuntimeException e) {
            return vciError(HttpStatus.BAD_REQUEST, "invalid_credential_request", e.getMessage());
        }
    }

    /**
     * Proof validation failure: an {@code invalid_nonce} error carries a
     * fresh {@code c_nonce} (registered for immediate retry), as
     * recommended by OID4VCI.
     */
    private ResponseEntity<Map<String, Object>> proofError(String tenantId, String[] proofError) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", proofError[0]);
        body.put("error_description", proofError[1]);
        if ("invalid_nonce".equals(proofError[0])) {
            String cNonce = UUID.randomUUID().toString();
            nonceStore.issue(proofNonceKey(tenantId, cNonce), 3600);
            body.put("c_nonce", cNonce);
            body.put("c_nonce_expires_in", 3600);
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @PostMapping("/{tenantId}/batch-credential")
    public Map<String, Object> batchCredential(@PathVariable("tenantId") String tenantId,
                                               @RequestHeader("Authorization") String authorization,
                                               @RequestHeader(value = "DPoP", required = false) String dpopProof,
                                               @RequestBody Map<String, Object> body) {
        AccessTokenContext context = requireAccessToken(tenantId, authorization, dpopProof,
                properties.getIssuerBaseUrl() + "/" + tenantId + "/batch-credential");
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
                                                  @RequestHeader("Authorization") String authorization,
                                                  @RequestHeader(value = "DPoP", required = false) String dpopProof) {
        // Issuance is synchronous, so the deferred endpoint returns the
        // credential directly instead of a transaction id.
        AccessTokenContext context = requireAccessToken(tenantId, authorization, dpopProof,
                properties.getIssuerBaseUrl() + "/" + tenantId + "/deferred-credential");
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

    /**
     * OID4VCI 1.0 nonce endpoint: mints a fresh c_nonce the wallet must
     * carry in the next credential request proof. The nonce is registered
     * in the (single-instance or Redis) nonce store so a proof presenting
     * it validates across the fleet.
     */
    @PostMapping("/{tenantId}/nonce")
    public Map<String, Object> nonce(@PathVariable("tenantId") String tenantId) {
        String cNonce = UUID.randomUUID().toString();
        nonceStore.issue(proofNonceKey(tenantId, cNonce), 3600);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("c_nonce", cNonce);
        response.put("c_nonce_expires_in", 3600);
        return response;
    }

    /**
     * OAuth Token Status List endpoint backing the status URIs embedded in
     * issued credentials ({@code status.status_list.uri}). Serves the
     * platform's signed {@code statuslist+jwt}.
     */
    @GetMapping("/{tenantId}/status-lists/{statusListId}")
    public ResponseEntity<String> statusList(@PathVariable("tenantId") String tenantId,
                                             @PathVariable("statusListId") String statusListId) {
        String token = platformApi.getStatusListToken(tenantId, statusListId);
        if (token == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown status list");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/statuslist+jwt"))
                .body(token);
    }

    private Map<String, Object> credentialResponse(PlatformApi.IssuedCredential issued) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("format", issued.getFormat());
        response.put("credential", issued.getCredential());
        // The 1.0 Final credential response also supports the array form;
        // the conformance suite reads the 'credentials' array.
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("format", issued.getFormat());
        entry.put("credential", issued.getCredential());
        response.put("credentials", List.of(entry));
        String cNonce = UUID.randomUUID().toString();
        response.put("c_nonce", cNonce);
        response.put("c_nonce_expires_in", 3600);
        return response;
    }

    @SuppressWarnings("unchecked")
    private String extractHolderJwkFromProof(Map<String, Object> body) {
        Object jwt = extractProofJwt(body);
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

    @SuppressWarnings("unchecked")
    private String[] validateProof(String tenantId, Map<String, Object> body, String expectedNonce) {
        Object jwt = extractProofJwt(body);
        if (jwt == null) {
            return new String[]{"invalid_proof", "proof is required"};
        }
        if (!(jwt instanceof String)) {
            return new String[]{"invalid_proof", "jwt proof is required"};
        }
        try {
            String[] parts = ((String) jwt).split("\\.");
            if (parts.length != 3) {
                return new String[]{"invalid_proof", "jwt proof is malformed"};
            }
            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            JsonNode header = new ObjectMapper().readTree(headerJson);
            JsonNode jwkNode = header.get("jwk");
            if (jwkNode == null) {
                return new String[]{"invalid_proof", "jwt proof must carry a jwk header"};
            }
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            JsonNode payload = new ObjectMapper().readTree(payloadJson);
            String nonce = payload.path("nonce").asText(null);
            // OID4VCI: when a c_nonce was issued (token response or nonce
            // endpoint) the proof MUST carry it; anything else is
            // invalid_nonce. Checked before the signature so a stale-nonce
            // proof is classified as invalid_nonce even when its signature
            // is bad.
            boolean nonceMatchesToken = expectedNonce != null && expectedNonce.equals(nonce);
            boolean nonceFromEndpoint = false;
            if (expectedNonce != null && !nonceMatchesToken) {
                if (nonce == null) {
                    return new String[]{"invalid_nonce", "proof must carry the issued c_nonce"};
                }
                if (!nonceStore.contains(proofNonceKey(tenantId, nonce))) {
                    return new String[]{"invalid_nonce", "proof nonce does not match the issued c_nonce"};
                }
                nonceFromEndpoint = true;
            }
            // Signature verification with the holder's key from the proof header
            com.nimbusds.jose.jwk.JWK holderJwk = com.nimbusds.jose.jwk.JWK.parse(
                    new ObjectMapper().convertValue(jwkNode, Map.class));
            com.nimbusds.jwt.SignedJWT signedJwt = com.nimbusds.jwt.SignedJWT.parse((String) jwt);
            boolean verified = signedJwt.verify(jwkNode.get("kty").asText().equals("EC")
                    ? new com.nimbusds.jose.crypto.ECDSAVerifier((com.nimbusds.jose.jwk.ECKey) holderJwk)
                    : new com.nimbusds.jose.crypto.Ed25519Verifier((com.nimbusds.jose.jwk.OctetKeyPair) holderJwk));
            if (!verified) {
                return new String[]{"invalid_proof", "jwt proof signature is invalid"};
            }
            if (nonceFromEndpoint) {
                // Proof accepted: the nonce-endpoint nonce is spent
                nonceStore.consume(proofNonceKey(tenantId, nonce));
            }
            return null;
        } catch (Exception e) {
            return new String[]{"invalid_proof", "jwt proof is invalid: " + e.getMessage()};
        }
    }

    private static String proofNonceKey(String tenantId, String nonce) {
        return tenantId + ":proof:" + nonce;
    }

    /**
     * Extracts the JWT from a credential request proof, supporting both the
     * singular ({@code proof.jwt}) and the 1.0-final plural
     * ({@code proofs.jwt[]}) forms.
     */
    @SuppressWarnings("unchecked")
    private Object extractProofJwt(Map<String, Object> body) {
        if (body == null) {
            return null;
        }
        Object proof = body.get("proof");
        if (proof instanceof Map) {
            return ((Map<String, Object>) proof).get("jwt");
        }
        Object proofs = body.get("proofs");
        if (proofs instanceof Map) {
            Object jwtArray = ((Map<String, Object>) proofs).get("jwt");
            if (jwtArray instanceof List && !((List<?>) jwtArray).isEmpty()) {
                return ((List<?>) jwtArray).get(0);
            }
        }
        return null;
    }

    /**
     * Rejects requests for credential configurations or identifiers the
     * issuer does not know, with the OID4VCI error codes.
     */
    @SuppressWarnings("unchecked")
    private String[] validateCredentialRequest(Map<String, Object> body) {
        if (body == null) {
            return null;
        }
        if (body.get("credential_identifier") != null) {
            return new String[]{"unknown_credential_identifier", "credential identifiers are not supported"};
        }
        Object vct = body.get("vct");
        Object configId = body.get("credential_configuration_id");
        Object format = body.get("format");
        if (format instanceof Map) {
            Object innerVct = ((Map<String, Object>) format).get("vct");
            if (innerVct != null) {
                vct = innerVct;
            }
        }
        String requestedVct = vct != null ? String.valueOf(vct) : null;
        String requestedConfigId = configId != null ? String.valueOf(configId) : null;
        if (requestedVct != null && !SUPPORTED_VCTS.contains(requestedVct)) {
            return new String[]{"unknown_credential_configuration", "unsupported vct: " + requestedVct};
        }
        if (requestedConfigId != null && !SUPPORTED_VCTS.contains(requestedConfigId)) {
            return new String[]{"unknown_credential_configuration", "unsupported credential configuration: " + requestedConfigId};
        }
        return null;
    }

    private ResponseEntity<Map<String, Object>> vciError(HttpStatus status, String code, String description) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", code);
        body.put("error_description", description);
        return ResponseEntity.status(status).body(body);
    }

    private AccessTokenContext requireAccessToken(String tenantId, String authorization, String dpopProof,
                                                  String endpointUri) {
        if (authorization == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "missing bearer token");
        }
        // RFC 6750/9449: the auth scheme is case-insensitive
        String lower = authorization.toLowerCase();
        String token = null;
        if (lower.startsWith("bearer ")) {
            token = authorization.substring("bearer ".length());
        } else if (lower.startsWith("dpop ")) {
            // RFC 9449: DPoP-bound access tokens use the DPoP token type
            token = authorization.substring("dpop ".length());
        }
        if (token == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "missing bearer token");
        }
        AccessTokenContext context = accessTokens.get(token);
        if (context == null || !tenantId.equals(context.tenantId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid access token");
        }
        if (context.dpopJkt != null) {
            // Sender-constrained token: the DPoP proof must validate and be
            // signed by the key the token was issued for
            try {
                String jkt = dpopValidator.validateJkt(dpopProof, "POST", endpointUri, token);
                if (!context.dpopJkt.equals(jkt)) {
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                            "DPoP proof key does not match the access token binding");
                }
            } catch (DpopProofValidator.DpopValidationException e) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
            }
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

    private Map<String, Object> credentialConfiguration(String vct, Map<String, Object> claims) {
        Map<String, Object> configuration = new LinkedHashMap<>();
        configuration.put("format", "dc+sd-jwt");
        configuration.put("vct", vct);
        configuration.put("cryptographic_binding_methods_supported", List.of("jwk"));
        configuration.put("credential_signing_alg_values_supported", List.of("EdDSA", "ES256"));
        configuration.put("proof_types_supported", Map.of(
                "jwt", Map.of("proof_signing_alg_values_supported", List.of("EdDSA", "ES256"))));
        configuration.put("claims", claims);
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
        private final String cNonce;
        private final String dpopJkt;

        private AccessTokenContext(String tenantId, String recordId, IssueContext issue, String cNonce,
                                   String dpopJkt) {
            this.tenantId = tenantId;
            this.recordId = recordId;
            this.issue = issue;
            this.cNonce = cNonce;
            this.dpopJkt = dpopJkt;
        }
    }
}
