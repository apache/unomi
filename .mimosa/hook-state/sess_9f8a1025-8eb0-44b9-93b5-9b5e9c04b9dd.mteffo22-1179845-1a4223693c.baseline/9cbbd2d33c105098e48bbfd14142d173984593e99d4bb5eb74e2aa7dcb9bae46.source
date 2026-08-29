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

import java.util.Date;

/**
 * A per-verifier pseudonymous subject reference: maps a profile to an opaque
 * reference that is unique per relying tenant, so verifiers cannot correlate a
 * subject across institutions. This is the linkage half of the split-knowledge
 * pattern — the identity half (profile resolution) is never reachable from the
 * verification path.
 */
public class PairwiseBindingRecord extends Item {
    /**
     * The PairwiseBindingRecord ITEM_TYPE.
     */
    public static final String ITEM_TYPE = "didvc-pairwise-binding";
    private static final long serialVersionUID = 7992458362288032527L;

    private String profileId;
    private String verifierTenantId;
    private String opaqueReference;
    private Date createdAt;

    /**
     * Default constructor.
     */
    public PairwiseBindingRecord() {
    }

    /**
     * Creates a pairwise binding with the given identifier.
     *
     * @param recordId the record identifier
     */
    public PairwiseBindingRecord(String recordId) {
        super(recordId);
        this.itemType = ITEM_TYPE;
    }

    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    public String getVerifierTenantId() {
        return verifierTenantId;
    }

    public void setVerifierTenantId(String verifierTenantId) {
        this.verifierTenantId = verifierTenantId;
    }

    /**
     * The verifier-scoped opaque reference (eIDAS-style sector-specific
     * pseudonym). Different verifiers receive different references.
     */
    public String getOpaqueReference() {
        return opaqueReference;
    }

    public void setOpaqueReference(String opaqueReference) {
        this.opaqueReference = opaqueReference;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
}
