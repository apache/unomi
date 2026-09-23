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

import org.apache.unomi.api.security.SecurityService;
import org.apache.unomi.api.security.UnomiRoles;

/**
 * Single definition of the trust policy for identity claims made through actions: merging
 * profiles, recording a merge identifier, updating another profile, or writing
 * {@code systemProperties}.
 * <p>
 * This is a role check, not a check of the credential that produced it: a tenant private key
 * authenticates as {@link UnomiRoles#TENANT_ADMINISTRATOR} and therefore passes, while a tenant
 * public API key or an unauthenticated context event does not. A missing
 * {@link SecurityService} fails closed.
 */
final class IdentityTrust {

    private IdentityTrust() {
    }

    /**
     * @param securityService the security service, possibly null while OSGi wiring is in flux
     * @return whether the caller holds system access (administrator or tenant administrator)
     */
    static boolean isTrustedIdentityCaller(SecurityService securityService) {
        return securityService != null && securityService.hasSystemAccess();
    }
}
