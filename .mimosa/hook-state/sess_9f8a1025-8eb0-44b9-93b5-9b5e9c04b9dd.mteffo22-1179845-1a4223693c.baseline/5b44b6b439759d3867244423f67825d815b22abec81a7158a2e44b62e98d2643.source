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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory audit-log store for tests and local development. Records are
 * exposed so tamper-evidence tests can mutate a copy.
 */
public class InMemoryAuditLogStore implements AuditLogStore {

    private final Map<Long, AuditRecord> records = new ConcurrentHashMap<>();

    @Override
    public long nextSeq() {
        return records.size() + 1L;
    }

    @Override
    public AuditRecord readLast() {
        return records.isEmpty() ? null : records.get((long) records.size());
    }

    @Override
    public void persist(AuditRecord record) {
        AuditRecord copy = new AuditRecord();
        copy.setSeq(record.getSeq());
        copy.setPrevHash(record.getPrevHash());
        copy.setEventType(record.getEventType());
        copy.setActor(record.getActor());
        copy.setSubjectRef(record.getSubjectRef());
        copy.setPayload(record.getPayload());
        copy.setCreatedAt(record.getCreatedAt());
        copy.setHash(record.getHash());
        records.put(record.getSeq(), copy);
    }

    @Override
    public List<AuditRecord> readAll() {
        List<AuditRecord> result = new ArrayList<>();
        for (long seq = 1; seq <= records.size(); seq++) {
            result.add(records.get(seq));
        }
        return result;
    }

    /**
     * Direct access to the stored record copies (for tamper-evidence tests).
     *
     * @param seq the sequence number
     * @return the stored record
     */
    public AuditRecord get(long seq) {
        return records.get(seq);
    }
}
