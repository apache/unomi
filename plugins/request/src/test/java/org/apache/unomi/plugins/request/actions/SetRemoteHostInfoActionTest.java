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
package org.apache.unomi.plugins.request.actions;

import org.junit.Before;
import org.junit.Test;

import java.net.InetAddress;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the trusted-proxy gating of client-supplied address hints in {@link SetRemoteHostInfoAction}.
 */
public class SetRemoteHostInfoActionTest {

    private SetRemoteHostInfoAction action;

    @Before
    public void init() {
        this.action = new SetRemoteHostInfoAction();
    }

    @Test
    public void defaultTrustedProxies_includeLoopbackAndPrivateRanges() {
        assertTrue(action.isTrustedProxy("127.0.0.1"));
        assertTrue(action.isTrustedProxy("10.20.30.40"));
        assertTrue(action.isTrustedProxy("172.16.5.5"));
        assertTrue(action.isTrustedProxy("192.168.1.254"));
        assertTrue(action.isTrustedProxy("::1"));
    }

    @Test
    public void defaultTrustedProxies_excludePublicAddresses() {
        assertFalse(action.isTrustedProxy("203.0.113.50"));
        assertFalse(action.isTrustedProxy("8.8.8.8"));
        assertFalse(action.isTrustedProxy("2001:4860:4860::8888"));
        // 172.32.x.x is just outside 172.16.0.0/12
        assertFalse(action.isTrustedProxy("172.32.0.1"));
    }

    @Test
    public void isTrustedProxy_rejectsNonIpValues() {
        assertFalse(action.isTrustedProxy(null));
        assertFalse(action.isTrustedProxy(""));
        assertFalse(action.isTrustedProxy("evil.example.com"));
        assertFalse(action.isTrustedProxy("not-an-ip"));
    }

    @Test
    public void setTrustedProxies_overridesDefaults() {
        action.setTrustedProxies("198.51.100.7,203.0.113.0/24");
        assertTrue(action.isTrustedProxy("198.51.100.7"));
        assertTrue(action.isTrustedProxy("203.0.113.99"));
        assertFalse(action.isTrustedProxy("127.0.0.1"));
        assertFalse(action.isTrustedProxy("10.0.0.1"));
    }

    @Test
    public void matchesAddressOrCidr_handlesExactAndPrefixMatches() throws Exception {
        assertTrue(SetRemoteHostInfoAction.matchesAddressOrCidr(InetAddress.getByName("192.0.2.1"), "192.0.2.1"));
        assertFalse(SetRemoteHostInfoAction.matchesAddressOrCidr(InetAddress.getByName("192.0.2.2"), "192.0.2.1"));
        assertTrue(SetRemoteHostInfoAction.matchesAddressOrCidr(InetAddress.getByName("192.0.2.130"), "192.0.2.128/25"));
        assertFalse(SetRemoteHostInfoAction.matchesAddressOrCidr(InetAddress.getByName("192.0.2.1"), "192.0.2.128/25"));
        assertTrue(SetRemoteHostInfoAction.matchesAddressOrCidr(InetAddress.getByName("fc00::1234"), "fc00::/7"));
        // an IPv4 address never matches an IPv6 range
        assertFalse(SetRemoteHostInfoAction.matchesAddressOrCidr(InetAddress.getByName("10.0.0.1"), "fc00::/7"));
        // malformed entries never match
        assertFalse(SetRemoteHostInfoAction.matchesAddressOrCidr(InetAddress.getByName("10.0.0.1"), "10.0.0.0/abc"));
    }
}
