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

import org.apache.unomi.api.Event;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.didvc.api.CredentialFormatter;
import org.apache.unomi.didvc.api.CredentialIssueRequest;
import org.apache.unomi.didvc.api.DidvcEventTypes;
import org.apache.unomi.didvc.api.items.CredentialRecord;
import org.apache.unomi.didvc.api.items.DidSchema;
import org.apache.unomi.didvc.api.items.KeyDescriptor;
import org.apache.unomi.didvc.api.items.StatusListRecord;
import org.apache.unomi.didvc.api.services.ConsentBridgeService;
import org.apache.unomi.didvc.api.services.CredentialSchemaService;
import org.apache.unomi.didvc.api.services.IssuanceService;
import org.apache.unomi.didvc.api.services.IssuerKeyService;
import org.apache.unomi.didvc.api.services.StatusService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Issuance orchestration: schema validation, consent-gated claim
 * minimization, status-index allocation, formatting and persistence. Emits
 * {@code didvcIssued} / {@code didvcRevoked} events so downstream segments,
 * audit and metering are ordinary Unomi event consumers.
 */
@Component(service = IssuanceService.class, immediate = true)
public class IssuanceServiceImpl implements IssuanceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(IssuanceServiceImpl.class);
    private static final String DEFAULT_STATUS_PURPOSE = "revocation";
    private static final int DEFAULT_STATUS_LIST_SIZE = 1024;

    @Reference
    private PersistenceService persistenceService;
    @Reference
    private CredentialSchemaService schemaService;
    @Reference
    private ConsentBridgeService consentBridgeService;
    @Reference
    private StatusService statusService;
    @Reference
    private IssuerKeyService issuerKeyService;
    @Reference(target = "(didvc.format=vc+sd-jwt)", cardinality = ReferenceCardinality.OPTIONAL)
    private CredentialFormatter defaultFormatter;
    @Reference(cardinality = ReferenceCardinality.OPTIONAL)
    private EventService eventService;
    /**
     * All registered credential formatters (SD-JWT, JSON-LD, …); a request's
     * {@code format} selects among them, null selects the default.
     */
    private final List<CredentialFormatter> formatters = new java.util.concurrent.CopyOnWriteArrayList<>();

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setSchemaService(CredentialSchemaService schemaService) {
        this.schemaService = schemaService;
    }

    public void setConsentBridgeService(ConsentBridgeService consentBridgeService) {
        this.consentBridgeService = consentBridgeService;
    }

    public void setStatusService(StatusService statusService) {
        this.statusService = statusService;
    }

    public void setIssuerKeyService(IssuerKeyService issuerKeyService) {
        this.issuerKeyService = issuerKeyService;
    }

    public void setDefaultFormatter(CredentialFormatter defaultFormatter) {
        this.defaultFormatter = defaultFormatter;
    }

    /**
     * OSGi DS bind method for the multi-cardinality formatter reference;
     * also usable from tests to register formatters directly.
     */
    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    public void addFormatter(CredentialFormatter formatter) {
        formatters.add(formatter);
    }

    public void removeFormatter(CredentialFormatter formatter) {
        formatters.remove(formatter);
    }

    public void setEventService(EventService eventService) {
        this.eventService = eventService;
    }

    @Override
    public CredentialRecord issueCredential(CredentialIssueRequest request) {
        CredentialFormatter formatter = selectFormatter(request);
        DidSchema schema = schemaService.getSchema(request.getSchemaId());
        if (schema == null) {
            throw new IllegalArgumentException("Unknown schema: " + request.getSchemaId());
        }
        // Claim minimization: every claim must be whitelisted on the schema
        schemaService.validateClaims(schema, request.allClaims());
        // Consent: disclosure must not exceed the granted claim set
        consentBridgeService.verifyDisclosure(request.getSubjectId(), request.getSchemaId(),
                request.getVerifierCategory(), request.allClaims().keySet());

        KeyDescriptor key = issuerKeyService.getKey(request.getKid());
        if (key == null) {
            throw new IllegalArgumentException("Unknown signing key: " + request.getKid());
        }

        int statusIndex = request.getStatusListIndex();
        String statusListId = null;
        String statusListUri = request.getStatusListUri();
        if (statusIndex < 0) {
            StatusListRecord list = findOrCreateDefaultList(request.getTenantId(), key.getIssuerDid());
            statusListId = list.getItemId();
            statusIndex = statusService.allocateIndex(statusListId);
            statusListUri = list.getStatusListId();
        }

        CredentialIssueRequest effective = copyWithStatus(request, statusIndex, statusListUri);
        String credential = formatter.format(effective);

        CredentialRecord record = new CredentialRecord("didvc-cred-" + UUID.randomUUID());
        record.setSchemaId(request.getSchemaId());
        record.setSubjectId(request.getSubjectId());
        record.setSubjectType(request.getSubjectType());
        record.setKid(request.getKid());
        record.setVerifierCategory(request.getVerifierCategory());
        record.setStatusListId(statusListId);
        record.setStatusListIndex(statusIndex);
        record.setFormat(formatter.getFormat());
        record.setCredential(credential);
        record.setIssuedAt(new Date());
        record.setExpiresAt(new Date(System.currentTimeMillis()
                + request.getValidityDays() * 24L * 3600 * 1000));
        record.setRevoked(false);
        record.setAlwaysDisclosedClaims(new HashMap<>(request.getAlwaysDisclosedClaims()));
        record.setSelectivelyDisclosedClaims(new HashMap<>(request.getSelectivelyDisclosedClaims()));
        record.setHolderPublicJwkJson(request.getHolderPublicJwkJson());
        record.setScope("didvc");
        record.setTenantId(request.getTenantId());
        persistenceService.save(record);
        emitEvent(DidvcEventTypes.DIDVC_ISSUED, request.getTenantId(), record, null);
        LOGGER.info("Issued credential {} (schema={}, vct={}, subject={}, statusIndex={})",
                record.getItemId(), schema.getItemId(), schema.getVct(),
                request.getSubjectId(), statusIndex);
        return record;
    }

    @Override
    public CredentialRecord getCredential(String recordId) {
        return persistenceService.load(recordId, CredentialRecord.class);
    }

    @Override
    public CredentialRecord revokeCredential(String recordId) {
        CredentialRecord record = persistenceService.load(recordId, CredentialRecord.class);
        if (record == null) {
            return null;
        }
        if (record.getStatusListId() != null && record.getStatusListIndex() != null) {
            statusService.revoke(record.getStatusListId(), record.getStatusListIndex());
        }
        record.setRevoked(true);
        persistenceService.save(record);
        emitEvent(DidvcEventTypes.DIDVC_REVOKED, record.getTenantId(), record, null);
        LOGGER.info("Revoked credential {}", recordId);
        return record;
    }

    @Override
    public CredentialRecord rebindCredential(String recordId, String holderPublicJwkJson) {
        CredentialRecord record = persistenceService.load(recordId, CredentialRecord.class);
        if (record == null) {
            return null;
        }
        if (holderPublicJwkJson == null || holderPublicJwkJson.isEmpty()) {
            throw new IllegalArgumentException("holderPublicJwkJson is required for key binding");
        }
        CredentialIssueRequest request = new CredentialIssueRequest();
        request.setTenantId(record.getTenantId());
        request.setSchemaId(record.getSchemaId());
        request.setSubjectId(record.getSubjectId());
        request.setSubjectType(record.getSubjectType());
        request.setKid(record.getKid());
        request.setVerifierCategory(record.getVerifierCategory());
        request.setFormat(record.getFormat());
        request.setHolderPublicJwkJson(holderPublicJwkJson);
        long remainingMillis = record.getExpiresAt() == null ? 0 : record.getExpiresAt().getTime() - System.currentTimeMillis();
        request.setValidityDays((int) Math.max(1, remainingMillis / (24L * 3600 * 1000)));
        request.setStatusListIndex(record.getStatusListIndex());
        request.setStatusListUri(statusListUriOf(record));
        if (record.getAlwaysDisclosedClaims() != null) {
            request.getAlwaysDisclosedClaims().putAll(record.getAlwaysDisclosedClaims());
        }
        if (record.getSelectivelyDisclosedClaims() != null) {
            request.getSelectivelyDisclosedClaims().putAll(record.getSelectivelyDisclosedClaims());
        }
        String credential = selectFormatter(request).format(request);
        record.setCredential(credential);
        record.setHolderPublicJwkJson(holderPublicJwkJson);
        persistenceService.save(record);
        LOGGER.info("Rebound credential {} to holder key", recordId);
        return record;
    }

    @Override
    public boolean isCredentialRevoked(String recordId) {
        CredentialRecord record = persistenceService.load(recordId, CredentialRecord.class);
        if (record == null) {
            return false;
        }
        if (record.isRevoked()) {
            return true;
        }
        if (record.getStatusListId() != null && record.getStatusListIndex() != null) {
            return statusService.isRevoked(record.getStatusListId(), record.getStatusListIndex());
        }
        return false;
    }

    private String statusListUriOf(CredentialRecord record) {
        if (record.getStatusListId() == null) {
            return null;
        }
        StatusListRecord list = persistenceService.load(record.getStatusListId(), StatusListRecord.class);
        return list == null ? null : list.getStatusListId();
    }

    private StatusListRecord findOrCreateDefaultList(String tenantId, String issuerDid) {        for (StatusListRecord record : persistenceService.getAllItems(StatusListRecord.class)) {
            if (tenantId != null && tenantId.equals(record.getTenantId())
                    && DEFAULT_STATUS_PURPOSE.equals(record.getStatusPurpose())
                    && issuerDid.equals(record.getIssuerDid())) {
                return record;
            }
        }
        return statusService.createStatusList(tenantId, issuerDid, DEFAULT_STATUS_PURPOSE, DEFAULT_STATUS_LIST_SIZE);
    }

    /**
     * Selects the credential formatter for a request: an explicit
     * {@code format} picks the matching registered formatter, null picks
     * the default (SD-JWT) formatter.
     */
    private CredentialFormatter selectFormatter(CredentialIssueRequest request) {
        String format = request.getFormat();
        if (format == null || format.isEmpty()) {
            if (defaultFormatter == null) {
                throw new IllegalStateException("No default credential formatter (didvc.format=vc+sd-jwt) is bound");
            }
            return defaultFormatter;
        }
        for (CredentialFormatter formatter : formatters) {
            if (format.equals(formatter.getFormat())) {
                return formatter;
            }
        }
        throw new IllegalArgumentException("No credential formatter is bound for format " + format);
    }

    private CredentialIssueRequest copyWithStatus(CredentialIssueRequest request, int statusIndex, String statusListUri) {
        CredentialIssueRequest effective = new CredentialIssueRequest();
        effective.setTenantId(request.getTenantId());
        effective.setSchemaId(request.getSchemaId());
        effective.setSubjectId(request.getSubjectId());
        effective.setSubjectType(request.getSubjectType());
        effective.setKid(request.getKid());
        effective.setVerifierCategory(request.getVerifierCategory());
        effective.setHolderPublicJwkJson(request.getHolderPublicJwkJson());
        effective.setValidityDays(request.getValidityDays());
        effective.setStatusListIndex(statusIndex);
        effective.setStatusListUri(statusListUri);
        effective.getAlwaysDisclosedClaims().putAll(request.getAlwaysDisclosedClaims());
        effective.getSelectivelyDisclosedClaims().putAll(request.getSelectivelyDisclosedClaims());
        return effective;
    }

    private void emitEvent(String eventType, String tenantId, CredentialRecord record, Throwable error) {
        if (eventService == null) {
            return;
        }
        try {
            Map<String, Object> properties = new HashMap<>();
            properties.put("recordId", record.getItemId());
            properties.put("schemaId", record.getSchemaId());
            properties.put("subjectId", record.getSubjectId());
            if (error != null) {
                properties.put("error", error.getMessage());
            }
            Event event = new Event(eventType, null, null, "didvc", null, null, properties, new Date(), true);
            event.setTenantId(tenantId);
            eventService.send(event);
        } catch (Exception e) {
            LOGGER.warn("Failed to emit {} event for credential {}", eventType, record.getItemId(), e);
        }
    }
}
