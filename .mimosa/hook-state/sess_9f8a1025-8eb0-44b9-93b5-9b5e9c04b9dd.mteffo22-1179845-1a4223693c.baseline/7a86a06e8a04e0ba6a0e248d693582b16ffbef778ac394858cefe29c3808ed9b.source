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

import org.apache.unomi.didvc.api.items.ReidentificationRequest;
import org.apache.unomi.didvc.api.services.PairwiseBindingService;
import org.apache.unomi.didvc.api.services.SplitKnowledgeCustodian;
import org.apache.unomi.didvc.api.services.SplitKnowledgeService;
import org.apache.unomi.didvc.services.MockPersistence;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Split-knowledge re-identification workflow (FR-G4): neither custodian
 * alone can resolve a subject; duplicate approvals do not advance the
 * workflow; resolution is single-use; every step lands on the audit
 * trail.
 */
class SplitKnowledgeServiceImplTest {

    private PersistenceService persistenceService;
    private SplitKnowledgeService service;

    @BeforeEach
    void setUp() {
        persistenceService = MockPersistence.create();
        PairwiseBindingService pairwiseBindings = org.mockito.Mockito.mock(PairwiseBindingService.class);
        org.mockito.Mockito.when(pairwiseBindings.resolveProfileId("bank-a", "didvc:pairwise:subject-9"))
                .thenReturn("profile-secret-42");
        SplitKnowledgeServiceImpl impl = new SplitKnowledgeServiceImpl();
        impl.setPersistenceService(persistenceService);
        impl.setPairwiseBindingService(pairwiseBindings);
        service = impl;
    }

    @Test
    void neitherCustodianAloneCanResolve() {
        String request = service.createReidentificationRequest("bank-a", "didvc:pairwise:subject-9", "CO-2026-77");

        service.approve(request, SplitKnowledgeCustodian.KYC_CUSTODIAN);
        SplitKnowledgeService.Resolution afterKyc = service.tryResolve(request);
        assertFalse(afterKyc.isResolved());

        // duplicate approvals never advance the workflow
        service.approve(request, SplitKnowledgeCustodian.KYC_CUSTODIAN);
        assertFalse(service.tryResolve(request).isResolved());

        // the second, distinct custodian unlocks resolution
        service.approve(request, SplitKnowledgeCustodian.OPERATOR_CUSTODIAN);
        SplitKnowledgeService.Resolution resolution = service.tryResolve(request);
        assertTrue(resolution.isResolved());
        assertEquals("profile-secret-42", resolution.getSubjectId());
    }

    @Test
    void operatorFirstOrderAlsoRequiresBoth() {
        String request = service.createReidentificationRequest("bank-a", "didvc:pairwise:subject-9", "CO-2026-78");
        service.approve(request, SplitKnowledgeCustodian.OPERATOR_CUSTODIAN);
        assertFalse(service.tryResolve(request).isResolved());
        service.approve(request, SplitKnowledgeCustodian.KYC_CUSTODIAN);
        assertTrue(service.tryResolve(request).isResolved());
    }

    @Test
    void everyStepIsAudited() {
        String request = service.createReidentificationRequest("bank-a", "didvc:pairwise:subject-9", "CO-2026-79");
        service.approve(request, SplitKnowledgeCustodian.KYC_CUSTODIAN);
        service.tryResolve(request); // denied — audited
        service.approve(request, SplitKnowledgeCustodian.OPERATOR_CUSTODIAN);
        SplitKnowledgeService.Resolution resolution = service.tryResolve(request);

        // request + 2 approvals + denied attempt + resolution = 5 steps
        assertTrue(resolution.getAuditTrail().size() >= 5);
        assertTrue(resolution.getAuditTrail().get(0).contains("requested:"));
        assertTrue(resolution.getAuditTrail().stream().anyMatch(s -> s.contains("custodian=kyc-custodian")));
        assertTrue(resolution.getAuditTrail().stream().anyMatch(s -> s.contains("custodian=operator-custodian")));
        assertTrue(resolution.getAuditTrail().stream().anyMatch(s -> s.contains("resolution-denied")));
        assertTrue(resolution.getAuditTrail().stream().anyMatch(s -> s.contains("resolved: subject=profile-secret-42")));

        // The trail persists with the request for compliance review
        ReidentificationRequest persisted = persistenceService.load(request, ReidentificationRequest.class);
        assertNotNull(persisted);
        assertEquals(resolution.getAuditTrail().size(), persisted.getAuditTrail().size());
    }

    @Test
    void resolutionIsSingleUse() {
        String request = service.createReidentificationRequest("bank-a", "didvc:pairwise:subject-9", "CO-2026-80");
        service.approve(request, SplitKnowledgeCustodian.KYC_CUSTODIAN);
        service.approve(request, SplitKnowledgeCustodian.OPERATOR_CUSTODIAN);
        assertTrue(service.tryResolve(request).isResolved());
        assertFalse(service.tryResolve(request).isResolved());
        // further approvals after resolution are refused
        assertFalse(service.approve(request, SplitKnowledgeCustodian.KYC_CUSTODIAN));
    }

    @Test
    void unknownReferenceAndMissingJustificationRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createReidentificationRequest("bank-a", "didvc:pairwise:subject-9", null));
        assertThrows(IllegalArgumentException.class,
                () -> service.createReidentificationRequest("bank-a", null, "CO-2026-81"));

        String request = service.createReidentificationRequest("bank-a", "didvc:pairwise:unknown", "CO-2026-82");
        service.approve(request, SplitKnowledgeCustodian.KYC_CUSTODIAN);
        service.approve(request, SplitKnowledgeCustodian.OPERATOR_CUSTODIAN);
        assertThrows(IllegalStateException.class, () -> service.tryResolve(request));
    }
}
