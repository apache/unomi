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
import org.apache.unomi.didvc.api.items.KeyDescriptor;
import org.apache.unomi.didvc.api.items.StatusListRecord;
import org.apache.unomi.didvc.api.services.IssuerKeyService;
import org.apache.unomi.didvc.api.services.StatusService;
import org.apache.unomi.didvc.services.util.BitstringCodec;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * W3C Bitstring Status List (v1.0) management with a StatusList2021 adapter.
 * Revocation is a bit flip in the persisted list; because verification always
 * checks the list, revocation takes effect at the next verification.
 */
@Component(service = StatusService.class, immediate = true)
public class StatusServiceImpl implements StatusService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StatusServiceImpl.class);
    private static final String VC_CONTEXT = "https://www.w3.org/ns/credentials/v2";
    private static final String STATUS_LIST_CONTEXT = "https://www.w3.org/ns/credentials/status/v1";

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
    public StatusListRecord createStatusList(String tenantId, String issuerDid, String statusPurpose, int size) {
        if (!"revocation".equals(statusPurpose) && !"suspension".equals(statusPurpose)) {
            throw new IllegalArgumentException("Unsupported status purpose: " + statusPurpose);
        }
        if (size < 1) {
            throw new IllegalArgumentException("Status list size must be at least 1");
        }
        String recordId = "didvc-status-" + UUID.randomUUID();
        StatusListRecord record = new StatusListRecord(recordId);
        record.setStatusPurpose(statusPurpose);
        record.setSize(size);
        record.setNextIndex(0);
        record.setIssuerDid(issuerDid);
        record.setEncodedList(BitstringCodec.encode(new byte[(size + 7) / 8]));
        record.setStatusListId("urn:didvc:status:" + statusPurpose + ":" + recordId);
        record.setScope("didvc");
        record.setTenantId(tenantId);
        persistenceService.save(record);
        LOGGER.info("Created {} status list {} ({} entries) for {}", statusPurpose, recordId, size, issuerDid);
        return record;
    }

    @Override
    public int allocateIndex(String statusListId) {
        StatusListRecord record = requireRecord(statusListId);
        int index = record.getNextIndex();
        byte[] bits = BitstringCodec.decode(record.getEncodedList());
        if (index >= bits.length * 8) {
            int newSize = Math.max(record.getSize() * 2, index + 1);
            bits = Arrays.copyOf(bits, (newSize + 7) / 8);
            record.setSize(newSize);
        }
        record.setEncodedList(BitstringCodec.encode(bits));
        record.setNextIndex(index + 1);
        persistenceService.save(record);
        return index;
    }

    @Override
    public void revoke(String statusListId, int index) {
        StatusListRecord record = requireRecord(statusListId);
        byte[] bits = BitstringCodec.decode(record.getEncodedList());
        if (index >= bits.length * 8) {
            throw new IndexOutOfBoundsException("Status index " + index + " out of range for list " + statusListId);
        }
        BitstringCodec.setBit(bits, index, true);
        record.setEncodedList(BitstringCodec.encode(bits));
        persistenceService.save(record);
    }

    @Override
    public boolean isRevoked(String statusListId, int index) {
        StatusListRecord record = requireRecord(statusListId);
        byte[] bits = BitstringCodec.decode(record.getEncodedList());
        return BitstringCodec.getBit(bits, index);
    }

    @Override
    public String publish(String statusListId, String kid) {
        StatusListRecord record = requireRecord(statusListId);
        KeyDescriptor key = issuerKeyService.getKey(kid);
        if (key == null) {
            throw new IllegalArgumentException("Unknown signing key: " + kid);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("@context", Arrays.asList(VC_CONTEXT, STATUS_LIST_CONTEXT));
        payload.put("id", record.getStatusListId());
        payload.put("type", "BitstringStatusList");
        payload.put("statusPurpose", record.getStatusPurpose());
        payload.put("encodedList", record.getEncodedList());
        String jwt = issuerKeyService.sign(kid, writeJson(payload));
        record.setKid(kid);
        record.setSignedJwt(jwt);
        persistenceService.save(record);
        return jwt;
    }

    @Override
    public String buildStatusList2021Jwt(String statusListId, String kid) {
        StatusListRecord record = requireRecord(statusListId);
        KeyDescriptor key = issuerKeyService.getKey(kid);
        if (key == null) {
            throw new IllegalArgumentException("Unknown signing key: " + kid);
        }
        Map<String, Object> credentialSubject = new LinkedHashMap<>();
        credentialSubject.put("id", record.getStatusListId());
        credentialSubject.put("type", "StatusList2021");
        credentialSubject.put("statusPurpose", record.getStatusPurpose());
        credentialSubject.put("encodedList", record.getEncodedList());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("@context", Arrays.asList(VC_CONTEXT, STATUS_LIST_CONTEXT));
        payload.put("id", record.getStatusListId());
        payload.put("type", Arrays.asList("VerifiableCredential", "StatusList2021Credential"));
        payload.put("iss", record.getIssuerDid());
        payload.put("issuanceDate", Instant.now().toString());
        payload.put("credentialSubject", credentialSubject);
        return issuerKeyService.sign(kid, writeJson(payload));
    }

    @Override
    public StatusListRecord getStatusList(String statusListId) {
        return persistenceService.load(statusListId, StatusListRecord.class);
    }

    private StatusListRecord requireRecord(String statusListId) {
        StatusListRecord record = persistenceService.load(statusListId, StatusListRecord.class);
        if (record == null) {
            throw new IllegalArgumentException("Unknown status list: " + statusListId);
        }
        return record;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize status list payload", e);
        }
    }
}
