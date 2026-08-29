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

package org.apache.unomi.didvc.batch;

import org.apache.unomi.didvc.audit.AuditLogService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * One manifest in a bulk verification batch: a caller correlation id
 * and the bearer credentials that make up the manifest (corporate
 * identity plus per-consignment cargo attestations).
 */
public class ManifestRecord {

    private final String manifestId;
    private final List<String> credentials;

    public ManifestRecord(String manifestId, List<String> credentials) {
        this.manifestId = manifestId;
        this.credentials = credentials;
    }

    public String getManifestId() {
        return manifestId;
    }

    public List<String> getCredentials() {
        return credentials;
    }

    /**
     * The outcome of verifying one manifest.
     */
    public static class Result {
        private final String manifestId;
        private final boolean valid;
        private final List<String> reasons;

        public Result(String manifestId, boolean valid, List<String> reasons) {
            this.manifestId = manifestId;
            this.valid = valid;
            this.reasons = reasons;
        }

        public String getManifestId() {
            return manifestId;
        }

        public boolean isValid() {
            return valid;
        }

        public List<String> getReasons() {
            return reasons;
        }
    }

    /**
     * Processes a batch of manifests (FR-L3): verifies each manifest's
     * credentials through the supplied verifier, appends a per-record
     * audit entry for every manifest (linked by manifest id), and hands
     * each result to the sink (the Kafka publisher in production; a
     * recording sink in tests). A manifest is valid when every one of
     * its credentials verifies; reasons accumulate per manifest.
     */
    public static class Processor {

        private final BiFunction<String, String, Boolean> verifier;
        private final BiFunction<String, String, String> failureReason;
        private final AuditLogService auditLogService;
        private final java.util.function.Consumer<Result> sink;

        /**
         * Creates a processor.
         *
         * @param verifier        returns whether one credential verifies
         *                        for a tenant
         * @param failureReason   returns the failure reason for a
         *                        credential that did not verify
         * @param auditLogService the immutable audit log (per-record
         *                        entries)
         * @param sink            consumes each result (Kafka publisher
         *                        in production)
         */
        public Processor(BiFunction<String, String, Boolean> verifier,
                         BiFunction<String, String, String> failureReason,
                         AuditLogService auditLogService,
                         java.util.function.Consumer<Result> sink) {
            this.verifier = verifier;
            this.failureReason = failureReason;
            this.auditLogService = auditLogService;
            this.sink = sink;
        }

        /**
         * Processes the batch.
         *
         * @param tenantId  the relying tenant (the logistics counterparty)
         * @param manifests the manifests
         * @return the per-manifest results, input order preserved
         */
        public List<Result> process(String tenantId, List<ManifestRecord> manifests) {
            List<Result> results = new ArrayList<>();
            for (ManifestRecord manifest : manifests) {
                List<String> reasons = new ArrayList<>();
                for (String credential : manifest.getCredentials()) {
                    if (!verifier.apply(tenantId, credential)) {
                        reasons.add(failureReason.apply(tenantId, credential));
                    }
                }
                Result result = new Result(manifest.getManifestId(), reasons.isEmpty(), reasons);
                audit(tenantId, result);
                if (sink != null) {
                    sink.accept(result);
                }
                results.add(result);
            }
            return results;
        }

        private void audit(String tenantId, Result result) {
            try {
                String payload = "{\"manifestId\":\"" + result.getManifestId()
                        + "\",\"valid\":" + result.isValid()
                        + (result.getReasons().isEmpty() ? "" : ",\"reasons\":" + reasonsJson(result.getReasons()))
                        + "}";
                auditLogService.append("didvcManifestVerified", tenantId, "manifest:" + result.getManifestId(),
                        payload);
            } catch (Exception ignored) {
                // audit failure must not break the pipeline; the hash
                // chain verification surfaces store problems separately
            }
        }

        private static String reasonsJson(List<String> reasons) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < reasons.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append('"').append(reasons.get(i).replace("\"", "'")).append('"');
            }
            return sb.append(']').toString();
        }
    }

    /**
     * Generates a manifest id for callers that do not carry their own.
     */
    public static String newManifestId() {
        return "manifest-" + UUID.randomUUID();
    }
}
