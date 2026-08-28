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

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SecurityUtilsTest {

    @Test
    public void testNullSecret() {
        assertEquals("****", SecurityUtils.maskSecret(null));
    }

    @Test
    public void testEmptySecret() {
        assertEquals("****", SecurityUtils.maskSecret(""));
    }

    @Test
    public void testSecretShorterThanThreshold() {
        assertEquals("****", SecurityUtils.maskSecret("abc"));
    }

    @Test
    public void testSecretExactlyAtThreshold() {
        // exactly 4 chars — still fully masked (not enough to reveal safely)
        assertEquals("****", SecurityUtils.maskSecret("abcd"));
    }

    @Test
    public void testSecretLongerThanThreshold() {
        assertEquals("abcd****", SecurityUtils.maskSecret("abcdefgh"));
    }

    @Test
    public void testRealWorldApiKey() {
        // Typical 32-char shared secret (e.g. X-Unomi-Peer value)
        assertEquals("670c****", SecurityUtils.maskSecret("670c26d1cc413346c3b2fd9ce65dab41"));
    }

    @Test
    public void testFiveCharSecret() {
        // One char beyond threshold — prefix visible, rest masked
        assertEquals("abcd****", SecurityUtils.maskSecret("abcde"));
    }
}
