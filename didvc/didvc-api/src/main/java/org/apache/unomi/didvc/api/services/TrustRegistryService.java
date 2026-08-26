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

package org.apache.unomi.didvc.api.services;

import org.apache.unomi.didvc.api.items.TrustEntry;

import java.util.Date;
import java.util.List;

/**
 * Trust registry: which relying tenants accept which credential types from
 * which issuers, at what accreditation level. Enforced on every verification.
 */
public interface TrustRegistryService {

    /**
     * Saves a trust entry.
     *
     * @param entry the entry
     */
    void saveTrustEntry(TrustEntry entry);

    /**
     * Loads a trust entry.
     *
     * @param entryId the entry identifier
     * @return the entry, or null if unknown
     */
    TrustEntry getTrustEntry(String entryId);

    /**
     * Deletes a trust entry.
     *
     * @param entryId the entry identifier
     */
    void deleteTrustEntry(String entryId);

    /**
     * Lists a relying tenant's trust entries.
     *
     * @param verifierTenantId the relying tenant
     * @return the tenant's entries
     */
    List<TrustEntry> getTrustEntries(String verifierTenantId);

    /**
     * Enforcement check: is this issuer/credential-type pair accepted by the
     * relying tenant at the given time?
     *
     * @param verifierTenantId the relying tenant
     * @param issuerDid        the credential issuer DID
     * @param vct              the credential type
     * @param now              the verification time
     * @return true when an active, unexpired, accredited entry exists
     */
    boolean isTrusted(String verifierTenantId, String issuerDid, String vct, Date now);
}
