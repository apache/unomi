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

package org.apache.unomi.didvc.edge.customs;

import org.apache.unomi.didvc.audit.AuditLogService;
import org.apache.unomi.didvc.batch.KafkaManifestResultSink;
import org.apache.unomi.didvc.batch.ManifestRecord;
import org.apache.unomi.didvc.edge.EdgeProperties;
import org.apache.unomi.didvc.edge.m2m.BearerCredentialVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Single Window endpoint for the logistics flow (FR-L1/L3/L4): accepts
 * a customs declaration message, verifies every line item's credentials
 * through the batch manifest pipeline (per-record audit, Kafka result
 * publishing when configured), and answers with a Single Window
 * verification response. API-key authenticated like the M2M endpoint;
 * production deployments terminate mTLS at the ingress.
 */
@RestController
public class CustomsEdiController {

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomsEdiController.class);

    private final EdgeProperties properties;
    private final CustomsEdiAdapter adapter;
    private final BearerCredentialVerifier verifier;
    private final AuditLogService auditLogService;
    private final KafkaManifestResultSink kafkaSink;

    public CustomsEdiController(EdgeProperties properties, CustomsEdiAdapter adapter,
                                BearerCredentialVerifier verifier, AuditLogService auditLogService) {
        this.properties = properties;
        this.adapter = adapter;
        this.verifier = verifier;
        this.auditLogService = auditLogService;
        if (properties.getManifestKafkaBootstrapServers() != null
                && !properties.getManifestKafkaBootstrapServers().isEmpty()) {
            this.kafkaSink = new KafkaManifestResultSink(properties.getManifestKafkaBootstrapServers(),
                    properties.getManifestKafkaTopic());
        } else {
            this.kafkaSink = null;
        }
    }

    /**
     * Verifies a Single Window declaration message end to end.
     */
    @PostMapping("/{tenantId}/customs/declarations")
    public Map<String, Object> verifyDeclaration(@PathVariable("tenantId") String tenantId,
                                                 @RequestHeader(value = "X-Api-Key", required = false) String apiKey,
                                                 @RequestBody Map<String, Object> ediMessage) {
        if (properties.getM2mApiKeys() == null || properties.getM2mApiKeys().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "customs verification is not configured for this edge");
        }
        if (apiKey == null || apiKey.isEmpty() || !properties.getM2mApiKeys().contains(apiKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid M2M API key");
        }
        List<CustomsEdiAdapter.EdiManifest> manifests;
        try {
            manifests = adapter.toManifests(ediMessage);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        ManifestRecord.Processor processor = new ManifestRecord.Processor(
                (tenant, credential) -> verifier.verify(tenant, credential, false).isValid(),
                (tenant, credential) -> {
                    BearerCredentialVerifier.Outcome outcome = verifier.verify(tenant, credential, false);
                    return outcome.getReason() == null ? "verification failed" : outcome.getReason();
                },
                auditLogService,
                kafkaSink);
        List<ManifestRecord> records = manifests.stream()
                .map(m -> new ManifestRecord(m.getManifestId(), m.getCredentials()))
                .toList();
        List<ManifestRecord.Result> results = processor.process(tenantId, records);

        Map<String, BearerCredentialVerifier.Outcome> outcomes = new LinkedHashMap<>();
        for (int i = 0; i < manifests.size(); i++) {
            CustomsEdiAdapter.EdiManifest manifest = manifests.get(i);
            ManifestRecord.Result result = results.get(i);
            outcomes.put(manifest.getManifestId(), result.isValid()
                    ? BearerCredentialVerifier.Outcome.valid()
                    : BearerCredentialVerifier.Outcome.invalid(String.join("; ", result.getReasons())));
        }
        LOGGER.info("Verified Single Window declaration {} for {}: {} manifests, {} accepted",
                ediMessage.get("declarationNumber"), tenantId, results.size(),
                results.stream().filter(ManifestRecord.Result::isValid).count());
        return adapter.toResponse(ediMessage, outcomes);
    }
}
