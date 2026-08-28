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
package org.apache.unomi.services.security;

import org.apache.unomi.api.tenants.ApiKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretHashServiceImplTest {

    private final SecretHashServiceImpl service = new SecretHashServiceImpl();

    @Test
    void hashProducesLowercaseHexSha256() {
        String stored = service.hash("secret-value");
        assertEquals(64, stored.length());
        assertTrue(stored.matches("[0-9a-f]+"));
    }

    @Test
    void hashIsDeterministic() {
        assertEquals(service.hash("same-secret"), service.hash("same-secret"));
    }

    @Test
    void hashDiffersForDifferentInputs() {
        assertNotEquals(service.hash("secret-a"), service.hash("secret-b"));
    }

    @Test
    void verifyAcceptsCorrectPlaintext() {
        String plaintext = "my-api-key-value";
        String stored = service.hash(plaintext);
        assertTrue(service.verify(plaintext, stored));
    }

    @Test
    void verifyRejectsWrongPlaintext() {
        String stored = service.hash("correct");
        assertFalse(service.verify("wrong", stored));
    }

    @Test
    void verifyRejectsNullPlaintext() {
        assertFalse(service.verify(null, service.hash("x")));
    }

    @Test
    void verifyRejectsNullStoredHash() {
        assertFalse(service.verify("x", null));
    }

    @Test
    void hashRejectsNullPlaintext() {
        assertThrows(IllegalArgumentException.class, () -> service.hash(null));
    }

    @Test
    void apiKeyHashAndVerifyRoundTrip() {
        String key = ApiKey.generatePlainTextKey();
        String stored = service.hash(key);
        assertTrue(service.verify(key, stored));
        assertFalse(service.verify(key + "x", stored));
    }
}
