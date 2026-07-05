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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretHashServiceImplTest {

    private final SecretHashServiceImpl service = new SecretHashServiceImpl();

    @Test
    void hashProducesThreePartFormat() {
        String stored = service.hash("secret-value");
        String[] parts = stored.split(":");
        assertEquals(3, parts.length);
        assertEquals(String.valueOf(SecretHashServiceImpl.DEFAULT_ITERATIONS), parts[0]);
    }

    @Test
    void hashUsesUniqueSaltPerCall() {
        String hash1 = service.hash("same-secret");
        String hash2 = service.hash("same-secret");
        assertNotEquals(hash1, hash2);
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
    void verifyRejectsMalformedStoredHash() {
        assertFalse(service.verify("x", "not-a-valid-hash"));
        assertFalse(service.verify("x", "1:only-two-parts"));
    }

    @Test
    void verifyRejectsInvalidBase64InStoredHash() {
        assertFalse(service.verify("x", "600000:!!!:!!!"));
    }

    @Test
    void hashRejectsNullPlaintext() {
        assertThrows(IllegalArgumentException.class, () -> service.hash(null));
    }

    @Test
    void generateRandomSecretReturnsHexOfRequestedLength() {
        String secret = service.generateRandomSecret(16);
        assertNotNull(secret);
        assertEquals(32, secret.length());
        assertTrue(secret.matches("[0-9A-F]+"));
    }

    @Test
    void generateRandomSecretRejectsNonPositiveLength() {
        assertThrows(IllegalArgumentException.class, () -> service.generateRandomSecret(0));
    }

    @Test
    void maskShowsPrefixAndLastFourCharacters() {
        assertEquals("unomi_****EFGH", service.mask("unomi_ABCDEFGH", "unomi_"));
    }

    @Test
    void maskWithoutPrefixShowsLastFourCharacters() {
        assertEquals("****cdef", service.mask("abcdef", null));
    }

    @Test
    void maskReturnsNullForNullPlaintext() {
        assertNull(service.mask(null, "unomi_"));
    }

    @Test
    void maskHandlesShortBody() {
        assertEquals("unomi_****X", service.mask("unomi_X", "unomi_"));
    }

    @Test
    void maskIgnoresPrefixWhenPlaintextDoesNotStartWithIt() {
        assertEquals("****2345", service.mask("12345", "unomi_"));
    }

    @Test
    void apiKeyGeneratePlainTextKeyUsesConfiguredPrefixAndLength() {
        String key = ApiKey.generatePlainTextKey(service);
        assertTrue(key.startsWith(ApiKey.KEY_PREFIX));
        assertEquals(ApiKey.KEY_PREFIX.length() + ApiKey.KEY_RANDOM_BYTES * 2, key.length());
    }

    @Test
    void apiKeyMaskPlainTextKeyUsesServiceMask() {
        String key = ApiKey.generatePlainTextKey(service);
        String masked = ApiKey.maskPlainTextKey(service, key);
        assertTrue(masked.startsWith(ApiKey.KEY_PREFIX + "****"));
        assertTrue(masked.endsWith(key.substring(key.length() - 4)));
    }

    @Test
    void apiKeyHashAndVerifyRoundTrip() {
        String key = ApiKey.generatePlainTextKey(service);
        String stored = service.hashHighEntropySecret(key);
        assertTrue(service.verifyHighEntropySecret(key, stored));
        assertFalse(service.verifyHighEntropySecret(key + "x", stored));
    }

    @Test
    void hashEmbedsIterationCountForFutureUpgrades() {
        String stored = service.hash("upgrade-test");
        assertTrue(stored.startsWith(SecretHashServiceImpl.DEFAULT_ITERATIONS + ":"));
    }

    @Test
    void hashHighEntropySecretIsDeterministic() {
        assertEquals(service.hashHighEntropySecret("same-secret"), service.hashHighEntropySecret("same-secret"));
    }

    @Test
    void hashHighEntropySecretDiffersForDifferentInputs() {
        assertNotEquals(service.hashHighEntropySecret("secret-a"), service.hashHighEntropySecret("secret-b"));
    }

    @Test
    void hashHighEntropySecretRejectsNullPlaintext() {
        assertThrows(IllegalArgumentException.class, () -> service.hashHighEntropySecret(null));
    }

    @Test
    void highEntropyHashDiffersFromPasswordHash() {
        String plaintext = "some-api-key";
        assertNotEquals(service.hashHighEntropySecret(plaintext), service.hash(plaintext));
    }
}
