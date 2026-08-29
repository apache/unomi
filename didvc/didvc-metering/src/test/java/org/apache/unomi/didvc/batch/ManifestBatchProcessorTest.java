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

package org.apache.unomi.didvc.batch;

import org.apache.unomi.didvc.audit.AuditLogService;
import org.apache.unomi.didvc.audit.InMemoryAuditLogStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bulk manifest verification (FR-L3): a batch of N manifests is
 * processed with one audit record per manifest (linked by manifest id)
 * and one sink publication per result; a manifest is valid only when
 * all of its credentials verify.
 */
class ManifestBatchProcessorTest {

    private static final String TENANT = "customs-hk";
    private static final int BATCH_SIZE = 25;

    private AuditLogService auditLogService;
    private List<ManifestRecord.Result> sinkResults;

    @BeforeEach
    void setUp() {
        auditLogService = new AuditLogService(new InMemoryAuditLogStore());
        sinkResults = new ArrayList<>();
    }

    private ManifestRecord.Processor processor(List<String> failingCredentials) {
        return new ManifestRecord.Processor(
                (tenant, credential) -> !failingCredentials.contains(credential),
                (tenant, credential) -> "credential is revoked",
                auditLogService,
                sinkResults::add);
    }

    @Test
    void batchOfNManifestsProcessesWithPerRecordAudit() {
        List<ManifestRecord> manifests = new ArrayList<>();
        for (int i = 0; i < BATCH_SIZE; i++) {
            manifests.add(new ManifestRecord("SWD-BULK:LI-" + i,
                    List.of("credential-good-" + i, "credential-extra-" + i)));
        }
        List<ManifestRecord.Result> results = processor(List.of()).process(TENANT, manifests);

        assertEquals(BATCH_SIZE, results.size());
        assertTrue(results.stream().allMatch(ManifestRecord.Result::isValid));

        // One audit record per manifest, linked by manifest id
        assertEquals(BATCH_SIZE, auditLogService.readAll().size());
        assertTrue(auditLogService.verifyChain());
        for (int i = 0; i < BATCH_SIZE; i++) {
            String payload = auditLogService.readAll().get(i).getPayload();
            assertTrue(payload.contains("SWD-BULK:LI-" + i), "audit record must link manifest " + i);
            assertTrue(payload.contains("\"valid\":true"));
        }
        // One sink publication per manifest
        assertEquals(BATCH_SIZE, sinkResults.size());
    }

    @Test
    void manifestWithOneBadCredentialIsInvalidWithReason() {
        ManifestRecord good = new ManifestRecord("M-GOOD", List.of("c1", "c2"));
        ManifestRecord bad = new ManifestRecord("M-BAD", List.of("c1", "bad-credential", "c3"));
        List<ManifestRecord.Result> results = processor(List.of("bad-credential"))
                .process(TENANT, List.of(good, bad));

        assertTrue(results.get(0).isValid());
        assertFalse(results.get(1).isValid());
        assertEquals(List.of("credential is revoked"), results.get(1).getReasons());
        assertTrue(auditLogService.readAll().get(1).getPayload().contains("credential is revoked"));
    }

    @Test
    void emptyManifestIsTriviallyValid() {
        ManifestRecord empty = new ManifestRecord("M-EMPTY", List.of());
        List<ManifestRecord.Result> results = processor(List.of()).process(TENANT, List.of(empty));
        assertTrue(results.get(0).isValid());
        assertEquals(1, auditLogService.readAll().size());
    }
}
