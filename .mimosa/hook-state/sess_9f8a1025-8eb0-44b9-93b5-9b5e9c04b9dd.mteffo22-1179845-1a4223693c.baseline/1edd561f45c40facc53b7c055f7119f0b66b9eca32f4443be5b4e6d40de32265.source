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

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verification metering with idempotent billing: each successful
 * verification is billed exactly once, keyed by a globally unique event id
 * generated per verification call.
 */
public class MeteringService {

    private final MeteringSink sink;
    private final Set<String> publishedEventIds = ConcurrentHashMap.newKeySet();

    public MeteringService(MeteringSink sink) {
        this.sink = sink;
    }

    /**
     * Records a billable verification event. Duplicate event ids are
     * ignored, making retries and replays safe.
     *
     * @param verifierTenantId the billed partner
     * @param issuerDid        the credential issuer
     * @param vct              the credential type
     * @param subjectRef       the verifier-scoped pairwise subject reference
     * @param amountMinorUnits the fee amount in minor units
     * @param currency         the fee currency
     * @return the event id of the billed event, or null when the event was a duplicate
     */
    public String recordVerification(String verifierTenantId, String issuerDid, String vct,
                                     String subjectRef, long amountMinorUnits, String currency) {
        String eventId = "didvc-meter-" + UUID.randomUUID();
        if (!publishedEventIds.add(eventId)) {
            return null;
        }
        VerificationMeteringRecord record = new VerificationMeteringRecord();
        record.setEventId(eventId);
        record.setVerifierTenantId(verifierTenantId);
        record.setIssuerDid(issuerDid);
        record.setVct(vct);
        record.setSubjectRef(subjectRef);
        record.setOccurredAt(System.currentTimeMillis());
        record.setAmountMinorUnits(amountMinorUnits);
        record.setCurrency(currency);
        sink.publish(record);
        return eventId;
    }
}
