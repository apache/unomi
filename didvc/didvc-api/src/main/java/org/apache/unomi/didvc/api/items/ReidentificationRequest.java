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

package org.apache.unomi.didvc.api.items;

import org.apache.unomi.api.Item;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * A split-knowledge re-identification request (FR-G4): a pairwise
 * subject reference to be re-identified under a legal-process
 * justification, the custodian approvals collected so far, the
 * step-by-step audit trail, and the single-use resolution state.
 */
public class ReidentificationRequest extends Item {

    /**
     * The ReidentificationRequest ITEM_TYPE.
     */
    public static final String ITEM_TYPE = "didvc-reidentification-request";
    private static final long serialVersionUID = 2718281828459045247L;

    private String pairwiseRef;
    private String justification;
    private String requestedBy;
    private Date requestedAt;
    /** Custodian role names that have approved, in approval order. */
    private List<String> approvals = new ArrayList<>();
    /** One entry per workflow step (request, approvals, resolution). */
    private List<String> auditTrail = new ArrayList<>();
    private boolean resolved;

    /**
     * Default constructor.
     */
    public ReidentificationRequest() {
    }

    public ReidentificationRequest(String requestId) {
        super(requestId);
        this.itemType = ITEM_TYPE;
    }

    public String getPairwiseRef() {
        return pairwiseRef;
    }

    public void setPairwiseRef(String pairwiseRef) {
        this.pairwiseRef = pairwiseRef;
    }

    /**
     * The legal-process reference (e.g. a court order id).
     */
    public String getJustification() {
        return justification;
    }

    public void setJustification(String justification) {
        this.justification = justification;
    }

    /**
     * The tenant whose compliance domain opened the request.
     */
    public String getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(String requestedBy) {
        this.requestedBy = requestedBy;
    }

    public Date getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(Date requestedAt) {
        this.requestedAt = requestedAt;
    }

    public List<String> getApprovals() {
        return approvals;
    }

    public void setApprovals(List<String> approvals) {
        this.approvals = approvals;
    }

    public List<String> getAuditTrail() {
        return auditTrail;
    }

    public void setAuditTrail(List<String> auditTrail) {
        this.auditTrail = auditTrail;
    }

    /**
     * True once the single-use resolution has been released.
     */
    public boolean isResolved() {
        return resolved;
    }

    public void setResolved(boolean resolved) {
        this.resolved = resolved;
    }
}
