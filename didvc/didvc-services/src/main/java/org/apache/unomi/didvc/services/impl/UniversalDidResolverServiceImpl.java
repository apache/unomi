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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.unomi.didvc.api.DidDocumentData;
import org.apache.unomi.didvc.api.items.DidDocumentRecord;
import org.apache.unomi.didvc.api.services.DidMethodResolver;
import org.apache.unomi.didvc.api.services.DidService;
import org.apache.unomi.didvc.api.services.UniversalDidResolverService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Universal DID resolution: dispatches by DID method to the registered
 * {@link DidMethodResolver} adapters, with {@code did:web} served by the
 * platform {@link DidService}. DIDs whose method has no registered adapter
 * resolve against the persisted DID-document registry — the local cache
 * for externally-resolved documents (iAM Smart, RealDID, …) and the
 * stub-document source for tests and offline operation.
 */
@Component(service = UniversalDidResolverService.class, immediate = true)
public class UniversalDidResolverServiceImpl implements UniversalDidResolverService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UniversalDidResolverServiceImpl.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<DidMethodResolver> resolvers = new CopyOnWriteArrayList<>();

    @Reference
    private DidService didService;
    @Reference
    private PersistenceService persistenceService;

    public void setDidService(DidService didService) {
        this.didService = didService;
    }

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    /**
     * OSGi DS bind method for {@link DidMethodResolver} adapters (dynamic,
     * multiple). Also usable from tests to register adapters directly.
     */
    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    public void addResolver(DidMethodResolver resolver) {
        resolvers.add(resolver);
    }

    public void removeResolver(DidMethodResolver resolver) {
        resolvers.remove(resolver);
    }

    @Override
    public DidDocumentData resolve(String did) {
        if (did == null || did.isEmpty()) {
            return null;
        }
        String method = methodOf(did);
        if ("web".equals(method)) {
            return didService.resolveDid(did);
        }
        for (DidMethodResolver resolver : resolvers) {
            if (method.equals(resolver.getMethod())) {
                DidDocumentData document = resolver.resolve(did);
                if (document != null) {
                    LOGGER.debug("Resolved {} via the {} method adapter", did, method);
                    return document;
                }
                return null;
            }
        }
        // No live driver for this method: serve the persisted registry
        // (cache of previously resolved documents or configured stubs).
        DidDocumentData document = resolveFromRegistry(did);
        if (document == null) {
            LOGGER.debug("No resolver and no registry entry for {}", did);
        }
        return document;
    }

    private DidDocumentData resolveFromRegistry(String did) {
        DidDocumentRecord record = persistenceService.load(did, DidDocumentRecord.class);
        if (record == null || record.isDeactivated()) {
            return null;
        }
        try {
            return objectMapper.readValue(record.getJson(), DidDocumentData.class);
        } catch (JsonProcessingException e) {
            LOGGER.warn("Registry DID document for {} is unreadable", did, e);
            return null;
        }
    }

    static String methodOf(String did) {
        String[] parts = did.split(":");
        return parts.length >= 2 ? parts[1] : "";
    }
}
