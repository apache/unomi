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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.unomi.didvc.api.DidDocumentData;
import org.apache.unomi.didvc.api.items.DidDocumentRecord;
import org.apache.unomi.didvc.api.items.KeyDescriptor;
import org.apache.unomi.didvc.api.services.DidService;
import org.apache.unomi.didvc.api.services.IssuerKeyService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * W3C DID Core operations for {@code did:web}: creation, resolution, key
 * rotation and deactivation, backed by {@link DidDocumentRecord} items.
 */
@Component(service = DidService.class, immediate = true)
public class DidServiceImpl implements DidService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DidServiceImpl.class);
    private static final List<String> DID_CONTEXT = Arrays.asList("https://www.w3.org/ns/did/v1");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Reference
    private PersistenceService persistenceService;
    @Reference
    private IssuerKeyService issuerKeyService;

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setIssuerKeyService(IssuerKeyService issuerKeyService) {
        this.issuerKeyService = issuerKeyService;
    }

    @Override
    public DidDocumentData createDid(String tenantId, String domain, String path, String algorithm) {
        String did = didWebId(domain, path);
        DidDocumentRecord existing = persistenceService.load(did, DidDocumentRecord.class);
        if (existing != null && !existing.isDeactivated()) {
            throw new IllegalStateException("DID already exists: " + did);
        }
        KeyDescriptor key = issuerKeyService.generateKey(tenantId, did, algorithm);
        DidDocumentData doc = new DidDocumentData();
        doc.setContext(DID_CONTEXT);
        doc.setId(did);
        DidDocumentData.VerificationMethod method = new DidDocumentData.VerificationMethod();
        method.setId(did + "#" + key.getKid());
        method.setType("JsonWebKey2020");
        method.setController(did);
        method.setPublicKeyJwk(readJwk(key.getPublicJwk()));
        doc.addVerificationMethod(method);
        DidDocumentData.Service service = new DidDocumentData.Service();
        service.setId(did + "#didvc");
        service.setType("CredentialRegistryService");
        service.setServiceEndpoint("https://" + domain + "/didvc");
        doc.setService(Arrays.asList(service));

        DidDocumentRecord record = new DidDocumentRecord(did);
        record.setDomain(domain);
        record.setPath(path);
        record.setJson(writeJson(doc));
        record.setDeactivated(false);
        record.setScope("didvc");
        record.setTenantId(tenantId);
        persistenceService.save(record);
        LOGGER.info("Created DID {}", did);
        return doc;
    }

    @Override
    public DidDocumentData resolveDid(String did) {
        DidDocumentRecord record = persistenceService.load(did, DidDocumentRecord.class);
        if (record == null || record.isDeactivated()) {
            return null;
        }
        return readDocument(record);
    }

    @Override
    public DidDocumentData rotateKey(String did, String algorithm) {
        DidDocumentRecord record = requireActiveRecord(did);
        if (record == null) {
            return null;
        }
        KeyDescriptor key = issuerKeyService.generateKey(record.getTenantId(), did, algorithm);
        DidDocumentData doc = readDocument(record);
        DidDocumentData.VerificationMethod method = new DidDocumentData.VerificationMethod();
        method.setId(did + "#" + key.getKid());
        method.setType("JsonWebKey2020");
        method.setController(did);
        method.setPublicKeyJwk(readJwk(key.getPublicJwk()));
        doc.addVerificationMethod(method);
        record.setJson(writeJson(doc));
        persistenceService.save(record);
        LOGGER.info("Rotated verification key for {} ({}); {} verification methods now listed",
                did, algorithm, doc.getVerificationMethod().size());
        return doc;
    }

    @Override
    public DidDocumentData deactivateDid(String did) {
        DidDocumentRecord record = requireActiveRecord(did);
        if (record == null) {
            return null;
        }
        record.setDeactivated(true);
        DidDocumentData doc = readDocument(record);
        doc.setService(Collections.emptyList());
        record.setJson(writeJson(doc));
        persistenceService.save(record);
        LOGGER.info("Deactivated DID {}", did);
        return doc;
    }

    @Override
    public List<DidDocumentData> listDids(String tenantId) {
        List<DidDocumentData> result = new ArrayList<>();
        for (DidDocumentRecord record : persistenceService.getAllItems(DidDocumentRecord.class)) {
            if ((tenantId == null || tenantId.equals(record.getTenantId())) && !record.isDeactivated()) {
                result.add(readDocument(record));
            }
        }
        return result;
    }

    private DidDocumentRecord requireActiveRecord(String did) {
        DidDocumentRecord record = persistenceService.load(did, DidDocumentRecord.class);
        if (record == null || record.isDeactivated()) {
            return null;
        }
        return record;
    }

    private DidDocumentData readDocument(DidDocumentRecord record) {
        try {
            return objectMapper.readValue(record.getJson(), DidDocumentData.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored DID document for " + record.getDid() + " is unreadable", e);
        }
    }

    private Map<String, Object> readJwk(String publicJwkJson) {
        try {
            return objectMapper.readValue(publicJwkJson, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored public JWK is unreadable", e);
        }
    }

    private String writeJson(DidDocumentData doc) {
        try {
            return objectMapper.writeValueAsString(doc);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize DID document " + doc.getId(), e);
        }
    }

    static String didWebId(String domain, String path) {
        StringBuilder sb = new StringBuilder("did:web:").append(domain);
        if (path != null && !path.isEmpty()) {
            sb.append(':').append(path.replace('/', ':'));
        }
        return sb.toString();
    }
}
