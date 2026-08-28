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

import com.nimbusds.jose.crypto.Ed25519Verifier;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jwt.SignedJWT;
import org.apache.unomi.didvc.api.items.KeyDescriptor;
import org.apache.unomi.didvc.api.items.StatusListRecord;
import org.apache.unomi.didvc.api.services.IssuerKeyService;
import org.apache.unomi.didvc.api.services.StatusService;
import org.apache.unomi.didvc.services.MockPersistence;
import org.apache.unomi.didvc.services.util.BitstringCodec;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bitstring Status List management: index allocation, revocation, expansion,
 * signed publication (BitstringStatusList and StatusList2021 adapter).
 */
class StatusServiceImplTest {

    private PersistenceService persistenceService;
    private IssuerKeyService keyService;
    private StatusService statusService;

    @BeforeEach
    void setUp() {
        persistenceService = MockPersistence.create();
        keyService = new IssuerKeyServiceImpl();
        ((IssuerKeyServiceImpl) keyService).setPersistenceService(persistenceService);
        statusService = new StatusServiceImpl();
        ((StatusServiceImpl) statusService).setPersistenceService(persistenceService);
        ((StatusServiceImpl) statusService).setIssuerKeyService(keyService);
    }

    private StatusListRecord createList(String purpose, int size) {
        return statusService.createStatusList("hkt", "did:web:example.hkt", purpose, size);
    }

    @Test
    void createStatusList() {
        StatusListRecord record = createList("revocation", 16);
        assertEquals("revocation", record.getStatusPurpose());
        assertEquals(16, record.getSize());
        assertEquals(0, record.getNextIndex());
        assertEquals(2, BitstringCodec.decode(record.getEncodedList()).length);
        assertNotNull(record.getStatusListId());
        assertEquals("hkt", record.getTenantId());
    }

    @Test
    void createRejectsUnknownPurposeAndZeroSize() {
        assertThrows(IllegalArgumentException.class,
                () -> statusService.createStatusList("hkt", "did:web:example.hkt", "expiry", 16));
        assertThrows(IllegalArgumentException.class,
                () -> statusService.createStatusList("hkt", "did:web:example.hkt", "revocation", 0));
    }

    @Test
    void allocateAndRevoke() {
        StatusListRecord record = createList("revocation", 16);
        String id = record.getItemId();
        assertEquals(0, statusService.allocateIndex(id));
        assertEquals(1, statusService.allocateIndex(id));
        assertFalse(statusService.isRevoked(id, 0));
        statusService.revoke(id, 0);
        assertTrue(statusService.isRevoked(id, 0));
        assertFalse(statusService.isRevoked(id, 1));
        // revocation is idempotent
        statusService.revoke(id, 0);
        assertTrue(statusService.isRevoked(id, 0));
    }

    @Test
    void listExpandsBeyondInitialSize() {
        StatusListRecord record = createList("revocation", 8);
        String id = record.getItemId();
        for (int i = 0; i < 20; i++) {
            assertEquals(i, statusService.allocateIndex(id));
        }
        StatusListRecord reloaded = statusService.getStatusList(id);
        assertTrue(reloaded.getSize() >= 20);
        statusService.revoke(id, 19);
        assertTrue(statusService.isRevoked(id, 19));
    }

    @Test
    void revokeOutOfRangeRejected() {
        StatusListRecord record = createList("revocation", 8);
        assertThrows(IndexOutOfBoundsException.class, () -> statusService.revoke(record.getItemId(), 8));
    }

    @Test
    void unknownListRejected() {
        assertThrows(IllegalArgumentException.class, () -> statusService.allocateIndex("unknown"));
        assertThrows(IllegalArgumentException.class, () -> statusService.revoke("unknown", 0));
        assertThrows(IllegalArgumentException.class, () -> statusService.publish("unknown", "kid"));
    }

    @Test
    void publishSignsStatusListJwt() throws Exception {
        KeyDescriptor key = keyService.generateKey("hkt", "did:web:example.hkt", "EdDSA");
        StatusListRecord record = createList("revocation", 16);
        statusService.allocateIndex(record.getItemId());
        statusService.revoke(record.getItemId(), 0);

        String jwt = statusService.publish(record.getItemId(), key.getKid());

        SignedJWT signedJWT = SignedJWT.parse(jwt);
        assertTrue(signedJWT.verify(new Ed25519Verifier(OctetKeyPair.parse(key.getPublicJwk()))));
        assertEquals("BitstringStatusList", signedJWT.getJWTClaimsSet().getStringClaim("type"));
        assertEquals("revocation", signedJWT.getJWTClaimsSet().getStringClaim("statusPurpose"));
        String encodedList = signedJWT.getJWTClaimsSet().getStringClaim("encodedList");
        byte[] bits = BitstringCodec.decode(encodedList);
        assertTrue(BitstringCodec.getBit(bits, 0), "published list must carry the revocation bit");

        // republish with the same kid is stable and stored
        assertEquals(jwt, statusService.publish(record.getItemId(), key.getKid()));
        assertEquals(jwt, statusService.getStatusList(record.getItemId()).getSignedJwt());
    }

    @Test
    void buildStatusList2021JwtAdapter() throws Exception {
        KeyDescriptor key = keyService.generateKey("hkt", "did:web:example.hkt", "EdDSA");
        StatusListRecord record = createList("revocation", 16);
        statusService.allocateIndex(record.getItemId());
        statusService.revoke(record.getItemId(), 0);

        String jwt = statusService.buildStatusList2021Jwt(record.getItemId(), key.getKid());

        SignedJWT signedJWT = SignedJWT.parse(jwt);
        assertTrue(signedJWT.verify(new Ed25519Verifier(OctetKeyPair.parse(key.getPublicJwk()))));
        List<String> types = signedJWT.getJWTClaimsSet().getStringListClaim("type");
        assertTrue(types.contains("StatusList2021Credential"));
        assertEquals("did:web:example.hkt", signedJWT.getJWTClaimsSet().getIssuer());
        Map<String, Object> credentialSubject = signedJWT.getJWTClaimsSet().getJSONObjectClaim("credentialSubject");
        byte[] bits = BitstringCodec.decode((String) credentialSubject.get("encodedList"));
        assertTrue(BitstringCodec.getBit(bits, 0));
    }
}
