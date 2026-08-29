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

package org.apache.unomi.didvc.edge.store;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nonce store single-use semantics: a nonce is consumed exactly once and
 * unknown nonces are rejected.
 */
class NonceStoreTest {

    @Test
    void issuedNonceConsumesOnce() {
        NonceStore store = new InMemoryNonceStore();
        store.issue("bank-a:nonce-1", 600);
        assertTrue(store.consume("bank-a:nonce-1"));
        assertFalse(store.consume("bank-a:nonce-1"), "nonce must be single-use");
    }

    @Test
    void unknownNonceRejected() {
        NonceStore store = new InMemoryNonceStore();
        assertFalse(store.consume("bank-a:never-issued"));
    }

    @Test
    void reissueAllowsConsumeAgain() {
        NonceStore store = new InMemoryNonceStore();
        store.issue("bank-a:nonce-2", 600);
        store.consume("bank-a:nonce-2");
        store.issue("bank-a:nonce-2", 600);
        assertTrue(store.consume("bank-a:nonce-2"));
    }
}
