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

package org.apache.unomi.didvc.sdjwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
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
 * A parsed SD-JWT presentation: the signed credential, its disclosed claims,
 * and — when present — the key-binding JWT proving holder possession.
 */
public class SdJwtPresentation {

    /**
     * Maximum allowed age of a key-binding JWT.
     */
    public static final long MAX_KEY_BINDING_AGE_SECONDS = 300;

    private final SignedJWT credential;
    private final Map<String, Object> claims;
    private final Map<String, Object> disclosedClaims;
    private final List<String> disclosures;
    private final SignedJWT keyBindingJwt;
    private final Map<String, Object> keyBindingClaims;

    SdJwtPresentation(SignedJWT credential, Map<String, Object> claims, Map<String, Object> disclosedClaims,
                      List<String> disclosures, SignedJWT keyBindingJwt, Map<String, Object> keyBindingClaims) {
        this.credential = credential;
        this.claims = claims;
        this.disclosedClaims = disclosedClaims;
        this.disclosures = disclosures;
        this.keyBindingJwt = keyBindingJwt;
        this.keyBindingClaims = keyBindingClaims;
    }

    public SignedJWT getCredential() {
        return credential;
    }

    /**
     * The signed payload claims, including {@code vct}, {@code iss}, time
     * claims, {@code status}, {@code cnf} and {@code _sd}.
     */
    public Map<String, Object> getClaims() {
        return claims;
    }

    /**
     * The Processed SD-JWT Payload (RFC 9901 §8.3): a copy of the signed
     * payload with {@code _sd}/{@code _sd_alg} removed and every presented
     * disclosed claim inserted at its position — nested objects get keys
     * added, array-entry placeholders are replaced by (or, when not
     * disclosed, dropped in favour of) the disclosed values.
     */
    public Map<String, Object> getDisclosedClaims() {
        return disclosedClaims;
    }

    public List<String> getDisclosures() {
        return disclosures;
    }

    public SignedJWT getKeyBindingJwt() {
        return keyBindingJwt;
    }

    public Map<String, Object> getKeyBindingClaims() {
        return keyBindingClaims;
    }

    /**
     * Verifies the credential signature against the issuer's public JWK.
     *
     * @param issuerPublicJwk the issuer public JWK
     * @return true when the signature validates
     * @throws JOSEException on JOSE errors
     */
    public boolean verifySignature(JWK issuerPublicJwk) throws JOSEException {
        return credential.verify(verifierFor(issuerPublicJwk));
    }

    /**
     * Verifies the key-binding JWT: holder signature against the
     * {@code cnf.jwk} key, {@code sd_hash} integrity over the presented
     * disclosures, nonce and audience binding, and freshness.
     *
     * @param expectedNonce    the nonce the verifier issued; may be null to skip
     * @param expectedAudience the verifier's identifier; may be null to skip
     * @param nowEpochSeconds  the current time in epoch seconds
     * @throws JOSEException    on JOSE errors
     * @throws SecurityException on any key-binding violation
     */
    @SuppressWarnings("unchecked")
    public void verifyKeyBinding(String expectedNonce, String expectedAudience, long nowEpochSeconds)
            throws JOSEException {
        if (keyBindingJwt == null || keyBindingClaims == null) {
            throw new SecurityException("Key binding JWT is missing");
        }
        Map<String, Object> cnf = (Map<String, Object>) claims.get("cnf");
        if (cnf == null || cnf.get("jwk") == null) {
            throw new SecurityException("Credential has no confirmation method (cnf.jwk)");
        }
        JWK holderJwk;
        try {
            holderJwk = JWK.parse((Map<String, Object>) cnf.get("jwk"));
        } catch (ParseException e) {
            throw new SecurityException("Holder JWK in cnf is unreadable", e);
        }
        if (!keyBindingJwt.verify(verifierFor(holderJwk))) {
            throw new SecurityException("Key binding JWT signature is invalid");
        }
        // RFC 9901 §4.3.1: sd_hash covers the Issuer-signed JWT and every
        // presented disclosure, each followed by a tilde — exactly the
        // presentation as received, minus the KB-JWT part
        StringBuilder preKeyBinding = new StringBuilder(credential.serialize());
        for (String disclosure : disclosures) {
            preKeyBinding.append('~').append(disclosure);
        }
        preKeyBinding.append('~');
        String expectedSdHash = SdJwtDigest.hashOfSdJwt(preKeyBinding.toString());
        String sdHash = (String) keyBindingClaims.get("sd_hash");
        if (!expectedSdHash.equals(sdHash)) {
            throw new SecurityException("sd_hash does not cover the presented disclosures");
        }
        if (expectedNonce != null && !expectedNonce.equals(keyBindingClaims.get("nonce"))) {
            throw new SecurityException("Key binding nonce does not match the verifier nonce");
        }
        if (expectedAudience != null && !expectedAudience.equals(keyBindingClaims.get("aud"))) {
            throw new SecurityException("Key binding audience does not match the verifier");
        }
        Number iat = (Number) keyBindingClaims.get("iat");
        if (iat == null || Math.abs(nowEpochSeconds - iat.longValue()) > MAX_KEY_BINDING_AGE_SECONDS) {
            throw new SecurityException("Key binding JWT is too old");
        }
    }

    private static JWSVerifier verifierFor(JWK jwk) throws JOSEException {
        if (jwk instanceof OctetKeyPair) {
            return new Ed25519Verifier((OctetKeyPair) jwk);
        }
        if (jwk instanceof ECKey) {
            return new ECDSAVerifier((ECKey) jwk);
        }
        throw new JOSEException("Unsupported key type: " + jwk.getKeyType());
    }
}
