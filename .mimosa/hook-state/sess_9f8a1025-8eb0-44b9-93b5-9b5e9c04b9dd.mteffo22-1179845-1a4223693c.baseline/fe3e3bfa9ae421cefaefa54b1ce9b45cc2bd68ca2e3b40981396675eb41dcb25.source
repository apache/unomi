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

import org.apache.unomi.didvc.api.items.ConsentGrantRecord;
import org.apache.unomi.didvc.api.services.ConsentBridgeService;
import org.apache.unomi.didvc.services.MockPersistence;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Consent-grant enforcement: disclosure is bounded by the granted claim set
 * per subject, schema and verifier category.
 */
class ConsentBridgeServiceImplTest {

    private PersistenceService persistenceService;
    private ConsentBridgeService consentBridgeService;

    @BeforeEach
    void setUp() {
        persistenceService = MockPersistence.create();
        consentBridgeService = new ConsentBridgeServiceImpl();
        ((ConsentBridgeServiceImpl) consentBridgeService).setPersistenceService(persistenceService);

        ConsentGrantRecord grant = new ConsentGrantRecord("grant-1");
        grant.setSubjectId("profile-1");
        grant.setSchemaId("hkt-kyc-v1");
        grant.setVerifierCategory("financial-institution");
        grant.setClaims(new HashSet<>(Arrays.asList("kycLevel", "sanctionsClear")));
        consentBridgeService.saveGrant(grant);
    }

    @Test
    void grantedClaimsReturned() {
        Set<String> granted = consentBridgeService.getGrantedClaims("profile-1", "hkt-kyc-v1", "financial-institution");
        assertEquals(new HashSet<>(Arrays.asList("kycLevel", "sanctionsClear")), granted);
    }

    @Test
    void noGrantMeansNoClaims() {
        assertTrue(consentBridgeService.getGrantedClaims("profile-9", "hkt-kyc-v1", "financial-institution").isEmpty());
        assertTrue(consentBridgeService.getGrantedClaims("profile-1", "hkt-kyc-v1", "customs").isEmpty());
    }

    @Test
    void grantedDisclosurePasses() {
        consentBridgeService.verifyDisclosure("profile-1", "hkt-kyc-v1", "financial-institution",
                new HashSet<>(Arrays.asList("kycLevel")));
    }

    @Test
    void ungrantedDisclosureRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> consentBridgeService.verifyDisclosure("profile-1", "hkt-kyc-v1", "financial-institution",
                        new HashSet<>(Arrays.asList("givenName"))));
        assertTrue(e.getMessage().contains("givenName"));
    }

    @Test
    void disclosureToOtherCategoryRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> consentBridgeService.verifyDisclosure("profile-1", "hkt-kyc-v1", "customs",
                        new HashSet<>(Arrays.asList("kycLevel"))));
    }
}
