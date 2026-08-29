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

import org.apache.unomi.didvc.api.services.PairwiseBindingService;
import org.apache.unomi.didvc.services.MockPersistence;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pairwise pseudonyms: different verifiers receive different references for
 * the same profile; resolution is scoped to the verifier tenant.
 */
class PairwiseBindingServiceImplTest {

    private PersistenceService persistenceService;
    private PairwiseBindingService bindingService;

    @BeforeEach
    void setUp() {
        persistenceService = MockPersistence.create();
        bindingService = new PairwiseBindingServiceImpl();
        ((PairwiseBindingServiceImpl) bindingService).setPersistenceService(persistenceService);
    }

    @Test
    void differentVerifiersGetDifferentReferences() {
        String refForBankA = bindingService.getOrCreateOpaqueReference("profile-1", "bank-a");
        String refForBankB = bindingService.getOrCreateOpaqueReference("profile-1", "bank-b");
        assertNotEquals(refForBankA, refForBankB);
    }

    @Test
    void referenceIsStablePerVerifier() {
        String first = bindingService.getOrCreateOpaqueReference("profile-1", "bank-a");
        String second = bindingService.getOrCreateOpaqueReference("profile-1", "bank-a");
        assertEquals(first, second);
    }

    @Test
    void resolveIsScopedToVerifierTenant() {
        String refForBankA = bindingService.getOrCreateOpaqueReference("profile-1", "bank-a");
        assertEquals("profile-1", bindingService.resolveProfileId("bank-a", refForBankA));
        // The same reference must not resolve for another verifier
        assertNull(bindingService.resolveProfileId("bank-b", refForBankA));
    }

    @Test
    void unknownReferenceResolvesNull() {
        assertNull(bindingService.resolveProfileId("bank-a", "didvc:pairwise:unknown"));
    }
}
