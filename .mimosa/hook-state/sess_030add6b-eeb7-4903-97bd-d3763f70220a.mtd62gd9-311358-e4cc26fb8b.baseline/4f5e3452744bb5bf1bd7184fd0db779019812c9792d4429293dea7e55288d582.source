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

import java.util.List;

/**
 * Append-only storage for audit records. Stores persist records exactly as
 * produced by the {@link AuditLogService} (which computes sequence numbers
 * and hash-chain links); implementations must refuse to mutate existing
 * records.
 */
public interface AuditLogStore {

    /**
     * The next sequence number to assign.
     *
     * @return the next sequence number
     */
    long nextSeq();

    /**
     * The most recently appended record, or null for an empty log.
     *
     * @return the last record, or null
     */
    AuditRecord readLast();

    /**
     * Appends a record. Implementations must be append-only.
     *
     * @param record the record
     */
    void persist(AuditRecord record);

    /**
     * All records in sequence order.
     *
     * @return the records
     */
    List<AuditRecord> readAll();
}
