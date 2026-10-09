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
package org.apache.unomi.plugins.baseplugin.actions;

import org.apache.unomi.api.security.CompatPeerPrincipal;
import org.apache.unomi.api.security.InsecureTransportPrincipal;
import org.apache.unomi.api.security.SecurityService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.security.auth.Subject;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class IdentityTrustTest {

    @Mock private SecurityService securityService;

    @Test
    public void missingSecurityService_isNotTrusted() {
        assertFalse(IdentityTrust.isTrustedIdentityCaller(null, CompatPeerPrincipal.ABILITY_MERGE_ON_LOGIN, false));
    }

    @Test
    public void systemAccess_isTrustedWithoutAnyPeerAbility() {
        when(securityService.hasSystemAccess()).thenReturn(true);

        assertTrue(IdentityTrust.isTrustedIdentityCaller(securityService, CompatPeerPrincipal.ABILITY_MERGE_ON_LOGIN, false));
    }

    @Test
    public void peerWithTheRequiredAbility_isTrusted() {
        when(securityService.hasCompatPeerAbility(CompatPeerPrincipal.ABILITY_MERGE_ON_LOGIN)).thenReturn(true);

        assertTrue(IdentityTrust.isTrustedIdentityCaller(securityService, CompatPeerPrincipal.ABILITY_MERGE_ON_LOGIN, false));
    }

    @Test
    public void peerWithAnotherAbility_isNotTrusted() {
        when(securityService.hasCompatPeerAbility(CompatPeerPrincipal.ABILITY_UPDATE_OTHER_PROFILES)).thenReturn(true);

        assertFalse(IdentityTrust.isTrustedIdentityCaller(securityService, CompatPeerPrincipal.ABILITY_MERGE_ON_LOGIN, false));
        assertTrue(IdentityTrust.isTrustedIdentityCaller(securityService,
                CompatPeerPrincipal.ABILITY_UPDATE_OTHER_PROFILES, false));
    }

    @Test
    public void systemAccessOverPlainHttp_isRefusedOnlyWhenSecureTransportIsRequired() {
        when(securityService.hasSystemAccess()).thenReturn(true);
        Subject overHttp = new Subject();
        overHttp.getPrincipals().add(InsecureTransportPrincipal.INSTANCE);
        when(securityService.getRequestSubject()).thenReturn(overHttp);

        assertFalse(IdentityTrust.isTrustedIdentityCaller(securityService, CompatPeerPrincipal.ABILITY_MERGE_ON_LOGIN, true));
        assertTrue(IdentityTrust.isTrustedIdentityCaller(securityService, CompatPeerPrincipal.ABILITY_MERGE_ON_LOGIN, false));
    }

    @Test
    public void systemAccessOverHttps_isTrustedWhenSecureTransportIsRequired() {
        when(securityService.hasSystemAccess()).thenReturn(true);
        when(securityService.getRequestSubject()).thenReturn(new Subject());

        assertTrue(IdentityTrust.isTrustedIdentityCaller(securityService, CompatPeerPrincipal.ABILITY_MERGE_ON_LOGIN, true));
    }

    /** Scheduled and other internal work has no request, so no transport to judge. */
    @Test
    public void systemAccessWithoutARequestSubject_isTrustedWhenSecureTransportIsRequired() {
        when(securityService.hasSystemAccess()).thenReturn(true);
        when(securityService.getRequestSubject()).thenReturn(null);

        assertTrue(IdentityTrust.isTrustedIdentityCaller(securityService, CompatPeerPrincipal.ABILITY_MERGE_ON_LOGIN, true));
    }

    /** The compatibility-mode peer keeps its Unomi 3.0 behaviour whatever the transport. */
    @Test
    public void compatPeerOverPlainHttp_isTrustedWhenSecureTransportIsRequired() {
        when(securityService.hasCompatPeerAbility(CompatPeerPrincipal.ABILITY_MERGE_ON_LOGIN)).thenReturn(true);

        assertTrue(IdentityTrust.isTrustedIdentityCaller(securityService, CompatPeerPrincipal.ABILITY_MERGE_ON_LOGIN, true));
    }

    @Test
    public void visitorWithoutSystemAccessOrPeerAbility_isNotTrusted() {
        assertFalse(IdentityTrust.isTrustedIdentityCaller(securityService, CompatPeerPrincipal.ABILITY_MERGE_ON_LOGIN, false));
    }
}
