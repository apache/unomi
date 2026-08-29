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

import java.util.List;

/**
 * Split-knowledge compliance workflow (FR-G4): re-identification of a
 * pairwise subject reference requires approvals from two distinct
 * custodians — the KYC-evidence custodian and the credential-operator
 * custodian. Neither custodian alone can resolve a subject; every step
 * (request, each approval, resolution) is recorded on the request's
 * audit trail.
 */
public interface SplitKnowledgeService {

    /**
     * Opens a re-identification request for a pairwise subject
     * reference under a legal-process justification.
     *
     * @param tenantId     the tenant whose compliance domain governs the request
     * @param pairwiseRef  the verifier-scoped pairwise reference to re-identify
     * @param justification the legal-process reference (e.g. a court order id)
     * @return the request id
     */
    String createReidentificationRequest(String tenantId, String pairwiseRef, String justification);

    /**
     * Records a custodian's approval. Approving twice does not advance
     * the workflow — two distinct custodians are required.
     *
     * @param requestId the request id
     * @param custodian the approving custodian
     * @return true when the approval was newly recorded
     */
    boolean approve(String requestId, SplitKnowledgeCustodian custodian);

    /**
     * Attempts resolution. Succeeds exactly once, only when both
     * custodians have approved; the resolution event is appended to the
     * audit trail.
     *
     * @param requestId the request id
     * @return the resolution — resolved only on the first successful attempt
     */
    Resolution tryResolve(String requestId);

    /**
     * The outcome of a resolution attempt.
     */
    class Resolution {
        private final boolean resolved;
        private final String subjectId;
        private final List<String> approvals;
        private final List<String> auditTrail;

        public Resolution(boolean resolved, String subjectId, List<String> approvals,
                          List<String> auditTrail) {
            this.resolved = resolved;
            this.subjectId = subjectId;
            this.approvals = approvals;
            this.auditTrail = auditTrail;
        }

        public boolean isResolved() {
            return resolved;
        }

        /**
         * The re-identified subject (profile id), present only when
         * resolved.
         */
        public String getSubjectId() {
            return subjectId;
        }

        public List<String> getApprovals() {
            return approvals;
        }

        /**
         * The step-by-step audit trail: request creation, each approval
         * (with custodian and timestamp) and the resolution.
         */
        public List<String> getAuditTrail() {
            return auditTrail;
        }
    }
}
