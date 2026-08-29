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

package org.apache.unomi.didvc.api;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Request to issue a verifiable credential. Claims are split into
 * always-disclosed and selectively-disclosable sets; selective disclosure is
 * the data-minimization mechanism a verifier sees in action.
 */
public class CredentialIssueRequest {

    private String tenantId;
    private String schemaId;
    private String subjectId;
    private String subjectType;
    private String kid;
    private String verifierCategory;
    private String holderPublicJwkJson;
    private String format;
    private int validityDays = 365;
    private int statusListIndex = -1;
    private String statusListUri;
    private final Map<String, Object> alwaysDisclosedClaims = new LinkedHashMap<>();
    private final Map<String, Object> selectivelyDisclosedClaims = new LinkedHashMap<>();

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getSchemaId() {
        return schemaId;
    }

    public void setSchemaId(String schemaId) {
        this.schemaId = schemaId;
    }

    /**
     * Subject reference: a profile id, or an opaque pairwise reference when
     * issued under a per-verifier pseudonym.
     */
    public String getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(String subjectId) {
        this.subjectId = subjectId;
    }

    public String getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(String subjectType) {
        this.subjectType = subjectType;
    }

    /**
     * Issuer signing key identifier.
     */
    public String getKid() {
        return kid;
    }

    public void setKid(String kid) {
        this.kid = kid;
    }

    /**
     * The relying-party category the consent grant is scoped to, e.g.
     * {@code financial-institution} or {@code customs}.
     */
    public String getVerifierCategory() {
        return verifierCategory;
    }

    public void setVerifierCategory(String verifierCategory) {
        this.verifierCategory = verifierCategory;
    }

    /**
     * Holder public JWK JSON, bound via {@code cnf.jwk}; null issues a
     * bearer credential.
     */
    public String getHolderPublicJwkJson() {
        return holderPublicJwkJson;
    }

    public void setHolderPublicJwkJson(String holderPublicJwkJson) {
        this.holderPublicJwkJson = holderPublicJwkJson;
    }

    /**
     * Requested credential format ({@code dc+sd-jwt} or {@code ldp_vc});
     * null selects the platform's default formatter.
     */
    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public int getValidityDays() {
        return validityDays;
    }

    public void setValidityDays(int validityDays) {
        this.validityDays = validityDays;
    }

    /**
     * Pre-allocated status-list index, or -1 to allocate at issuance.
     */
    public int getStatusListIndex() {
        return statusListIndex;
    }

    public void setStatusListIndex(int statusListIndex) {
        this.statusListIndex = statusListIndex;
    }

    /**
     * Public URI of the status list the credential's status points at.
     */
    public String getStatusListUri() {
        return statusListUri;
    }

    public void setStatusListUri(String statusListUri) {
        this.statusListUri = statusListUri;
    }

    public Map<String, Object> getAlwaysDisclosedClaims() {
        return alwaysDisclosedClaims;
    }

    public Map<String, Object> getSelectivelyDisclosedClaims() {
        return selectivelyDisclosedClaims;
    }

    /**
     * Convenience: the full claim set (both maps merged) for schema
     * validation.
     */
    public Map<String, Object> allClaims() {
        Map<String, Object> all = new LinkedHashMap<>();
        all.putAll(alwaysDisclosedClaims);
        all.putAll(selectivelyDisclosedClaims);
        return all;
    }

    /**
     * A convenience issuance time (unused by the formatter, which stamps its
     * own time claims).
     */
    public Date getIssuedAt() {
        return new Date();
    }
}
