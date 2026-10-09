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
package org.apache.unomi.rest.authentication;

import org.apache.unomi.api.security.CompatPeerPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class V2ThirdPartyConfigServiceTest {

    private V2ThirdPartyConfigService service;

    @Mock
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        service = new V2ThirdPartyConfigService();
    }

    @Test
    void exampleKeyIsDisabledUnlessAllowed() {
        Map<String, Object> props = baseProvider(V2ThirdPartyConfigService.EXAMPLE_PROVIDER_KEY);
        props.put("allowExampleKey", "false");
        service.modified(props);
        assertTrue(service.getProviders().isEmpty());

        props.put("allowExampleKey", "true");
        service.modified(props);
        assertTrue(service.isValidProvider("provider1"));
        assertTrue(service.isAllowExampleKey());
    }

    @Test
    void shortAndEmptyKeysAreDisabled() {
        Map<String, Object> props = new HashMap<>();
        props.put("thirdparty.short.key", "tooshort");
        props.put("thirdparty.empty.key", "");
        props.put("thirdparty.ok.key", "long-enough-secret-01");
        props.put("thirdparty.ok.ipAddresses", "127.0.0.1");
        props.put("thirdparty.ok.allowedEvents", "login");
        service.modified(props);
        assertFalse(service.isValidProvider("short"));
        assertFalse(service.isValidProvider("empty"));
        assertTrue(service.isValidProvider("ok"));
    }

    @Test
    void abilitiesDefaultToFullSetAndCanBeLimited() {
        Map<String, Object> props = baseProvider("long-enough-secret-01");
        service.modified(props);
        assertEquals(CompatPeerPrincipal.DEFAULT_ABILITIES, service.getProviders().get("provider1").getAbilities());

        props.put("thirdparty.provider1.abilities", "setEventId,chooseProfileId");
        service.modified(props);
        assertEquals(2, service.getProviders().get("provider1").getAbilities().size());
        assertTrue(service.getProviders().get("provider1").getAbilities()
                .contains(CompatPeerPrincipal.ABILITY_SET_EVENT_ID));
    }

    @Test
    void keyLengthBoundary() {
        Map<String, Object> props = new HashMap<>();
        props.put("thirdparty.fifteen.key", "123456789012345");
        props.put("thirdparty.sixteen.key", "1234567890123456");
        service.modified(props);
        assertFalse(service.isValidProvider("fifteen"));
        assertTrue(service.isValidProvider("sixteen"));
    }

    @Test
    void misspeltAbilitiesDisableTheProviderInsteadOfGrantingEverything() {
        Map<String, Object> props = baseProvider("long-enough-secret-01");
        props.put("thirdparty.provider1.abilities", "setEventID,merge");
        service.modified(props);
        assertFalse(service.isValidProvider("provider1"));

        when(request.getHeader("X-Unomi-Peer")).thenReturn("long-enough-secret-01");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        assertTrue(service.authenticatePeer(request).isEmpty());
    }

    @Test
    void unknownAbilityNextToAKnownOneIsDropped() {
        Map<String, Object> props = baseProvider("long-enough-secret-01");
        props.put("thirdparty.provider1.abilities", "setEventId,chooseProfileID");
        service.modified(props);
        assertEquals(Set.of(CompatPeerPrincipal.ABILITY_SET_EVENT_ID),
                service.getProviders().get("provider1").getAbilities());
    }

    @Test
    void emptyAbilitiesGrantNoAbilityButKeepProtectedEvents() {
        Map<String, Object> props = baseProvider("long-enough-secret-01");
        props.put("thirdparty.provider1.abilities", "");
        service.modified(props);
        assertTrue(service.getProviders().get("provider1").getAbilities().isEmpty());
        assertTrue(service.validateProviderByKey("long-enough-secret-01", "login", "127.0.0.1"));

        when(request.getHeader("X-Unomi-Peer")).thenReturn("long-enough-secret-01");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        Optional<CompatPeerPrincipal> peer = service.authenticatePeer(request);
        assertTrue(peer.isPresent());
        for (String ability : CompatPeerPrincipal.DEFAULT_ABILITIES) {
            assertFalse(peer.get().hasAbility(ability));
        }
    }

    @Test
    void providerWithoutIpAllowlistIsRefusedFromEveryAddress() {
        Map<String, Object> props = baseProvider("long-enough-secret-01");
        for (String ipAddresses : new String[]{null, "", " , "}) {
            if (ipAddresses == null) {
                props.remove("thirdparty.provider1.ipAddresses");
            } else {
                props.put("thirdparty.provider1.ipAddresses", ipAddresses);
            }
            service.modified(props);

            when(request.getHeader("X-Unomi-Peer")).thenReturn("long-enough-secret-01");
            when(request.getRemoteAddr()).thenReturn("127.0.0.1");
            assertTrue(service.authenticatePeer(request).isEmpty(), "ipAddresses=" + ipAddresses);
            assertFalse(service.validateProviderByKey("long-enough-secret-01", "login", "127.0.0.1"),
                    "ipAddresses=" + ipAddresses);
            // The provider's events stay protected, so they are not downgraded to public events.
            assertTrue(service.isProtectedEventType("login"));
        }
    }

    @Test
    void authenticatePeerRequiresKeyAndIp() {
        Map<String, Object> props = baseProvider("long-enough-secret-01");
        service.modified(props);

        when(request.getHeader("X-Unomi-Peer")).thenReturn("long-enough-secret-01");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        Optional<CompatPeerPrincipal> peer = service.authenticatePeer(request);
        assertTrue(peer.isPresent());
        assertEquals("provider1", peer.get().getProviderId());

        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        assertTrue(service.authenticatePeer(request).isEmpty());

        when(request.getHeader("X-Unomi-Peer")).thenReturn("wrong-key-wrong-key");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        assertTrue(service.authenticatePeer(request).isEmpty());
    }

    @Test
    void resolveClientIpUsesXffOnlyBehindTrustedProxy() {
        Map<String, Object> props = baseProvider("long-enough-secret-01");
        props.put("trustedProxies", "10.0.0.1");
        service.modified(props);

        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.9, 10.0.0.1");
        assertEquals("203.0.113.9", service.resolveClientIp(request));

        when(request.getRemoteAddr()).thenReturn("203.0.113.1");
        assertEquals("203.0.113.1", service.resolveClientIp(request));
    }

    @Test
    void resolveClientIpIgnoresClientSuppliedLeadingXffEntries() {
        Map<String, Object> props = baseProvider("long-enough-secret-01");
        props.put("trustedProxies", "10.0.0.0/24");
        service.modified(props);
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");

        // The client sent "127.0.0.1"; the proxy appended the address it really saw.
        when(request.getHeader("X-Forwarded-For")).thenReturn("127.0.0.1, 198.51.100.7");
        assertEquals("198.51.100.7", service.resolveClientIp(request));

        // A second trusted proxy in the chain is skipped as well.
        when(request.getHeader("X-Forwarded-For")).thenReturn("127.0.0.1, 198.51.100.7, 10.0.0.2");
        assertEquals("198.51.100.7", service.resolveClientIp(request));

        when(request.getHeader("X-Unomi-Peer")).thenReturn("long-enough-secret-01");
        when(request.getHeader("X-Forwarded-For")).thenReturn("127.0.0.1, 198.51.100.7");
        assertTrue(service.authenticatePeer(request).isEmpty());
    }

    @Test
    void resolveClientIpFallsBackToRemoteAddrWhenXffHasNoUsableEntry() {
        Map<String, Object> props = baseProvider("long-enough-secret-01");
        props.put("trustedProxies", "10.0.0.1");
        service.modified(props);
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");

        for (String header : new String[]{",", ",,", " , ", "10.0.0.1", "", null}) {
            when(request.getHeader("X-Forwarded-For")).thenReturn(header);
            assertEquals("10.0.0.1", service.resolveClientIp(request), "X-Forwarded-For: " + header);
        }
    }

    @Test
    void validateProviderByKeyChecksEventType() {
        Map<String, Object> props = baseProvider("long-enough-secret-01");
        service.modified(props);
        assertTrue(service.validateProviderByKey("long-enough-secret-01", "login", "127.0.0.1"));
        assertFalse(service.validateProviderByKey("long-enough-secret-01", "view", "127.0.0.1"));
    }

    @Test
    void validateProviderByKeyRejectsWrongIp() {
        Map<String, Object> props = baseProvider("long-enough-secret-01");
        service.modified(props);
        assertFalse(service.validateProviderByKey("long-enough-secret-01", "login", "10.0.0.1"));
    }

    @Test
    void authenticatePeerCarriesConfiguredAbilitySubset() {
        Map<String, Object> props = baseProvider("long-enough-secret-01");
        props.put("thirdparty.provider1.abilities", "setEventId");
        service.modified(props);

        when(request.getHeader("X-Unomi-Peer")).thenReturn("long-enough-secret-01");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        Optional<CompatPeerPrincipal> peer = service.authenticatePeer(request);
        assertTrue(peer.isPresent());
        assertEquals(1, peer.get().getAbilities().size());
        assertTrue(peer.get().getAbilities().contains(CompatPeerPrincipal.ABILITY_SET_EVENT_ID));
        assertFalse(peer.get().getAbilities().contains(CompatPeerPrincipal.ABILITY_CHOOSE_PROFILE_ID));
    }

    @Test
    void authenticatePeerUsesXffIpAgainstAllowlistBehindTrustedProxy() {
        Map<String, Object> props = baseProvider("long-enough-secret-01");
        props.put("trustedProxies", "10.0.0.1");
        props.put("thirdparty.provider1.ipAddresses", "203.0.113.9");
        service.modified(props);

        when(request.getHeader("X-Unomi-Peer")).thenReturn("long-enough-secret-01");
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.9, 10.0.0.1");
        assertTrue(service.authenticatePeer(request).isPresent());

        when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.1, 10.0.0.1");
        assertTrue(service.authenticatePeer(request).isEmpty());

        // Direct client that is not a trusted proxy: allowlist matches remoteAddr (XFF unused).
        when(request.getRemoteAddr()).thenReturn("203.0.113.9");
        assertTrue(service.authenticatePeer(request).isPresent());

        // Forged XFF from a non-proxy peer must not satisfy the allowlist (header is unused).
        when(request.getRemoteAddr()).thenReturn("198.51.100.1");
        lenient().when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.9");
        assertTrue(service.authenticatePeer(request).isEmpty());
    }

    private static Map<String, Object> baseProvider(String key) {
        Map<String, Object> props = new HashMap<>();
        props.put("thirdparty.provider1.key", key);
        props.put("thirdparty.provider1.ipAddresses", "127.0.0.1,::1");
        props.put("thirdparty.provider1.allowedEvents", "login,updateProperties");
        return props;
    }
}
