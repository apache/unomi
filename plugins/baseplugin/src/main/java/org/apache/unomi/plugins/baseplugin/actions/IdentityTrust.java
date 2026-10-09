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
import org.apache.unomi.api.security.UnomiRoles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.Subject;

/**
 * Single definition of the trust policy for identity claims made through actions: merging
 * profiles, recording a merge identifier, updating another profile, or writing
 * {@code systemProperties}.
 * <p>
 * A tenant private key authenticates as {@link UnomiRoles#TENANT_ADMINISTRATOR} and therefore
 * passes. A validated single-tenant-compatibility peer with the matching ability also passes
 * (see {@link CompatPeerPrincipal}). A tenant public API key or an unauthenticated context event
 * does not. A missing {@link SecurityService} fails closed.
 * <p>
 * When a secure transport is required, a caller trusted for its system access is refused if its
 * request was not encrypted (see {@link InsecureTransportPrincipal}). Work that does not come from
 * an HTTP request carries no such marker and is unaffected, and so is a compat peer.
 */
final class IdentityTrust {

    private static final Logger LOGGER = LoggerFactory.getLogger(IdentityTrust.class);

    private IdentityTrust() {
    }

    /**
     * @param securityService        the security service, possibly null while OSGi wiring is in flux
     * @param ability                a {@link CompatPeerPrincipal} ability required of a compat peer
     * @param requireSecureTransport whether system access only counts on an encrypted request
     * @return whether the caller holds the given compat-peer ability, or system access over an
     *         acceptable transport
     */
    static boolean isTrustedIdentityCaller(SecurityService securityService, String ability,
            boolean requireSecureTransport) {
        if (securityService == null) {
            return false;
        }
        if (securityService.hasCompatPeerAbility(ability)) {
            return true;
        }
        if (!securityService.hasSystemAccess()) {
            return false;
        }
        if (requireSecureTransport && isInsecureTransport(securityService)) {
            LOGGER.warn("Refusing identity claim from a trusted caller: the request was not sent over HTTPS "
                    + "and requireSecureTransport is enabled");
            return false;
        }
        return true;
    }

    private static boolean isInsecureTransport(SecurityService securityService) {
        Subject subject = securityService.getRequestSubject();
        return subject != null && !subject.getPrincipals(InsecureTransportPrincipal.class).isEmpty();
    }
}
