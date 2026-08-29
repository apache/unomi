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

import org.apache.unomi.didvc.api.items.ReidentificationRequest;
import org.apache.unomi.didvc.api.services.PairwiseBindingService;
import org.apache.unomi.didvc.api.services.SplitKnowledgeCustodian;
import org.apache.unomi.didvc.api.services.SplitKnowledgeService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Split-knowledge re-identification workflow (FR-G4). Resolution of a
 * pairwise subject reference is structurally impossible until both
 * custodians — KYC-evidence and credential-operator — have approved the
 * same request, and it can be released exactly once. Every step
 * (request creation, each approval with custodian and timestamp, the
 * resolution) is appended to the request's audit trail, which persists
 * with the request for compliance review.
 */
@Component(service = SplitKnowledgeService.class, immediate = true)
public class SplitKnowledgeServiceImpl implements SplitKnowledgeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SplitKnowledgeServiceImpl.class);

    @Reference
    private PersistenceService persistenceService;
    @Reference
    private PairwiseBindingService pairwiseBindingService;

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setPairwiseBindingService(PairwiseBindingService pairwiseBindingService) {
        this.pairwiseBindingService = pairwiseBindingService;
    }

    @Override
    public String createReidentificationRequest(String tenantId, String pairwiseRef, String justification) {
        if (pairwiseRef == null || pairwiseRef.isEmpty()) {
            throw new IllegalArgumentException("pairwiseRef is required");
        }
        if (justification == null || justification.isEmpty()) {
            throw new IllegalArgumentException("a legal-process justification is required");
        }
        ReidentificationRequest request = new ReidentificationRequest("didvc-reid-" + UUID.randomUUID());
        request.setPairwiseRef(pairwiseRef);
        request.setJustification(justification);
        request.setRequestedBy(tenantId);
        request.setRequestedAt(new Date());
        request.setResolved(false);
        request.setScope("didvc");
        request.setTenantId(tenantId);
        appendStep(request, "requested: pairwise=" + pairwiseRef + " justification=" + justification
                + " at=" + request.getRequestedAt().getTime());
        persistenceService.save(request);
        LOGGER.info("Opened split-knowledge re-identification request {} for {} (justification={})",
                request.getItemId(), tenantId, justification);
        return request.getItemId();
    }

    @Override
    public boolean approve(String requestId, SplitKnowledgeCustodian custodian) {
        ReidentificationRequest request = load(requestId);
        if (request == null || request.isResolved()) {
            return false;
        }
        if (request.getApprovals().contains(custodian.getRoleName())) {
            // A repeat approval by the same custodian is a no-op: two
            // distinct custodians are required
            appendStep(request, "duplicate-approval: custodian=" + custodian.getRoleName());
            persistenceService.save(request);
            return false;
        }
        request.getApprovals().add(custodian.getRoleName());
        appendStep(request, "approved: custodian=" + custodian.getRoleName()
                + " at=" + System.currentTimeMillis());
        persistenceService.save(request);
        LOGGER.info("Split-knowledge request {} approved by {}", requestId, custodian.getRoleName());
        return true;
    }

    @Override
    public Resolution tryResolve(String requestId) {
        ReidentificationRequest request = load(requestId);
        if (request == null) {
            return new Resolution(false, null, List.of(), List.of());
        }
        boolean bothCustodians = request.getApprovals().contains(SplitKnowledgeCustodian.KYC_CUSTODIAN.getRoleName())
                && request.getApprovals().contains(SplitKnowledgeCustodian.OPERATOR_CUSTODIAN.getRoleName());
        if (!request.isResolved() && bothCustodians) {
            String subjectId = resolveSubject(request);
            request.setResolved(true);
            appendStep(request, "resolved: subject=" + subjectId + " at=" + System.currentTimeMillis());
            persistenceService.save(request);
            LOGGER.info("Split-knowledge request {} resolved (dual custody satisfied)", requestId);
            return new Resolution(true, subjectId, request.getApprovals(), request.getAuditTrail());
        }
        if (request.isResolved()) {
            appendStep(request, "resolution-attempt-after-resolved at=" + System.currentTimeMillis());
            persistenceService.save(request);
        } else {
            appendStep(request, "resolution-denied: approvals=" + request.getApprovals().size()
                    + " at=" + System.currentTimeMillis());
            persistenceService.save(request);
        }
        return new Resolution(false, null, request.getApprovals(), request.getAuditTrail());
    }

    private String resolveSubject(ReidentificationRequest request) {
        // The pairwise service holds the linkage half; it only becomes
        // reachable through this dual-custody workflow
        String profileId = pairwiseBindingService.resolveProfileId(request.getRequestedBy(), request.getPairwiseRef());
        if (profileId == null) {
            throw new IllegalStateException("unknown pairwise reference: " + request.getPairwiseRef());
        }
        return profileId;
    }

    private ReidentificationRequest load(String requestId) {
        if (requestId == null) {
            return null;
        }
        return persistenceService.load(requestId, ReidentificationRequest.class);
    }

    private void appendStep(ReidentificationRequest request, String step) {
        request.getAuditTrail().add(step);
    }
}
