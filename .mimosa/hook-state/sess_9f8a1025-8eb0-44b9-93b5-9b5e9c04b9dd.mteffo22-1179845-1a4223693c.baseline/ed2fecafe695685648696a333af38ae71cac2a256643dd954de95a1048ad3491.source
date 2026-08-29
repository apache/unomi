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
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.Ed25519Signer;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.OctetKeyPair;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default in-process key-material provider: keys live in JVM memory for
 * the process lifetime (lost on restart, never persisted). Production
 * deployments replace this with {@link Pkcs11KeyMaterialProvider}.
 */
public class InProcessKeyMaterialProvider implements KeyMaterialProvider {

    private final Map<String, KeyMaterial> keys = new ConcurrentHashMap<>();

    private static final class KeyMaterial {
        private final JWK jwk;
        private final JWSAlgorithm algorithm;

        private KeyMaterial(JWK jwk, JWSAlgorithm algorithm) {
            this.jwk = jwk;
            this.algorithm = algorithm;
        }
    }

    @Override
    public void register(String kid, JWK jwk, JWSAlgorithm algorithm) {
        keys.put(kid, new KeyMaterial(jwk, algorithm));
    }

    @Override
    public String sign(String kid, String payloadJson, String typ) {
        KeyMaterial material = keys.get(kid);
        if (material == null) {
            throw new IllegalStateException("Private key material not available for kid " + kid
                    + ": after a restart, keys must be re-loaded from the HSM/KMS provider");
        }
        try {
            JWSHeader.Builder headerBuilder = new JWSHeader.Builder(material.algorithm).keyID(kid);
            if (typ != null) {
                headerBuilder.type(new com.nimbusds.jose.JOSEObjectType(typ));
            }
            JWSObject jwsObject = new JWSObject(headerBuilder.build(), new Payload(payloadJson));
            jwsObject.sign(signerFor(material.jwk));
            return jwsObject.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Signing failed for kid " + kid, e);
        }
    }

    @Override
    public void remove(String kid) {
        keys.remove(kid);
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
