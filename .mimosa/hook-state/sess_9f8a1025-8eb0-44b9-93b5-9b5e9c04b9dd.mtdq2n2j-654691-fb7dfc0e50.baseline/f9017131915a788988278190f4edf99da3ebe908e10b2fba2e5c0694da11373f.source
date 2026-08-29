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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.Curve;
import org.apache.unomi.didvc.api.items.KeyDescriptor;
import org.apache.unomi.didvc.api.services.IssuerKeyService;
import org.apache.unomi.didvc.services.MockPersistence;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issuer key lifecycle: generation (Ed25519/ES256), RFC 7638 thumbprint kids,
 * JWS signing/verification, and the guarantee that private key material is
 * never persisted.
 */
class IssuerKeyServiceImplTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private PersistenceService persistenceService;
    private IssuerKeyService keyService;

    @BeforeEach
    void setUp() {
        persistenceService = MockPersistence.create();
        keyService = new IssuerKeyServiceImpl();
        ((IssuerKeyServiceImpl) keyService).setPersistenceService(persistenceService);
    }

    @Test
    void generateEdDsaKey() throws Exception {
        KeyDescriptor key = keyService.generateKey("hkt", "did:web:example.hkt", "EdDSA");

        assertNotNull(key.getKid());
        assertEquals("EdDSA", key.getAlg());
        assertEquals("OKP", key.getKeyType());
        assertEquals("did:web:example.hkt", key.getIssuerDid());
        assertNotNull(key.getRotationDueDate());
        assertEquals("hkt", key.getTenantId());

        // The kid must be the RFC 7638 JWK thumbprint of the public key
        OctetKeyPair publicJwk = OctetKeyPair.parse(key.getPublicJwk());
        assertEquals(publicJwk.computeThumbprint().toString(), key.getKid());
        assertEquals(Curve.Ed25519, publicJwk.getCurve());

        // Public JWK must carry no private material
        JsonNode node = objectMapper.readTree(key.getPublicJwk());
        assertFalse(node.has("d"), "public JWK must never contain private key material");
    }

    @Test
    void generateEs256Key() throws Exception {
        KeyDescriptor key = keyService.generateKey("hkt", "did:web:example.hkt", "ES256");
        assertEquals("ES256", key.getAlg());
        assertEquals("EC", key.getKeyType());
        assertFalse(objectMapper.readTree(key.getPublicJwk()).has("d"));
    }

    @Test
    void unsupportedAlgorithmRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> keyService.generateKey("hkt", "did:web:example.hkt", "RS256"));
    }

    @Test
    void signAndVerifyEdDsa() {
        KeyDescriptor key = keyService.generateKey("hkt", "did:web:example.hkt", "EdDSA");
        String jws = keyService.sign(key.getKid(), "{\"claim\":\"value\"}");
        assertTrue(jws.startsWith("eyJ"), "expected compact JWS");
        assertTrue(keyService.verify(key.getKid(), jws));
    }

    @Test
    void signAndVerifyEs256() {
        KeyDescriptor key = keyService.generateKey("hkt", "did:web:example.hkt", "ES256");
        String jws = keyService.sign(key.getKid(), "{\"claim\":\"value\"}");
        assertTrue(keyService.verify(key.getKid(), jws));
    }

    @Test
    void tamperedSignatureRejected() {
        KeyDescriptor key = keyService.generateKey("hkt", "did:web:example.hkt", "EdDSA");
        String jws = keyService.sign(key.getKid(), "{\"claim\":\"value\"}");
        String[] parts = jws.split("\\.");
        String tamperedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"claim\":\"tampered\"}".getBytes());
        assertFalse(keyService.verify(key.getKid(), parts[0] + "." + tamperedPayload + "." + parts[2]));
    }

    @Test
    void verifyWithUnknownKidReturnsFalse() {
        assertFalse(keyService.verify("unknown-kid", "eyJhbGciOiJFZERTQSJ9.e30.signature"));
    }

    @Test
    void signRequiresInMemoryKeyMaterial() {
        // The key descriptor exists in persistence but the private material is
        // only in the provider — a fresh service instance has no material.
        keyService.generateKey("hkt", "did:web:example.hkt", "EdDSA");
        IssuerKeyService restarted = new IssuerKeyServiceImpl();
        ((IssuerKeyServiceImpl) restarted).setPersistenceService(persistenceService);
        KeyDescriptor key = restarted.getKeys("hkt").get(0);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> restarted.sign(key.getKid(), "{}"));
        assertTrue(e.getMessage().contains("HSM/KMS"), "error must point at the key-material provider");
    }

    @Test
    void getAndDeleteKey() {
        KeyDescriptor key = keyService.generateKey("hkt", "did:web:example.hkt", "EdDSA");
        assertEquals(key.getKid(), keyService.getKey(key.getKid()).getKid());
        keyService.deleteKey(key.getKid());
        assertNull(keyService.getKey(key.getKid()));
    }

    @Test
    void keysScopedByTenant() {
        keyService.generateKey("hkt", "did:web:example.hkt", "EdDSA");
        keyService.generateKey("bank-a", "did:web:bank-a.example.hkt", "EdDSA");
        List<KeyDescriptor> hktKeys = keyService.getKeys("hkt");
        assertEquals(1, hktKeys.size());
        assertEquals("hkt", hktKeys.get(0).getTenantId());
    }
}
