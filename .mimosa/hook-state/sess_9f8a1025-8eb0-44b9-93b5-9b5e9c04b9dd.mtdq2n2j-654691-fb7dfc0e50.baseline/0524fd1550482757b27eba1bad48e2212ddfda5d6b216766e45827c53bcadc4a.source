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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Test class for IPValidationUtils
 */
public class IPValidationUtilsTest {

    @Test
    public void testNoRestrictions() {
        // No IP restrictions should always return true
        assertTrue(IPValidationUtils.isIpAuthorized("192.168.1.1", null));
        assertTrue(IPValidationUtils.isIpAuthorized("192.168.1.1", Collections.emptySet()));
    }

    @Test
    public void testBlankSourceIP() {
        // Blank source IP should return false
        Set<String> authorizedIPs = new HashSet<>(Arrays.asList("192.168.1.1"));
        assertFalse(IPValidationUtils.isIpAuthorized("", authorizedIPs));
        assertFalse(IPValidationUtils.isIpAuthorized(null, authorizedIPs));
        assertFalse(IPValidationUtils.isIpAuthorized("   ", authorizedIPs));
    }

    @Test
    public void testExactMatch() {
        Set<String> authorizedIPs = new HashSet<>(Arrays.asList("192.168.1.1", "10.0.0.1"));
        
        assertTrue(IPValidationUtils.isIpAuthorized("192.168.1.1", authorizedIPs));
        assertTrue(IPValidationUtils.isIpAuthorized("10.0.0.1", authorizedIPs));
        assertFalse(IPValidationUtils.isIpAuthorized("192.168.1.2", authorizedIPs));
    }

    @Test
    public void testIPv6Addresses() {
        Set<String> authorizedIPs = new HashSet<>(Arrays.asList("::1", "2001:db8::1"));
        
        assertTrue(IPValidationUtils.isIpAuthorized("::1", authorizedIPs));
        assertTrue(IPValidationUtils.isIpAuthorized("[::1]", authorizedIPs)); // With brackets
        assertTrue(IPValidationUtils.isIpAuthorized("2001:db8::1", authorizedIPs));
        assertTrue(IPValidationUtils.isIpAuthorized("[2001:db8::1]", authorizedIPs)); // With brackets
        assertFalse(IPValidationUtils.isIpAuthorized("2001:db8::2", authorizedIPs));
        assertFalse(IPValidationUtils.isIpAuthorized("[2001:db8::2]", authorizedIPs)); // With brackets
    }

    @Test
    public void testCIDRRanges() {
        Set<String> authorizedIPs = new HashSet<>(Arrays.asList("127.0.0.0/8", "192.168.0.0/16"));
        
        // Test localhost range
        assertTrue(IPValidationUtils.isIpAuthorized("127.0.0.1", authorizedIPs));
        assertTrue(IPValidationUtils.isIpAuthorized("127.255.255.255", authorizedIPs));
        assertFalse(IPValidationUtils.isIpAuthorized("128.0.0.1", authorizedIPs));
        
        // Test private network range
        assertTrue(IPValidationUtils.isIpAuthorized("192.168.1.1", authorizedIPs));
        assertTrue(IPValidationUtils.isIpAuthorized("192.168.255.255", authorizedIPs));
        assertFalse(IPValidationUtils.isIpAuthorized("192.169.1.1", authorizedIPs));
    }

    @Test
    public void testInvalidIPs() {
        Set<String> authorizedIPs = new HashSet<>(Arrays.asList("192.168.1.1"));
        
        // Invalid IPs should return false but not throw exceptions
        assertFalse(IPValidationUtils.isIpAuthorized("invalid-ip", authorizedIPs));
        assertFalse(IPValidationUtils.isIpAuthorized("256.256.256.256", authorizedIPs));
        assertFalse(IPValidationUtils.isIpAuthorized("192.168.1", authorizedIPs));
    }

    @Test
    public void testInvalidAuthorizedIPs() {
        Set<String> authorizedIPs = new HashSet<>(Arrays.asList("invalid-ip", "192.168.1.1"));
        
        // Should still work with valid IPs even if some authorized IPs are invalid
        assertTrue(IPValidationUtils.isIpAuthorized("192.168.1.1", authorizedIPs));
        assertFalse(IPValidationUtils.isIpAuthorized("192.168.1.2", authorizedIPs));
    }

    @Test
    public void testAllInvalidAuthorizedIPs() {
        Set<String> authorizedIPs = new HashSet<>(Arrays.asList("invalid-ip-1", "invalid-ip-2", "256.256.256.256"));
        
        // Should return false when all authorized IPs are invalid
        assertFalse(IPValidationUtils.isIpAuthorized("192.168.1.1", authorizedIPs));
        assertFalse(IPValidationUtils.isIpAuthorized("10.0.0.1", authorizedIPs));
    }

    @Test
    public void testMixedValidAndInvalidAuthorizedIPs() {
        Set<String> authorizedIPs = new HashSet<>(Arrays.asList("invalid-ip", "192.168.1.0/24", "another-invalid"));
        
        // Should work with CIDR ranges even when some authorized IPs are invalid
        assertTrue(IPValidationUtils.isIpAuthorized("192.168.1.1", authorizedIPs));
        assertTrue(IPValidationUtils.isIpAuthorized("192.168.1.255", authorizedIPs));
        assertFalse(IPValidationUtils.isIpAuthorized("192.168.2.1", authorizedIPs));
    }

    @Test
    public void testEdgeCases() {
        Set<String> authorizedIPs = new HashSet<>(Arrays.asList("127.0.0.1"));
        
        // Test edge cases for bracket handling
        assertFalse(IPValidationUtils.isIpAuthorized("[", authorizedIPs)); // Only opening bracket
        assertFalse(IPValidationUtils.isIpAuthorized("]", authorizedIPs)); // Only closing bracket
        assertFalse(IPValidationUtils.isIpAuthorized("[]", authorizedIPs)); // Empty brackets
        assertFalse(IPValidationUtils.isIpAuthorized("[invalid]", authorizedIPs)); // Invalid IP in brackets
    }

    @Test
    public void testWhitespaceHandling() {
        Set<String> authorizedIPs = new HashSet<>(Arrays.asList("  192.168.1.1  ", "  10.0.0.0/8  "));
        
        // Should handle whitespace in authorized IPs (trim() is called)
        assertTrue(IPValidationUtils.isIpAuthorized("192.168.1.1", authorizedIPs));
        assertTrue(IPValidationUtils.isIpAuthorized("10.0.0.1", authorizedIPs));
        assertFalse(IPValidationUtils.isIpAuthorized("192.168.1.2", authorizedIPs));
    }
} 