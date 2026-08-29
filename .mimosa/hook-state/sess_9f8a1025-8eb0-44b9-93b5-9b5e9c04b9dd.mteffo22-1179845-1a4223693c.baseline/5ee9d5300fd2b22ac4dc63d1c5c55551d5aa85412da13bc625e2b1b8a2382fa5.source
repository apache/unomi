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

import org.apache.unomi.didvc.api.items.ConsentGrantRecord;
import org.apache.unomi.didvc.api.services.ConsentBridgeService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Consent bridge over the consent-grant store: per credential type, per
 * verifier category, per subject. Disclosure beyond the granted claim set
 * is refused — the enforcement half of FR-CS1.
 */
@Component(service = ConsentBridgeService.class, immediate = true)
public class ConsentBridgeServiceImpl implements ConsentBridgeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsentBridgeServiceImpl.class);

    @Reference
    private PersistenceService persistenceService;

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @Override
    public void saveGrant(ConsentGrantRecord grant) {
        if (grant.getItemType() == null) {
            grant.setItemType(ConsentGrantRecord.ITEM_TYPE);
        }
        if (grant.getScope() == null) {
            grant.setScope("didvc");
        }
        persistenceService.save(grant);
    }

    @Override
    public Set<String> getGrantedClaims(String subjectId, String schemaId, String verifierCategory) {
        Set<String> granted = new HashSet<>();
        for (ConsentGrantRecord grant : persistenceService.getAllItems(ConsentGrantRecord.class)) {
            if (subjectId.equals(grant.getSubjectId())
                    && schemaId.equals(grant.getSchemaId())
                    && matches(verifierCategory, grant.getVerifierCategory())) {
                granted.addAll(grant.getClaims());
            }
        }
        return granted;
    }

    @Override
    public void verifyDisclosure(String subjectId, String schemaId, String verifierCategory, Set<String> claims) {
        Set<String> granted = getGrantedClaims(subjectId, schemaId, verifierCategory);
        for (String claim : claims) {
            if (!granted.contains(claim)) {
                throw new IllegalArgumentException("Claim '" + claim + "' is not covered by the subject's consent grant"
                        + " for schema " + schemaId + " and verifier category " + verifierCategory);
            }
        }
        LOGGER.debug("Consent check passed for subject {} schema {} ({} claims)", subjectId, schemaId, claims.size());
    }

    private boolean matches(String verifierCategory, String grantCategory) {
        if (grantCategory == null) {
            return true;
        }
        return grantCategory.equals(verifierCategory);
    }
}
