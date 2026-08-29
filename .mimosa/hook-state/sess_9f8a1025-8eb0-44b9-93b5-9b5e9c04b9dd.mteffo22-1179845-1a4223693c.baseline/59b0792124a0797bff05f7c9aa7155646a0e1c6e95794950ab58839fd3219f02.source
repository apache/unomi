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

package org.apache.unomi.didvc.services;

import org.apache.unomi.didvc.api.DidDocumentData;
import org.apache.unomi.didvc.api.services.UniversalDidResolverService;
import org.apache.unomi.didvc.services.impl.DidKeyMethodResolver;
import org.apache.unomi.didvc.services.impl.UniversalDidResolverServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DID Core / did:key vector suite (T-8.2): identifiers built per the
 * did:key method algorithm (multibase base58btc of the ed25519-pub
 * multicodec {@code 0xed 0x01} + raw key) over the official RFC 8032
 * §7.1 Ed25519 test-vector public keys. The resolver must derive the
 * exact JWK {@code x} (the base64url raw key) for each vector, and the
 * resolved document must satisfy the DID Core shape (id, controller,
 * verificationMethod with an assertionMethod reference).
 */
class DidKeyCoreVectorTest {

    /**
     * Official RFC 8032 §7.1 test-vector public keys, expressed as
     * did:key identifiers and their expected JWK x values.
     */
    private static final String[][] RFC8032_VECTORS = {
            {"did:key:z6MktwupdmLXVVqTzCw4i46r4uGyosGXRnR3XjN4Zq7oMMsw", "11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo"},
            {"did:key:z6MkiaMbhXHNA4eJVCCj8dbzKzTgYDKf6crKgHVHid1F1WCT", "PUAXw-hDiVqStwqnTRt-vJyYLM8uxJaMwM1V8Sr0Zgw"},
            {"did:key:z6MkwSD8dBdqcXQzKJZQFPy2hh2izzxskndKCjdmC2dBpfME", "_FHNjmIYoaONpH7QAjDwWAgW7RO6MwOsXeuRFUiQgCU"},
    };

    private DidKeyMethodResolver didKeyResolver;
    private UniversalDidResolverService universalResolver;

    @BeforeEach
    void setUp() {
        didKeyResolver = new DidKeyMethodResolver();
        UniversalDidResolverServiceImpl impl = new UniversalDidResolverServiceImpl();
        impl.setPersistenceService(MockPersistence.create());
        impl.setDidService(Mockito.mock(org.apache.unomi.didvc.api.services.DidService.class));
        impl.addResolver(didKeyResolver);
        universalResolver = impl;
    }

    @Test
    void resolvesRfc8032KeyMaterialExactly() {
        for (String[] vector : RFC8032_VECTORS) {
            String did = vector[0];
            String expectedX = vector[1];
            DidDocumentData document = didKeyResolver.resolve(did);
            assertNotNull(document, "vector must resolve: " + did);
            assertEquals(did, document.getId());
            // DID Core shape: verificationMethod with controller + JWK
            assertEquals(1, document.getVerificationMethod().size());
            DidDocumentData.VerificationMethod method = document.getVerificationMethod().get(0);
            assertEquals(did, method.getController());
            assertEquals("JsonWebKey2020", method.getType());
            // The exact RFC 8032 public key bytes as the JWK x
            assertEquals("OKP", method.getPublicKeyJwk().get("kty"));
            assertEquals("Ed25519", method.getPublicKeyJwk().get("crv"));
            assertEquals(expectedX, method.getPublicKeyJwk().get("x"));
            // and the x decodes back to exactly 32 key bytes
            byte[] raw = Base64.getUrlDecoder().decode(expectedX);
            assertEquals(32, raw.length);
            // assertionMethod references the verification method (DID Core)
            assertNotNull(document.getAssertionMethod());
            assertTrue(document.getAssertionMethod().contains(method.getId()),
                    "assertionMethod must reference the key");
        }
    }

    @Test
    void universalResolverRoutesDidKeyVectors() {
        for (String[] vector : RFC8032_VECTORS) {
            DidDocumentData document = universalResolver.resolve(vector[0]);
            assertNotNull(document, "universal resolver must route " + vector[0]);
            assertEquals(vector[1], document.getVerificationMethod().get(0).getPublicKeyJwk().get("x"));
        }
    }

    @Test
    void didCoreContextIsPresent() {
        DidDocumentData document = didKeyResolver.resolve(RFC8032_VECTORS[0][0]);
        assertEquals(Arrays.asList("https://www.w3.org/ns/did/v1"), document.getContext());
    }
}
