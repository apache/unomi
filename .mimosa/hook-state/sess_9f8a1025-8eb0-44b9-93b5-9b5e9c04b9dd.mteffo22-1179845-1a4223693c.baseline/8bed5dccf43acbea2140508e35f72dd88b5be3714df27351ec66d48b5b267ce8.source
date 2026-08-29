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

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator;
import org.apache.unomi.didvc.api.DidDocumentData;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code did:key} resolution: identifier decoding (multibase base58btc +
 * multicodec ed25519-pub) and DID-document derivation.
 */
class DidKeyMethodResolverTest {

    private final DidKeyMethodResolver resolver = new DidKeyMethodResolver();

    @Test
    void resolvesEd25519DidKey() throws Exception {
        OctetKeyPair key = new OctetKeyPairGenerator(Curve.Ed25519).generate();
        byte[] raw = key.toPublicJWK().getX().decode();
        byte[] multicodec = new byte[raw.length + 2];
        multicodec[0] = (byte) 0xed;
        multicodec[1] = 0x01;
        System.arraycopy(raw, 0, multicodec, 2, raw.length);
        String did = "did:key:z" + DidKeyMethodResolver.Base58.encode(multicodec);

        DidDocumentData document = resolver.resolve(did);
        assertNotNull(document);
        assertEquals(did, document.getId());
        assertEquals(Arrays.asList("https://www.w3.org/ns/did/v1"), document.getContext());
        assertEquals(1, document.getVerificationMethod().size());
        DidDocumentData.VerificationMethod method = document.getVerificationMethod().get(0);
        assertEquals("JsonWebKey2020", method.getType());
        assertEquals(did, method.getController());
        assertEquals("OKP", method.getPublicKeyJwk().get("kty"));
        assertEquals("Ed25519", method.getPublicKeyJwk().get("crv"));
        assertEquals(key.toPublicJWK().getX().toString(), method.getPublicKeyJwk().get("x"));
        assertEquals(Arrays.asList(method.getId()), document.getAssertionMethod());
    }

    @Test
    void unknownMethodReturnsNull() {
        assertNull(resolver.resolve("did:web:example.com"));
    }

    @Test
    void invalidEncodingReturnsNull() {
        assertNull(resolver.resolve("did:key:not-base58!"));
    }

    @Test
    void base58RoundTrips() {
        byte[] data = "didvc test vector".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String encoded = DidKeyMethodResolver.Base58.encode(data);
        byte[] decoded = DidKeyMethodResolver.Base58.decode(encoded);
        assertTrue(Arrays.equals(data, decoded));
    }
}
