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

package org.apache.unomi.didvc.services.impl;

import org.apache.unomi.didvc.api.items.TrustEntry;
import org.apache.unomi.didvc.api.services.TrustRegistryService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Trust registry: relying-tenant acceptance of issuer/credential-type pairs,
 * enforced on every verification. Untrusted issuers, unknown credential
 * types and expired or revoked entries are rejected.
 */
@Component(service = TrustRegistryService.class, immediate = true)
public class TrustRegistryServiceImpl implements TrustRegistryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TrustRegistryServiceImpl.class);

    @Reference
    private PersistenceService persistenceService;

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @Override
    public void saveTrustEntry(TrustEntry entry) {
        if (entry.getItemType() == null) {
            entry.setItemType(TrustEntry.ITEM_TYPE);
        }
        if (entry.getScope() == null) {
            entry.setScope("didvc");
        }
        persistenceService.save(entry);
        LOGGER.info("Saved trust entry {} (verifier={}, issuer={}, vct={}, level={})",
                entry.getItemId(), entry.getTenantId(), entry.getIssuerDid(),
                entry.getVct(), entry.getAccreditationLevel());
    }

    @Override
    public TrustEntry getTrustEntry(String entryId) {
        return persistenceService.load(entryId, TrustEntry.class);
    }

    @Override
    public void deleteTrustEntry(String entryId) {
        persistenceService.remove(entryId, TrustEntry.class);
    }

    @Override
    public List<TrustEntry> getTrustEntries(String verifierTenantId) {
        List<TrustEntry> result = new ArrayList<>();
        for (TrustEntry entry : persistenceService.getAllItems(TrustEntry.class)) {
            if (verifierTenantId == null || verifierTenantId.equals(entry.getTenantId())) {
                result.add(entry);
            }
        }
        return result;
    }

    @Override
    public boolean isTrusted(String verifierTenantId, String issuerDid, String vct, Date now) {
        for (TrustEntry entry : persistenceService.getAllItems(TrustEntry.class)) {
            if (!"active".equals(entry.getStatus())) {
                continue;
            }
            if (verifierTenantId != null && !verifierTenantId.equals(entry.getTenantId())) {
                continue;
            }
            if (issuerDid != null && !issuerDid.equals(entry.getIssuerDid())) {
                continue;
            }
            if (vct != null && !vct.equals(entry.getVct())) {
                continue;
            }
            if (entry.getValidFrom() != null && entry.getValidFrom().after(now)) {
                continue;
            }
            if (entry.getValidUntil() != null && !entry.getValidUntil().after(now)) {
                continue;
            }
            return true;
        }
        return false;
    }
}
