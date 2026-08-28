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

import org.apache.unomi.didvc.api.items.PairwiseBindingRecord;
import org.apache.unomi.didvc.api.services.PairwiseBindingService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.UUID;

/**
 * Per-verifier pseudonymous subject references. Each relying tenant gets a
 * different opaque reference for the same profile, so verifiers cannot
 * correlate subjects across institutions; profile resolution stays inside
 * the platform and is never exposed to the verification edge.
 */
@Component(service = PairwiseBindingService.class, immediate = true)
public class PairwiseBindingServiceImpl implements PairwiseBindingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PairwiseBindingServiceImpl.class);

    @Reference
    private PersistenceService persistenceService;

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @Override
    public String getOrCreateOpaqueReference(String profileId, String verifierTenantId) {
        for (PairwiseBindingRecord binding : persistenceService.getAllItems(PairwiseBindingRecord.class)) {
            if (profileId.equals(binding.getProfileId())
                    && verifierTenantId.equals(binding.getVerifierTenantId())) {
                return binding.getOpaqueReference();
            }
        }
        String opaqueReference = "didvc:pairwise:" + UUID.randomUUID().toString().replace("-", "");
        PairwiseBindingRecord binding = new PairwiseBindingRecord("didvc-binding-" + UUID.randomUUID());
        binding.setProfileId(profileId);
        binding.setVerifierTenantId(verifierTenantId);
        binding.setOpaqueReference(opaqueReference);
        binding.setCreatedAt(new Date());
        binding.setScope("didvc");
        persistenceService.save(binding);
        LOGGER.info("Created pairwise reference for profile {} towards verifier {}", profileId, verifierTenantId);
        return opaqueReference;
    }

    @Override
    public String resolveProfileId(String verifierTenantId, String opaqueReference) {
        for (PairwiseBindingRecord binding : persistenceService.getAllItems(PairwiseBindingRecord.class)) {
            if (verifierTenantId.equals(binding.getVerifierTenantId())
                    && opaqueReference.equals(binding.getOpaqueReference())) {
                return binding.getProfileId();
            }
        }
        return null;
    }
}
