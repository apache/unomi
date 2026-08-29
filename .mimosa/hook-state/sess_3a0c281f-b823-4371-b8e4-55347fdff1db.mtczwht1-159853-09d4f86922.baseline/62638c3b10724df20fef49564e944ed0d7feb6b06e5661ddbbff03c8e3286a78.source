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

package org.apache.unomi.didvc.edge.platform;

import com.nimbusds.jose.jwk.JWK;

import java.util.Map;

/**
 * The platform operations the credential edge needs: issuance, credential
 * retrieval, revocation status, trust checks and issuer key resolution.
 * Implemented over the Unomi REST API; faked in tests.
 */
public interface PlatformApi {

    /**
     * Issues a credential through the platform's orchestration pipeline.
     *
     * @param tenantId the platform tenant
     * @param request  the issue request
     * @return the issued credential
     */
    IssuedCredential issueCredential(String tenantId, IssueRequest request);

    /**
     * Loads an issued credential by record id.
     *
     * @param tenantId the platform tenant
     * @param recordId the record id
     * @return the credential, or null
     */
    IssuedCredential getCredential(String tenantId, String recordId);

    /**
     * Re-issues a credential with holder key binding (cnf.jwk) using the
     * claims stored at original issuance.
     *
     * @param tenantId           the platform tenant
     * @param recordId           the record id
     * @param holderPublicJwkJson the holder's public JWK
     * @return the rebound credential, or null
     */
    IssuedCredential rebindCredential(String tenantId, String recordId, String holderPublicJwkJson);

    /**
     * Checks the revocation status of a status-list entry.
     *
     * @param tenantId     the platform tenant
     * @param statusListId the status list id (last segment of the status uri)
     * @param index        the status index
     * @return true when revoked
     */
    boolean isStatusRevoked(String tenantId, String statusListId, int index);

    /**
     * Trust-registry check for a relying tenant.
     *
     * @param tenantId  the relying tenant
     * @param issuerDid the credential issuer DID
     * @param vct       the credential type
     * @return true when trusted
     */
    boolean isTrusted(String tenantId, String issuerDid, String vct);

    /**
     * The default issuer key id, or null when the platform requires an
     * explicit kid on every issue request.
     */
    default String getDefaultIssuerKid() {
        return null;
    }

    /**
     * Resolves an issuer's public JWK from its DID document.
     *
     * @param issuerDid the issuer DID
     * @param kid       the key identifier
     * @return the public JWK, or null when unknown
     */
    JWK resolveIssuerKey(String issuerDid, String kid);

    /**
     * An issued credential as returned by the platform.
     */
    class IssuedCredential {
        private String itemId;
        private String schemaId;
        private String subjectId;
        private String format;
        private String credential;
        private Integer statusListIndex;
        private String statusListId;
        private Long expiresAt;
        private boolean revoked;

        public String getItemId() {
            return itemId;
        }

        public void setItemId(String itemId) {
            this.itemId = itemId;
        }

        public String getSchemaId() {
            return schemaId;
        }

        public void setSchemaId(String schemaId) {
            this.schemaId = schemaId;
        }

        public String getSubjectId() {
            return subjectId;
        }

        public void setSubjectId(String subjectId) {
            this.subjectId = subjectId;
        }

        public String getFormat() {
            return format;
        }

        public void setFormat(String format) {
            this.format = format;
        }

        public String getCredential() {
            return credential;
        }

        public void setCredential(String credential) {
            this.credential = credential;
        }

        public Integer getStatusListIndex() {
            return statusListIndex;
        }

        public void setStatusListIndex(Integer statusListIndex) {
            this.statusListIndex = statusListIndex;
        }

        public String getStatusListId() {
            return statusListId;
        }

        public void setStatusListId(String statusListId) {
            this.statusListId = statusListId;
        }

        public Long getExpiresAt() {
            return expiresAt;
        }

        public void setExpiresAt(Long expiresAt) {
            this.expiresAt = expiresAt;
        }

        public boolean isRevoked() {
            return revoked;
        }

        public void setRevoked(boolean revoked) {
            this.revoked = revoked;
        }
    }

    /**
     * Issue request as sent to the platform REST API.
     */
    class IssueRequest {
        private String tenantId;
        private String schemaId;
        private String subjectId;
        private String subjectType;
        private String kid;
        private String verifierCategory;
        private String holderPublicJwkJson;
        private Integer validityDays;
        private Map<String, Object> alwaysDisclosedClaims;
        private Map<String, Object> selectivelyDisclosedClaims;

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

        public String getKid() {
            return kid;
        }

        public void setKid(String kid) {
            this.kid = kid;
        }

        public String getVerifierCategory() {
            return verifierCategory;
        }

        public void setVerifierCategory(String verifierCategory) {
            this.verifierCategory = verifierCategory;
        }

        public String getHolderPublicJwkJson() {
            return holderPublicJwkJson;
        }

        public void setHolderPublicJwkJson(String holderPublicJwkJson) {
            this.holderPublicJwkJson = holderPublicJwkJson;
        }

        public Integer getValidityDays() {
            return validityDays;
        }

        public void setValidityDays(Integer validityDays) {
            this.validityDays = validityDays;
        }

        public Map<String, Object> getAlwaysDisclosedClaims() {
            return alwaysDisclosedClaims;
        }

        public void setAlwaysDisclosedClaims(Map<String, Object> alwaysDisclosedClaims) {
            this.alwaysDisclosedClaims = alwaysDisclosedClaims;
        }

        public Map<String, Object> getSelectivelyDisclosedClaims() {
            return selectivelyDisclosedClaims;
        }

        public void setSelectivelyDisclosedClaims(Map<String, Object> selectivelyDisclosedClaims) {
            this.selectivelyDisclosedClaims = selectivelyDisclosedClaims;
        }
    }
}
