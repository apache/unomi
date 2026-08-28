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

package org.apache.unomi.didvc.metering;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory metering sink for tests and local development.
 */
public class InMemoryMeteringSink implements MeteringSink {

    private final List<VerificationMeteringRecord> records = new ArrayList<>();
    private final Set<String> seenEventIds = ConcurrentHashMap.newKeySet();

    @Override
    public synchronized void publish(VerificationMeteringRecord record) {
        records.add(record);
        seenEventIds.add(record.getEventId());
    }

    /**
     * All records published so far.
     *
     * @return the records
     */
    public synchronized List<VerificationMeteringRecord> getRecords() {
        return new ArrayList<>(records);
    }

    /**
     * Clears all records (test support).
     */
    public synchronized void clear() {
        records.clear();
        seenEventIds.clear();
    }
}
