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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hash-chained audit log: append order, chain verification, and tamper
 * detection on both the in-memory and JDBC stores.
 */
class AuditLogServiceTest {

    @Test
    void chainAppendsAndVerifies() {
        AuditLogService service = new AuditLogService(new InMemoryAuditLogStore());
        service.append("didvcIssued", "hkt", "didvc:pairwise:a", "{\"schemaId\":\"hkt-kyc-v1\"}");
        service.append("didvpVerified", "bank-a", "didvc:pairwise:a", "{\"vct\":\"hkt_kyc_v1\"}");
        service.append("didvcRevoked", "hkt", "didvc:pairwise:a", "{\"recordId\":\"r1\"}");

        assertEquals(3, service.readAll().size());
        assertEquals("genesis", service.readAll().get(0).getPrevHash());
        assertEquals(service.readAll().get(0).getHash(), service.readAll().get(1).getPrevHash());
        assertTrue(service.verifyChain());
    }

    @Test
    void tamperedRecordIsDetected() {
        InMemoryAuditLogStore store = new InMemoryAuditLogStore();
        AuditLogService service = new AuditLogService(store);
        service.append("didvcIssued", "hkt", "didvc:pairwise:a", "{\"schemaId\":\"hkt-kyc-v1\"}");
        service.append("didvpVerified", "bank-a", "didvc:pairwise:a", "{\"vct\":\"hkt_kyc_v1\"}");
        assertTrue(service.verifyChain());

        // Mutate the stored copy of the first record
        store.get(1).setPayload("{\"schemaId\":\"tampered\"}");
        assertFalse(service.verifyChain());
    }

    @Test
    void brokenLinkIsDetected() {
        InMemoryAuditLogStore store = new InMemoryAuditLogStore();
        AuditLogService service = new AuditLogService(store);
        service.append("didvcIssued", "hkt", "didvc:pairwise:a", "{\"a\":1}");
        service.append("didvcRevoked", "hkt", "didvc:pairwise:a", "{\"a\":2}");

        store.get(2).setPrevHash("forged");
        assertFalse(service.verifyChain());
    }

    @Test
    void jdbcStoreRoundTripAndTamperDetection() throws Exception {
        org.h2.jdbcx.JdbcDataSource dataSource = new org.h2.jdbcx.JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:audit-test;DB_CLOSE_DELAY=-1");
        JdbcAuditLogStore store = new JdbcAuditLogStore(dataSource);
        store.init();
        AuditLogService service = new AuditLogService(store);

        service.append("didvcIssued", "hkt", "didvc:pairwise:a", "{\"a\":1}");
        service.append("didvpVerified", "bank-a", "didvc:pairwise:a", "{\"a\":2}");
        assertTrue(service.verifyChain());
        assertEquals(2, service.readAll().size());

        // Simulate a direct UPDATE bypassing the append-only discipline
        try (java.sql.Connection connection = dataSource.getConnection();
             java.sql.Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE didvc_audit_log SET payload = '{\"tampered\":true}' WHERE seq = 1");
        }
        assertFalse(service.verifyChain());
    }
}
