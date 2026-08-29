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

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jwt.SignedJWT;

import java.security.KeyStore;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;

/**
 * SoftHSM2 signing proof for the PKCS#11 provider (FR-G2 acceptance:
 * signing with SoftHSM2). Not a unit test — orchestrated by
 * {@code didvc/scripts/run-hsm-softhsm2-proof.sh}, which installs
 * SoftHSM2 in a container, initializes a per-run token (random PIN,
 * never a literal), generates the signing key on the token with
 * keytool, and runs this proof against it.
 *
 * <p>Usage: {@code Pkcs11Softhsm2Proof <pkcs11-config> <pin> <kid>} —
 * exits 0 and prints PROOF-OK when the token-signed JWS verifies
 * against the token's public key.</p>
 */
public final class Pkcs11Softhsm2Proof {

    private Pkcs11Softhsm2Proof() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("usage: Pkcs11Softhsm2Proof <pkcs11-config> <pin> <kid>");
            System.exit(2);
        }
        String config = args[0];
        char[] pin = args[1].toCharArray();
        String kid = args[2];

        Pkcs11KeyMaterialProvider provider = new Pkcs11KeyMaterialProvider(config, pin);
        boolean aliasPresent = false;
        KeyStore tokenStore = provider.tokenStore();
        while (tokenStore.aliases().hasMoreElements()) {
            if (kid.equals(tokenStore.aliases().nextElement())) {
                aliasPresent = true;
                break;
            }
        }
        if (!aliasPresent) {
            throw new IllegalStateException("alias " + kid + " not present on the token");
        }

        provider.register(kid, null, JWSAlgorithm.ES256);
        String jws = provider.sign(kid, "{\"vct\":\"hkt_kyc_v1\"}", "vc+sd-jwt");

        PublicKey publicKey = provider.publicKey(kid);
        if (publicKey == null) {
            throw new IllegalStateException("no public key exposed for alias " + kid);
        }
        SignedJWT signedJWT = SignedJWT.parse(jws);
        if (!kid.equals(signedJWT.getHeader().getKeyID())) {
            throw new IllegalStateException("JWS header kid mismatch");
        }
        if (!(publicKey instanceof ECPublicKey ecPublic)) {
            throw new IllegalStateException("token public key is not EC: " + publicKey.getAlgorithm());
        }
        ECKey publicJwk = new ECKey.Builder(Curve.P_256, ecPublic).build();
        if (!signedJWT.verify(new ECDSAVerifier(publicJwk))) {
            throw new IllegalStateException("token-signed JWS failed public-key verification");
        }
        if (jws.contains("\"d\"")) {
            throw new IllegalStateException("private material leaked into the JWS");
        }
        System.out.println("PROOF-OK kid=" + kid);
    }
}
