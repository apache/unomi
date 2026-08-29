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

import org.apache.unomi.didvc.api.items.TrustEntry;
import org.apache.unomi.didvc.api.services.TrustRegistryService;
import org.apache.unomi.didvc.services.MockPersistence;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Trust-registry enforcement: active, accredited, unexpired entries pass;
 * untrusted issuers, unknown types, expired windows and revoked entries
 * are rejected.
 */
class TrustRegistryServiceImplTest {

    private static final long NOW = 1_700_000_000_000L;

    private PersistenceService persistenceService;
    private TrustRegistryService trustRegistryService;

    @BeforeEach
    void setUp() {
        persistenceService = MockPersistence.create();
        trustRegistryService = new TrustRegistryServiceImpl();
        ((TrustRegistryServiceImpl) trustRegistryService).setPersistenceService(persistenceService);
    }

    private TrustEntry activeEntry(String entryId, String verifierTenantId, String issuerDid, String vct) {
        TrustEntry entry = new TrustEntry(entryId);
        entry.setTenantId(verifierTenantId);
        entry.setIssuerDid(issuerDid);
        entry.setVct(vct);
        entry.setAccreditationLevel("accredited");
        entry.setValidFrom(new Date(NOW - 1000));
        entry.setValidUntil(new Date(NOW + 86_400_000L));
        entry.setStatus("active");
        return entry;
    }

    @Test
    void saveGetDelete() {
        trustRegistryService.saveTrustEntry(activeEntry("trust-1", "bank-a", "did:web:id.example.hkt", "hkt_kyc_v1"));
        TrustEntry loaded = trustRegistryService.getTrustEntry("trust-1");
        assertNotNull(loaded);
        assertEquals("accredited", loaded.getAccreditationLevel());
        trustRegistryService.deleteTrustEntry("trust-1");
        assertNull(trustRegistryService.getTrustEntry("trust-1"));
    }

    @Test
    void trustedIssuerPasses() {
        trustRegistryService.saveTrustEntry(activeEntry("trust-1", "bank-a", "did:web:id.example.hkt", "hkt_kyc_v1"));
        assertTrue(trustRegistryService.isTrusted("bank-a", "did:web:id.example.hkt", "hkt_kyc_v1", new Date(NOW)));
    }

    @Test
    void untrustedIssuerRejected() {
        trustRegistryService.saveTrustEntry(activeEntry("trust-1", "bank-a", "did:web:id.example.hkt", "hkt_kyc_v1"));
        assertFalse(trustRegistryService.isTrusted("bank-a", "did:web:attacker.example.hkt", "hkt_kyc_v1", new Date(NOW)));
    }

    @Test
    void unknownCredentialTypeRejected() {
        trustRegistryService.saveTrustEntry(activeEntry("trust-1", "bank-a", "did:web:id.example.hkt", "hkt_kyc_v1"));
        assertFalse(trustRegistryService.isTrusted("bank-a", "did:web:id.example.hkt", "hkt_profcred_v1", new Date(NOW)));
    }

    @Test
    void otherTenantRejected() {
        trustRegistryService.saveTrustEntry(activeEntry("trust-1", "bank-a", "did:web:id.example.hkt", "hkt_kyc_v1"));
        assertFalse(trustRegistryService.isTrusted("bank-b", "did:web:id.example.hkt", "hkt_kyc_v1", new Date(NOW)));
    }

    @Test
    void expiredEntryRejected() {
        TrustEntry entry = activeEntry("trust-1", "bank-a", "did:web:id.example.hkt", "hkt_kyc_v1");
        entry.setValidUntil(new Date(NOW - 1000));
        trustRegistryService.saveTrustEntry(entry);
        assertFalse(trustRegistryService.isTrusted("bank-a", "did:web:id.example.hkt", "hkt_kyc_v1", new Date(NOW)));
    }

    @Test
    void notYetValidEntryRejected() {
        TrustEntry entry = activeEntry("trust-1", "bank-a", "did:web:id.example.hkt", "hkt_kyc_v1");
        entry.setValidFrom(new Date(NOW + 1000));
        trustRegistryService.saveTrustEntry(entry);
        assertFalse(trustRegistryService.isTrusted("bank-a", "did:web:id.example.hkt", "hkt_kyc_v1", new Date(NOW)));
    }

    @Test
    void revokedEntryRejected() {
        TrustEntry entry = activeEntry("trust-1", "bank-a", "did:web:id.example.hkt", "hkt_kyc_v1");
        entry.setStatus("revoked");
        trustRegistryService.saveTrustEntry(entry);
        assertFalse(trustRegistryService.isTrusted("bank-a", "did:web:id.example.hkt", "hkt_kyc_v1", new Date(NOW)));
    }

    private void assertEquals(String expected, String actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}
