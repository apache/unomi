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
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.Ed25519Signer;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.OctetKeyPair;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the key-binding JWT a holder attaches to an SD-JWT presentation:
 * signed with the holder's key bound in {@code cnf.jwk}, carrying the
 * verifier's nonce and audience and the {@code sd_hash} covering the
 * presented disclosures.
 */
public class KeyBindingJwtBuilder {

    /**
     * Builds a key-binding JWT.
     *
     * @param holderPrivateJwk      the holder's private JWK (bound in cnf.jwk)
     * @param nonce                 the verifier nonce
     * @param audience              the verifier identifier
     * @param sdJwtWithoutKeyBinding the exact presentation string covered by
     *                              {@code sd_hash} (RFC 9901 §4.3.1):
     *                              {@code <JWT>~<d1>~...~<dn>~}
     * @param issuedAt              issuance time
     * @return the compact key-binding JWT
     * @throws JOSEException on signing failure
     */
    public String build(JWK holderPrivateJwk, String nonce, String audience, String sdJwtWithoutKeyBinding,
                        Date issuedAt)
            throws JOSEException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("nonce", nonce);
        payload.put("aud", audience);
        payload.put("iat", issuedAt.getTime() / 1000);
        payload.put("sd_hash", SdJwtDigest.hashOfSdJwt(sdJwtWithoutKeyBinding));

        JWSAlgorithm algorithm = holderPrivateJwk instanceof OctetKeyPair ? JWSAlgorithm.EdDSA : JWSAlgorithm.ES256;
        JWSHeader header = new JWSHeader.Builder(algorithm)
                .type(new com.nimbusds.jose.JOSEObjectType("kb+jwt"))
                .build();
        com.nimbusds.jose.JWSObject jwsObject = new com.nimbusds.jose.JWSObject(header,
                new Payload(SdJwtDigest.toJsonBytes(payload)));
        jwsObject.sign(signerFor(holderPrivateJwk));
        return jwsObject.serialize();
    }

    private static JWSSigner signerFor(JWK jwk) throws JOSEException {
        if (jwk instanceof OctetKeyPair) {
            return new Ed25519Signer((OctetKeyPair) jwk);
        }
        if (jwk instanceof ECKey) {
            return new ECDSASigner((ECKey) jwk);
        }
        throw new JOSEException("Unsupported key type: " + jwk.getKeyType());
    }
}
