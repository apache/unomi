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

package org.apache.unomi.didvc.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Immutable, hash-chained audit log for issuance, presentation,
 * verification and revocation events. Each record's hash covers its own
 * content and the previous record's hash; {@link #verifyChain()} recomputes
 * the chain and detects any mutation, in memory or in the store.
 */
public class AuditLogService {

    /**
     * Hash used for the first record's predecessor link.
     */
    public static final String GENESIS_HASH = "genesis";

    private final AuditLogStore store;

    public AuditLogService(AuditLogStore store) {
        this.store = store;
    }

    /**
     * Appends an audit record.
     *
     * @param eventType  the event type, e.g. {@code didvcIssued} or {@code didvpVerified}
     * @param actor      the acting party (tenant or service identifier)
     * @param subjectRef the verifier-scoped pairwise subject reference
     * @param payload    event payload as JSON
     * @return the appended record
     */
    public synchronized AuditRecord append(String eventType, String actor, String subjectRef, String payload) {
        AuditRecord previous = store.readLast();
        AuditRecord record = new AuditRecord();
        record.setSeq(store.nextSeq());
        record.setPrevHash(previous == null ? GENESIS_HASH : previous.getHash());
        record.setEventType(eventType);
        record.setActor(actor);
        record.setSubjectRef(subjectRef);
        record.setPayload(payload);
        record.setCreatedAt(System.currentTimeMillis());
        record.setHash(computeHash(record));
        store.persist(record);
        return record;
    }

    /**
     * Recomputes the hash chain and reports whether it is intact.
     *
     * @return true when every record's hash and predecessor link verify
     */
    public boolean verifyChain() {
        String expectedPrev = GENESIS_HASH;
        long expectedSeq = 1;
        for (AuditRecord record : store.readAll()) {
            if (record.getSeq() != expectedSeq) {
                return false;
            }
            if (!expectedPrev.equals(record.getPrevHash())) {
                return false;
            }
            if (!computeHash(record).equals(record.getHash())) {
                return false;
            }
            expectedPrev = record.getHash();
            expectedSeq++;
        }
        return true;
    }

    /**
     * All records in sequence order.
     *
     * @return the records
     */
    public List<AuditRecord> readAll() {
        return store.readAll();
    }

    static String computeHash(AuditRecord record) {
        String input = record.getSeq() + "|" + record.getPrevHash() + "|" + record.getEventType() + "|"
                + record.getActor() + "|" + record.getSubjectRef() + "|" + record.getPayload() + "|"
                + record.getCreatedAt();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
