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

package org.apache.unomi.didvc.edge.wallet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.Ed25519Signer;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jwt.SignedJWT;
import org.apache.unomi.didvc.edge.EdgeProperties;
import org.apache.unomi.didvc.sdjwt.KeyBindingJwtBuilder;
import org.apache.unomi.didvc.sdjwt.SdJwtParser;
import org.apache.unomi.didvc.sdjwt.SdJwtPresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wallet backend orchestration for the HKT subscriber app: redeems
 * OID4VCI credential offers (token exchange + key-bound credential
 * delivery via a holder proof), stores the held credentials, lists them,
 * and builds key-bound OID4VP presentations against verifier
 * authorization requests (offer → hold → present).
 */
@Service
public class WalletService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WalletService.class);
    private static final String PRE_AUTHORIZED_GRANT = "urn:ietf:params:oauth:grant-type:pre-authorized_code";

    private final WalletCredentialStore store;
    private final WalletProtocolClient client;
    private final EdgeProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WalletService(WalletCredentialStore store, WalletProtocolClient client, EdgeProperties properties) {
        this.store = store;
        this.client = client;
        this.properties = properties;
    }

    public List<StoredCredential> listCredentials(String walletId) {
        return store.list(walletId);
    }

    public StoredCredential getCredential(String walletId, String credentialId) {
        return store.get(walletId, credentialId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown credential"));
    }

    public void deleteCredential(String walletId, String credentialId) {
        if (!store.delete(walletId, credentialId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown credential");
        }
    }

    /**
     * The wallet's public holder key as a JWKS document (empty when the
     * wallet has not redeemed an offer yet).
     */
    public Map<String, Object> holderJwks(String walletId) {
        Map<String, Object> jwks = new LinkedHashMap<>();
        jwks.put("keys", store.getHolderKey(walletId)
                .map(key -> (Object) key.toPublicJWK().toJSONObject())
                .map(List::of)
                .orElse(List.of()));
        return jwks;
    }

    /**
     * Redeems a credential offer (pre-authorized-code grant) and holds
     * the delivered credential: issuer metadata discovery, token
     * exchange, holder-proof credential request, key binding, storage.
     *
     * @param walletId the wallet redeeming the offer
     * @param offer    the credential offer JSON
     * @return the held credential
     */
    public StoredCredential redeemOffer(String walletId, JsonNode offer) {
        String credentialIssuer = requireText(offer, "credential_issuer");
        String vct = offerVct(offer);
        String preAuthCode = offerPreAuthorizedCode(offer);

        OctetKeyPair holderKey = store.getOrCreateHolderKey(walletId);

        JsonNode metadata = client.fetchIssuerMetadata(credentialIssuer);
        String tokenEndpoint = metadata.path("token_endpoint").asText(credentialIssuer + "/token");
        String credentialEndpoint = metadata.path("credential_endpoint").asText(credentialIssuer + "/credential");

        JsonNode tokenResponse = client.tokenRequest(tokenEndpoint, PRE_AUTHORIZED_GRANT,
                "pre-authorized_code", preAuthCode);
        String accessToken = requireText(tokenResponse, "access_token");
        String cNonce = tokenResponse.path("c_nonce").asText(null);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("format", "dc+sd-jwt");
        request.put("vct", vct);
        request.put("proof", Map.of("proof_type", "jwt",
                "jwt", buildProofJwt(holderKey, cNonce, credentialIssuer)));
        JsonNode credentialResponse = client.credentialRequest(credentialEndpoint, accessToken, request);
        String credential = requireText(credentialResponse, "credential");
        String format = credentialResponse.path("format").asText("dc+sd-jwt");

        StoredCredential stored = new StoredCredential();
        stored.setWalletId(walletId);
        stored.setFormat(format);
        stored.setCredential(credential);
        stored.setHolderJwkJson(holderKey.toPublicJWK().toJSONString());
        applySdJwtMetadata(stored, credential);
        if (stored.getVct() == null) {
            stored.setVct(vct);
        }
        store.save(stored);
        LOGGER.info("Wallet {} redeemed offer and holds credential {} (vct={})",
                walletId, stored.getCredentialId(), stored.getVct());
        return stored;
    }

    /**
     * Builds and submits a key-bound presentation for an authorization
     * request: fetches the request object, selects the matching held
     * credential, attaches a key-binding JWT carrying the request nonce
     * and audience, and posts the {@code vp_token} to the verifier's
     * response URI.
     *
     * @param walletId   the wallet presenting
     * @param requestUri the authorization request's {@code request_uri}
     * @return the verification result
     */
    public Map<String, Object> present(String walletId, String requestUri) {
        String requestObject = client.fetchRequestObject(requestUri);
        Map<String, Object> request = parseRequestObject(requestObject);

        String nonce = requireString(request, "nonce");
        String clientId = requireString(request, "client_id");
        String responseUri = requireString(request, "response_uri");
        String vct = requestedVct(request);

        StoredCredential stored = selectCredential(walletId, vct);
        OctetKeyPair holderKey = store.getHolderKey(walletId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "wallet has no holder key; redeem an offer first"));

        String keyBindingJwt;
        try {
            keyBindingJwt = new KeyBindingJwtBuilder().build(
                    holderKey, nonce, clientId, stored.getCredential(), new Date());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "key-binding JWT build failed: " + e.getMessage());
        }
        String vpToken = stored.getCredential() + keyBindingJwt;

        Map<String, Object> submission = new LinkedHashMap<>();
        submission.put("state", lastPathSegment(requestUri));
        submission.put("nonce", nonce);
        submission.put("vp_token", vpToken);
        JsonNode result = client.postPresentation(responseUri, submission);
        Map<String, Object> resultMap = objectMapper.convertValue(result, Map.class);
        LOGGER.info("Wallet {} presented {} for vct={}: valid={}",
                walletId, stored.getCredentialId(), stored.getVct(), resultMap.get("valid"));
        return resultMap;
    }

    /**
     * Selects a held SD-JWT credential matching the requested credential
     * type; the most recently held match wins.
     */
    private StoredCredential selectCredential(String walletId, String requestedVct) {
        StoredCredential match = null;
        for (StoredCredential credential : store.list(walletId)) {
            if (requestedVct == null || requestedVct.equals(credential.getVct())) {
                match = credential;
            }
        }
        if (match == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "wallet holds no credential matching the requested type" +
                            (requestedVct == null ? "" : " " + requestedVct));
        }
        return match;
    }

    /**
     * The requested credential type from the authorization request's
     * DCQL query ({@code credentials[].meta.vct_values}) or plain claims
     * map (its keys).
     */
    private String requestedVct(Map<String, Object> request) {
        Object dcql = request.get("dcql_query");
        if (dcql != null) {
            try {
                JsonNode dcqlNode = dcql instanceof String
                        ? objectMapper.readTree((String) dcql) : objectMapper.valueToTree(dcql);
                for (JsonNode credential : dcqlNode.path("credentials")) {
                    JsonNode vctValues = credential.path("meta").path("vct_values");
                    if (vctValues.isArray() && vctValues.size() > 0) {
                        return vctValues.get(0).asText();
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("Unreadable dcql_query in authorization request", e);
            }
        }
        Object claims = request.get("claims");
        if (claims instanceof Map && !((Map<?, ?>) claims).isEmpty()) {
            return ((Map<?, ?>) claims).keySet().iterator().next().toString();
        }
        return null;
    }

    /**
     * Parses the authorization request object. HS256-signed request
     * objects (this edge's default) are verified against the shared
     * request-signing secret; asymmetric request objects pass through
     * (their verification is the verifier's published-JWKS case).
     */
    private Map<String, Object> parseRequestObject(String requestObject) {
        try {
            SignedJWT jwt = SignedJWT.parse(requestObject);
            if (JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())) {
                boolean verified = jwt.verify(new MACVerifier(
                        properties.getRequestSigningSecret().getBytes(StandardCharsets.UTF_8)));
                if (!verified) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "authorization request object signature is invalid");
                }
            }
            return jwt.getJWTClaimsSet().toJSONObject();
        } catch (ParseException | com.nimbusds.jose.JOSEException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "authorization request object is unreadable: " + e.getMessage());
        }
    }

    private void applySdJwtMetadata(StoredCredential stored, String credential) {
        try {
            SdJwtPresentation presentation = new SdJwtParser().parse(credential);
            Map<String, Object> claims = presentation.getClaims();
            stored.setIssuerDid((String) claims.get("iss"));
            stored.setVct((String) claims.get("vct"));
            stored.setSubjectId((String) claims.get("sub"));
            Number issuedAt = (Number) claims.get("iat");
            Number expiresAt = (Number) claims.get("exp");
            stored.setIssuedAt(issuedAt == null ? null : issuedAt.longValue() * 1000);
            stored.setExpiresAt(expiresAt == null ? null : expiresAt.longValue() * 1000);
            Object status = claims.get("status");
            if (status instanceof Map && ((Map<?, ?>) status).get("status_list") instanceof Map) {
                Map<?, ?> statusList = (Map<?, ?>) ((Map<?, ?>) status).get("status_list");
                Number index = (Number) statusList.get("idx");
                stored.setStatusListIndex(index == null ? null : index.intValue());
                stored.setStatusListId((String) statusList.get("uri"));
            }
        } catch (ParseException | IllegalArgumentException e) {
            LOGGER.warn("Held credential metadata extraction failed: {}", e.getMessage());
        }
    }

    /**
     * OID4VCI key-attestation proof: a {@code openid4vci-proof+jwt}
     * signed with the wallet's holder key, carrying the issuer's
     * {@code c_nonce} and public key in the header.
     */
    private String buildProofJwt(OctetKeyPair holderKey, String cNonce, String audience) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iss", "didvc-wallet");
        payload.put("aud", audience);
        payload.put("iat", System.currentTimeMillis() / 1000);
        if (cNonce != null) {
            payload.put("nonce", cNonce);
        }
        try {
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.EdDSA)
                    .type(new com.nimbusds.jose.JOSEObjectType("openid4vci-proof+jwt"))
                    .jwk(holderKey.toPublicJWK())
                    .build();
            com.nimbusds.jose.JWSObject jws = new com.nimbusds.jose.JWSObject(header,
                    new com.nimbusds.jose.Payload(objectMapper.writeValueAsBytes(payload)));
            jws.sign(new Ed25519Signer(holderKey));
            return jws.serialize();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "proof JWT build failed: " + e.getMessage());
        }
    }

    private String offerVct(JsonNode offer) {
        for (JsonNode credential : offer.path("credentials")) {
            return credential.asText();
        }
        for (JsonNode configId : offer.path("credential_configuration_ids")) {
            return configId.asText();
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "offer carries no credential type");
    }

    private String offerPreAuthorizedCode(JsonNode offer) {
        JsonNode grants = offer.path("grants");
        JsonNode preAuth = grants.path(PRE_AUTHORIZED_GRANT);
        JsonNode code = preAuth.path("pre-authorized_code");
        if (!code.isTextual()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "offer carries no pre-authorized_code grant");
        }
        return code.asText();
    }

    private static String requireText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
        return value.asText();
    }

    private static String requireString(Map<String, Object> map, String field) {
        Object value = map.get(field);
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is missing from the request object");
        }
        return String.valueOf(value);
    }

    private static String lastPathSegment(String uri) {
        String trimmed = uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
        int slash = trimmed.lastIndexOf('/');
        return slash < 0 ? trimmed : trimmed.substring(slash + 1);
    }
}
