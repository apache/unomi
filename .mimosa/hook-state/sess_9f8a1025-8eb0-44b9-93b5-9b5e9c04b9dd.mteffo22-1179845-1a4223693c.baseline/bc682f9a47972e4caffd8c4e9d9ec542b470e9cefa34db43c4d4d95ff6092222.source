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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.unomi.didvc.api.DidDocumentData;
import org.apache.unomi.didvc.api.items.DidDocumentRecord;
import org.apache.unomi.didvc.api.services.DidMethodResolver;
import org.apache.unomi.didvc.api.services.DidService;
import org.apache.unomi.didvc.services.MockPersistence;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Universal resolution across DID methods: did:web delegation, did:key
 * derivation, configured method adapters, and the persisted-registry
 * fallback that serves stub documents for methods without a live driver.
 */
class UniversalDidResolverServiceImplTest {

    private PersistenceService persistenceService;
    private DidService didService;
    private UniversalDidResolverServiceImpl service;

    @BeforeEach
    void setUp() {
        persistenceService = MockPersistence.create();
        didService = Mockito.mock(DidService.class);
        service = new UniversalDidResolverServiceImpl();
        service.setPersistenceService(persistenceService);
        service.setDidService(didService);
    }

    @Test
    void didWebDelegatesToDidService() {
        DidDocumentData document = new DidDocumentData();
        document.setId("did:web:example.hkt");
        Mockito.when(didService.resolveDid("did:web:example.hkt")).thenReturn(document);

        assertSame(document, service.resolve("did:web:example.hkt"));
    }

    @Test
    void registryFallbackServesStubDocumentsForExternalMethods() throws Exception {
        DidDocumentData stub = new DidDocumentData();
        stub.setContext(Arrays.asList("https://www.w3.org/ns/did/v1"));
        stub.setId("did:iamsmart:stub.example.hkt:profile:abc123");
        DidDocumentRecord record = new DidDocumentRecord(stub.getId());
        record.setJson(new ObjectMapper().writeValueAsString(stub));
        record.setTenantId("hkt");
        record.setScope("didvc");
        persistenceService.save(record);

        DidDocumentData resolved = service.resolve(stub.getId());
        assertNotNull(resolved);
        assertEquals(stub.getId(), resolved.getId());
        assertEquals(stub.getContext(), resolved.getContext());
    }

    @Test
    void unknownDidReturnsNull() {
        assertNull(service.resolve("did:realdid:unknown.example.hkt:sub"));
    }

    @Test
    void deactivatedRegistryEntryDoesNotResolve() throws Exception {
        DidDocumentData stub = new DidDocumentData();
        stub.setId("did:realdid:dead.example.hkt:sub");
        DidDocumentRecord record = new DidDocumentRecord(stub.getId());
        record.setJson(new ObjectMapper().writeValueAsString(stub));
        record.setDeactivated(true);
        persistenceService.save(record);

        assertNull(service.resolve(stub.getId()));
    }

    @Test
    void adapterRegisteredForItsMethodWinsOverRegistry() {
        DidMethodResolver adapter = Mockito.mock(DidMethodResolver.class);
        Mockito.when(adapter.getMethod()).thenReturn("iamsmart");
        DidDocumentData document = new DidDocumentData();
        document.setId("did:iamsmart:from-adapter");
        Mockito.when(adapter.resolve("did:iamsmart:from-adapter")).thenReturn(document);
        service.addResolver(adapter);

        assertSame(document, service.resolve("did:iamsmart:from-adapter"));
        service.removeResolver(adapter);
        assertNull(service.resolve("did:iamsmart:from-adapter"));
    }

    @Test
    void methodOfParsesDid() {
        assertEquals("web", UniversalDidResolverServiceImpl.methodOf("did:web:example.com"));
        assertEquals("key", UniversalDidResolverServiceImpl.methodOf("did:key:z6Mkfoo"));
        assertEquals("", UniversalDidResolverServiceImpl.methodOf("did"));
        assertEquals("", UniversalDidResolverServiceImpl.methodOf("not-a-did"));
    }
}
