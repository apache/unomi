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

package org.apache.unomi.didvc.services.impl;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.Ed25519Verifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jwt.SignedJWT;

import java.text.ParseException;
import java.util.List;
import java.util.Map;

/**
 * Parses and verifies the JSON-LD credentials produced by
 * {@link JsonLdVcFormatter}: a compact JWS whose payload is a VC DM 2.0
 * document. Signature verification is performed against the issuer's
 * public JWK (resolved from its DID document).
 */
public class JsonLdVcParser {

    /** A parsed JSON-LD credential. */
    public static class ParsedCredential {
        private final SignedJWT jwt;
        private final Map<String, Object> claims;

        private ParsedCredential(SignedJWT jwt, Map<String, Object> claims) {
            this.jwt = jwt;
            this.claims = claims;
        }

        /** The compact JWS the credential was parsed from. */
        public SignedJWT getJwt() {
            return jwt;
        }

        /** The VC DM 2.0 document as a claim map. */
        public Map<String, Object> getClaims() {
            return claims;
        }

        /** The issuer DID. */
        public String getIssuer() {
            return (String) claims.get("issuer");
        }

        /** The first non-VerifiableCredential type (the credential's vct). */
        public String getCredentialType() {
            Object typeValue = claims.get("type");
            if (typeValue instanceof List) {
                for (Object entry : (List<?>) typeValue) {
                    if (entry instanceof String && !"VerifiableCredential".equals(entry)) {
                        return (String) entry;
                    }
                }
            }
            return null;
        }

        /** The key identifier the credential was signed with. */
        public String getKid() {
            return jwt.getHeader().getKeyID();
        }

        /** The credentialSubject object. */
        @SuppressWarnings("unchecked")
        public Map<String, Object> getCredentialSubject() {
            return (Map<String, Object>) claims.get("credentialSubject");
        }
    }

    /**
     * Parses a compact JWS-wrapped JSON-LD credential.
     *
     * @param compact the credential string
     * @return the parsed credential
     * @throws ParseException when the JWS is unreadable
     */
    public ParsedCredential parse(String compact) throws ParseException {
        if (compact == null || compact.isEmpty()) {
            throw new IllegalArgumentException("Credential must not be empty");
        }
        SignedJWT jwt = SignedJWT.parse(compact);
        Map<String, Object> claims = jwt.getJWTClaimsSet().toJSONObject();
        if (claims.get("credentialSubject") == null || claims.get("type") == null) {
            throw new IllegalArgumentException("Not a VC DM 2.0 document: type and credentialSubject are required");
        }
        return new ParsedCredential(jwt, claims);
    }

    /**
     * Verifies the credential's issuer signature against the issuer's
     * public key.
     *
     * @param credential      the parsed credential
     * @param issuerPublicJwk the issuer's public JWK
     * @return true when the signature validates
     * @throws JOSEException on verification failure
     */
    public boolean verify(ParsedCredential credential, JWK issuerPublicJwk) throws JOSEException {
        if (issuerPublicJwk instanceof OctetKeyPair) {
            return credential.getJwt().verify(new Ed25519Verifier((OctetKeyPair) issuerPublicJwk));
        }
        if (issuerPublicJwk instanceof ECKey) {
            return credential.getJwt().verify(new ECDSAVerifier((ECKey) issuerPublicJwk));
        }
        throw new JOSEException("Unsupported key type: " + issuerPublicJwk.getKeyType());
    }
}
