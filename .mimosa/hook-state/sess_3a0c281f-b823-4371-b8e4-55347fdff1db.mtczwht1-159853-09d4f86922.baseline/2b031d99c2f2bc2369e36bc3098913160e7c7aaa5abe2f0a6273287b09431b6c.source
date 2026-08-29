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

import org.apache.unomi.didvc.api.DidDocumentData;
import org.apache.unomi.didvc.api.services.DidService;
import org.apache.unomi.didvc.api.services.IssuerKeyService;
import org.apache.unomi.didvc.services.MockPersistence;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * did:web lifecycle: creation, resolution, key rotation, deactivation.
 * Runs against a mock persistence store with real key generation, so the
 * full create → resolve → rotate → deactivate round trip is exercised.
 */
class DidServiceImplTest {

    private PersistenceService persistenceService;
    private IssuerKeyService keyService;
    private DidService didService;

    @BeforeEach
    void setUp() {
        persistenceService = MockPersistence.create();
        keyService = new IssuerKeyServiceImpl();
        ((IssuerKeyServiceImpl) keyService).setPersistenceService(persistenceService);
        didService = new DidServiceImpl();
        ((DidServiceImpl) didService).setPersistenceService(persistenceService);
        ((DidServiceImpl) didService).setIssuerKeyService(keyService);
    }

    @Test
    void createAndResolve() {
        DidDocumentData doc = didService.createDid("hkt", "id.example.hkt", null, "EdDSA");
        assertEquals("did:web:id.example.hkt", doc.getId());
        assertNotNull(doc.getContext());
        assertEquals(1, doc.getVerificationMethod().size());
        assertEquals("did:web:id.example.hkt#" + doc.getVerificationMethod().get(0).getId().split("#")[1],
                doc.getAssertionMethod().get(0));
        assertEquals("OKP", doc.getVerificationMethod().get(0).getPublicKeyJwk().get("kty"));
        assertNotNull(doc.getService());
        assertEquals("https://id.example.hkt/didvc", doc.getService().get(0).getServiceEndpoint());

        DidDocumentData resolved = didService.resolveDid("did:web:id.example.hkt");
        assertEquals(doc.getId(), resolved.getId());
        assertEquals(doc.getVerificationMethod().size(), resolved.getVerificationMethod().size());
    }

    @Test
    void didWebIdEncodesPathSegments() {
        DidDocumentData doc = didService.createDid("hkt", "id.example.hkt", "issuers/bank-a", "ES256");
        assertEquals("did:web:id.example.hkt:issuers:bank-a", doc.getId());
        assertEquals("EC", doc.getVerificationMethod().get(0).getPublicKeyJwk().get("kty"));
    }

    @Test
    void duplicateCreateRejected() {
        didService.createDid("hkt", "id.example.hkt", null, "EdDSA");
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> didService.createDid("hkt", "id.example.hkt", null, "EdDSA"));
        assertTrue(e.getMessage().contains("already exists"));
    }

    @Test
    void rotateAddsVerificationMethod() {
        DidDocumentData doc = didService.createDid("hkt", "id.example.hkt", null, "EdDSA");
        DidDocumentData rotated = didService.rotateKey("did:web:id.example.hkt", "ES256");
        assertEquals(2, rotated.getVerificationMethod().size());
        assertEquals(2, rotated.getAssertionMethod().size());
        assertEquals(2, didService.resolveDid(doc.getId()).getVerificationMethod().size());
    }

    @Test
    void deactivateMakesDidUnresolvable() {
        didService.createDid("hkt", "id.example.hkt", null, "EdDSA");
        DidDocumentData deactivated = didService.deactivateDid("did:web:id.example.hkt");
        assertNotNull(deactivated);
        assertTrue(deactivated.getService().isEmpty());
        assertNull(didService.resolveDid("did:web:id.example.hkt"));
        assertNull(didService.deactivateDid("did:web:id.example.hkt"), "second deactivation resolves nothing");
    }

    @Test
    void resolveUnknownDidReturnsNull() {
        assertNull(didService.resolveDid("did:web:unknown.example.hkt"));
    }

    @Test
    void listDidsScopedByTenant() {
        didService.createDid("hkt", "id.example.hkt", null, "EdDSA");
        didService.createDid("bank-a", "id.bank-a.example.hkt", null, "EdDSA");
        List<DidDocumentData> hktDids = didService.listDids("hkt");
        assertEquals(1, hktDids.size());
        assertEquals("did:web:id.example.hkt", hktDids.get(0).getId());
        assertFalse(didService.listDids("bank-a").isEmpty());
    }
}
