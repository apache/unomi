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
package org.apache.unomi.services.common.security;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ApiKeyHashServiceImplTest {

    private ApiKeyHashServiceImpl hashService;

    @Before
    public void setUp() {
        hashService = new ApiKeyHashServiceImpl();
    }

    @Test
    public void generateKeyUsesUnomiPrefix() {
        String key = hashService.generateKey();
        assertNotNull(key);
        assertTrue("Key should use unomi_v1_ prefix", key.startsWith("unomi_v1_"));
    }

    @Test
    public void hashAndVerifyRoundTrip() {
        String plainTextKey = hashService.generateKey();
        String storedHash = hashService.hash(plainTextKey);

        assertTrue("Stored hash should use iterations:salt:hash format", storedHash.contains(":"));
        assertTrue(hashService.verify(plainTextKey, storedHash));
        assertFalse(hashService.verify("wrong-key", storedHash));
    }

    @Test
    public void verifyRejectsNullInputs() {
        String storedHash = hashService.hash(hashService.generateKey());
        assertFalse(hashService.verify(null, storedHash));
        assertFalse(hashService.verify("some-key", null));
    }

    @Test
    public void verifyRejectsMalformedHash() {
        assertFalse(hashService.verify("unomi_v1_ABCD", "not-a-valid-hash"));
        assertFalse(hashService.verify("unomi_v1_ABCD", "1:bad-base64:also-bad"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void hashRejectsNullPlaintext() {
        hashService.hash(null);
    }

    @Test
    public void maskShowsPrefixAndLastFourChars() {
        String plainTextKey = "unomi_v1_C606D77D1D219509637A82C062BCD17F13D6DF1501702DC396D4A12D63D4E5F2";
        String masked = hashService.mask(plainTextKey);
        assertTrue(masked.startsWith("unomi_v1_****"));
        assertTrue(masked.endsWith("E5F2"));
        assertNotEquals(plainTextKey, masked);
    }

    @Test
    public void verifyUsesConstantTimeComparison() {
        String plainTextKey = hashService.generateKey();
        String storedHash = hashService.hash(plainTextKey);

        long validStart = System.nanoTime();
        hashService.verify(plainTextKey, storedHash);
        long validElapsed = System.nanoTime() - validStart;

        long invalidStart = System.nanoTime();
        hashService.verify(hashService.generateKey(), storedHash);
        long invalidElapsed = System.nanoTime() - invalidStart;

        assertTrue("Verify timing should not differ by more than 50ms between valid and invalid keys",
                Math.abs(validElapsed - invalidElapsed) < 50_000_000L);
    }
}
