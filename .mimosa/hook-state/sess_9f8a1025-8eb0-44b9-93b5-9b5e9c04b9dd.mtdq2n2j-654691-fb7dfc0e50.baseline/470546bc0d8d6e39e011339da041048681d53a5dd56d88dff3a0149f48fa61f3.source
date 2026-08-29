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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Metering service: billable records with partner/amount fields, and
 * idempotent billing keyed by event id.
 */
class MeteringServiceTest {

    @Test
    void recordsCarryBillingFields() {
        InMemoryMeteringSink sink = new InMemoryMeteringSink();
        MeteringService service = new MeteringService(sink);

        String eventId = service.recordVerification("bank-a", "did:web:id.example.hkt",
                "hkt_kyc_v1", "didvc:pairwise:abc", 150L, "HKD");

        assertNotNull(eventId);
        List<VerificationMeteringRecord> records = sink.getRecords();
        assertEquals(1, records.size());
        VerificationMeteringRecord record = records.get(0);
        assertEquals("bank-a", record.getVerifierTenantId());
        assertEquals("hkt_kyc_v1", record.getVct());
        assertEquals(150L, record.getAmountMinorUnits());
        assertEquals("HKD", record.getCurrency());
        assertEquals(eventId, record.getEventId());
        assertTrue(record.getOccurredAt() > 0);
    }

    @Test
    void eachVerificationIsBilledOnce() {
        InMemoryMeteringSink sink = new InMemoryMeteringSink();
        MeteringService service = new MeteringService(sink);

        service.recordVerification("bank-a", "did:web:id.example.hkt", "hkt_kyc_v1",
                "didvc:pairwise:abc", 150L, "HKD");
        service.recordVerification("bank-a", "did:web:id.example.hkt", "hkt_kyc_v1",
                "didvc:pairwise:abc", 150L, "HKD");

        // Two distinct verifications are two billable events
        assertEquals(2, sink.getRecords().size());
    }
}
