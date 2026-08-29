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

import org.apache.unomi.didvc.api.items.CredentialRecord;
import org.apache.unomi.didvc.api.services.CredentialRefreshService;
import org.apache.unomi.didvc.services.MockPersistence;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Re-verification lifecycle: expiry-window sweep and identity-change
 * triggers (annual refresh / SIM re-registration patterns).
 */
class CredentialRefreshServiceImplTest {

    private static final long NOW = 1_700_000_000_000L;
    private static final long ONE_YEAR = 365L * 24 * 3600 * 1000;

    private PersistenceService persistenceService;
    private CredentialRefreshService refreshService;

    @BeforeEach
    void setUp() {
        persistenceService = MockPersistence.create();
        refreshService = new CredentialRefreshServiceImpl();
        ((CredentialRefreshServiceImpl) refreshService).setPersistenceService(persistenceService);
    }

    private CredentialRecord record(String id, String subjectId, long expiresAt) {
        CredentialRecord record = new CredentialRecord(id);
        record.setSubjectId(subjectId);
        record.setExpiresAt(new Date(expiresAt));
        record.setTenantId("hkt");
        persistenceService.save(record);
        return record;
    }

    @Test
    void notDueOutsideWindow() {
        CredentialRecord record = record("cred-1", "profile-1", NOW + ONE_YEAR);
        assertFalse(refreshService.isRefreshDue(record, new Date(NOW)));
    }

    @Test
    void dueInsideWindow() {
        // 89 days before expiry: inside the 90-day refresh window
        CredentialRecord record = record("cred-1", "profile-1", NOW + 89L * 24 * 3600 * 1000);
        assertTrue(refreshService.isRefreshDue(record, new Date(NOW)));
    }

    @Test
    void sweepMarksOnlyDueCredentials() {
        record("cred-due", "profile-1", NOW + 10L * 24 * 3600 * 1000);
        record("cred-fine", "profile-1", NOW + ONE_YEAR);
        assertEquals(1, refreshService.sweepExpiringCredentials(new Date(NOW)));
        assertTrue(((CredentialRecord) persistenceService.load("cred-due", CredentialRecord.class)).isRefreshDue());
        assertFalse(((CredentialRecord) persistenceService.load("cred-fine", CredentialRecord.class)).isRefreshDue());
        // second sweep finds nothing new
        assertEquals(0, refreshService.sweepExpiringCredentials(new Date(NOW)));
    }

    @Test
    void identityChangeMarksSubjectsCredentials() {
        record("cred-1", "profile-1", NOW + ONE_YEAR);
        record("cred-2", "profile-1", NOW + ONE_YEAR);
        record("cred-3", "profile-2", NOW + ONE_YEAR);
        assertEquals(2, refreshService.markRefreshDueForSubject("profile-1"));
        assertTrue(((CredentialRecord) persistenceService.load("cred-1", CredentialRecord.class)).isRefreshDue());
        assertTrue(((CredentialRecord) persistenceService.load("cred-2", CredentialRecord.class)).isRefreshDue());
        assertFalse(((CredentialRecord) persistenceService.load("cred-3", CredentialRecord.class)).isRefreshDue());
    }

    @Test
    void revokedCredentialIsDue() {
        CredentialRecord record = record("cred-1", "profile-1", NOW + ONE_YEAR);
        record.setRevoked(true);
        persistenceService.save(record);
        assertTrue(refreshService.isRefreshDue(record, new Date(NOW)));
    }
}
