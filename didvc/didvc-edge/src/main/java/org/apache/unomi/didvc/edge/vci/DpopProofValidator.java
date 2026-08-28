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

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.Ed25519Verifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.ParseException;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RFC 9449 DPoP proof validation for the credential issuer's token and
 * credential endpoints: header {@code typ}/{@code jwk}, {@code htm} /
 * {@code htu} binding, freshness, replay protection via {@code jti},
 * {@code ath} when accessing with an access token, and the key-binding
 * thumbprint ({@code jkt}) the access token is constrained to.
 */
public class DpopProofValidator {

    /**
     * Maximum allowed age of a DPoP proof {@code iat} (and how far in the
     * future it may be dated, absorbing clock skew).
     */
    static final long IAT_WINDOW_SECONDS = 300;

    private final Map<String, Long> seenJtis = new ConcurrentHashMap<>();

    /**
     * Signals a rejected DPoP proof, carrying the RFC 9449 error code
     * ({@code invalid_dpop_proof} or {@code invalid_token}).
     */
    public static class DpopValidationException extends RuntimeException {
        private final String errorCode;

        public DpopValidationException(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        public String getErrorCode() {
            return errorCode;
        }
    }

    /**
     * Validates a DPoP proof and returns the RFC 7638 thumbprint of the
     * proof's public key — the {@code jkt} an access token is
     * sender-constrained to.
     *
     * @param dpopProof         the DPoP proof JWT (HTTP {@code DPoP} header)
     * @param httpMethod        the HTTP method of the protected request
     * @param targetUri         the canonical URI of the endpoint (no query)
     * @param accessTokenOrNull the access token, to enforce {@code ath};
     *                          null at the token endpoint
     * @return the {@code jkt} of the proof's key
     * @throws DpopValidationException when the proof is invalid
     */
    public String validateJkt(String dpopProof, String httpMethod, String targetUri, String accessTokenOrNull) {
        if (dpopProof == null || dpopProof.isEmpty()) {
            throw new DpopValidationException("invalid_dpop_proof", "DPoP proof is required for this access token");
        }
        SignedJWT jwt;
        try {
            jwt = SignedJWT.parse(dpopProof);
        } catch (ParseException e) {
            throw new DpopValidationException("invalid_dpop_proof", "DPoP proof is not a valid JWT");
        }
        JWSHeader header = jwt.getHeader();
        String typ = header.getType() == null ? null : header.getType().toString();
        if (typ == null || !"dpop+jwt".equalsIgnoreCase(typ)) {
            throw new DpopValidationException("invalid_dpop_proof", "DPoP proof typ must be dpop+jwt");
        }
        JWK jwk = header.getJWK();
        if (jwk == null || jwk.isPrivate()) {
            throw new DpopValidationException("invalid_dpop_proof", "DPoP proof must carry a public jwk header");
        }
        try {
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (!httpMethod.equalsIgnoreCase(claims.getStringClaim("htm"))) {
                throw new DpopValidationException("invalid_dpop_proof", "DPoP htm does not match the request method");
            }
            if (!uriEquals(targetUri, claims.getStringClaim("htu"))) {
                throw new DpopValidationException("invalid_dpop_proof", "DPoP htu does not match the endpoint URI");
            }
            Number iat = claims.getIssueTime() == null ? null : claims.getIssueTime().getTime() / 1000;
            if (iat == null
                    || Math.abs(System.currentTimeMillis() / 1000 - iat.longValue()) > IAT_WINDOW_SECONDS) {
                throw new DpopValidationException("invalid_dpop_proof", "DPoP proof iat is outside the acceptance window");
            }
            String jti = claims.getJWTID();
            if (jti == null || jti.isEmpty()) {
                throw new DpopValidationException("invalid_dpop_proof", "DPoP proof jti is required");
            }
            Long previous = seenJtis.putIfAbsent(jti, System.currentTimeMillis());
            if (previous != null) {
                throw new DpopValidationException("invalid_dpop_proof", "DPoP proof jti was already used");
            }
            purgeExpiredJtis();
            if (accessTokenOrNull != null) {
                String ath = claims.getStringClaim("ath");
                if (ath == null || !ath.equals(sha256Base64Url(accessTokenOrNull))) {
                    throw new DpopValidationException("invalid_dpop_proof", "DPoP ath does not match the access token");
                }
            }
            if (!verifySignature(jwt, jwk)) {
                throw new DpopValidationException("invalid_dpop_proof", "DPoP proof signature is invalid");
            }
            return jwk.computeThumbprint().toString();
        } catch (DpopValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new DpopValidationException("invalid_dpop_proof", "DPoP proof is invalid: " + e.getMessage());
        }
    }

    private boolean verifySignature(SignedJWT jwt, JWK jwk) throws JOSEException {
        JWSVerifier verifier;
        if (jwk instanceof OctetKeyPair) {
            verifier = new Ed25519Verifier((OctetKeyPair) jwk);
        } else if (jwk instanceof ECKey) {
            verifier = new ECDSAVerifier((ECKey) jwk);
        } else {
            throw new JOSEException("Unsupported DPoP key type: " + jwk.getKeyType());
        }
        return jwt.verify(verifier);
    }

    /**
     * RFC 9449 htu comparison: scheme and host case-insensitive, default
     * ports normalized away, query and fragment ignored.
     */
    static boolean uriEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return normalize(expected).equals(normalize(actual));
    }

    private static String normalize(String uri) {
        String trimmed = uri.split("[?#]", 2)[0];
        if (trimmed.contains("://")) {
            String[] parts = trimmed.split("://", 2);
            String scheme = parts[0].toLowerCase();
            String rest = parts[1];
            String[] hostPortPath = rest.split("/", 2);
            String[] hostPort = hostPortPath[0].split(":", 2);
            String host = hostPort[0].toLowerCase();
            String port = "";
            if (hostPort.length == 2 && !hostPort[1].isEmpty()) {
                String defaultPort = "https".equals(scheme) ? "443" : "80";
                if (!defaultPort.equals(hostPort[1])) {
                    port = ":" + hostPort[1];
                }
            }
            String path = hostPortPath.length == 2 ? "/" + hostPortPath[1] : "";
            return scheme + "://" + host + port + path;
        }
        return trimmed;
    }

    static String sha256Base64Url(String value) {
        try {
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private void purgeExpiredJtis() {
        long cutoff = System.currentTimeMillis() - (IAT_WINDOW_SECONDS * 2 * 1000);
        seenJtis.entrySet().removeIf(entry -> entry.getValue() < cutoff);
    }
}
