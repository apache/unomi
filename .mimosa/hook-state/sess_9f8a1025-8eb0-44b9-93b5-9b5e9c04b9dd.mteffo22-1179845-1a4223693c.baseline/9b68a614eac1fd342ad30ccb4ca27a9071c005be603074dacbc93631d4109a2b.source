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
import java.util.Map;

/**
 * Metadata for one issued verifiable credential: schema, subject, issuer key,
 * status-list position, validity window and the serialized credential itself.
 * The credential is signed data, so storing it is safe; private key material is
 * never stored here (see {@code KeyDescriptor}, which carries public keys only).
 */
public class CredentialRecord extends Item {
    /**
     * The CredentialRecord ITEM_TYPE.
     */
    public static final String ITEM_TYPE = "didvc-credential-record";
    private static final long serialVersionUID = -8973084450948835440L;

    private String schemaId;
    private String subjectId;
    private String subjectType;
    private String kid;
    private String verifierCategory;
    private String statusListId;
    private Integer statusListIndex;
    private String format;
    private String credential;
    private Date issuedAt;
    private Date expiresAt;
    private boolean revoked;
    private boolean refreshDue;
    private Map<String, Object> alwaysDisclosedClaims;
    private Map<String, Object> selectivelyDisclosedClaims;
    private String holderPublicJwkJson;

    /**
     * Default constructor.
     */
    public CredentialRecord() {
    }

    /**
     * Creates a credential record with the given identifier.
     *
     * @param recordId the record identifier
     */
    public CredentialRecord(String recordId) {
        super(recordId);
        this.itemType = ITEM_TYPE;
    }

    public String getSchemaId() {
        return schemaId;
    }

    public void setSchemaId(String schemaId) {
        this.schemaId = schemaId;
    }

    /**
     * Subject reference: a profile id, or an opaque pairwise reference when the
     * credential is issued under a per-verifier pseudonym.
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

    public String getKid() {
        return kid;
    }

    public void setKid(String kid) {
        this.kid = kid;
    }

    public String getStatusListId() {
        return statusListId;
    }

    public void setStatusListId(String statusListId) {
        this.statusListId = statusListId;
    }

    public Integer getStatusListIndex() {
        return statusListIndex;
    }

    public void setStatusListIndex(Integer statusListIndex) {
        this.statusListIndex = statusListIndex;
    }

    /**
     * Credential format, e.g. {@code vc+sd-jwt} or {@code ldp_vc}.
     */
    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    /**
     * The serialized (signed) credential.
     */
    public String getCredential() {
        return credential;
    }

    public void setCredential(String credential) {
        this.credential = credential;
    }

    public Date getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(Date issuedAt) {
        this.issuedAt = issuedAt;
    }

    public Date getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Date expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public void setRevoked(boolean revoked) {
        this.revoked = revoked;
    }

    /**
     * True when the credential is inside its re-verification window or its
     * subject's identity evidence changed (e.g. SIM re-registration).
     */
    public boolean isRefreshDue() {
        return refreshDue;
    }

    public void setRefreshDue(boolean refreshDue) {
        this.refreshDue = refreshDue;
    }

    /**
     * The verifier category the disclosure consent was scoped to at
     * issuance; retained for re-issuance with holder binding.
     */
    public String getVerifierCategory() {
        return verifierCategory;
    }

    public void setVerifierCategory(String verifierCategory) {
        this.verifierCategory = verifierCategory;
    }

    /**
     * The always-disclosed claims as issued; retained so the credential can
     * be re-issued with a holder key at credential-request time.
     */
    public Map<String, Object> getAlwaysDisclosedClaims() {
        return alwaysDisclosedClaims;
    }

    public void setAlwaysDisclosedClaims(Map<String, Object> alwaysDisclosedClaims) {
        this.alwaysDisclosedClaims = alwaysDisclosedClaims;
    }

    /**
     * The selectively-disclosable claims as issued.
     */
    public Map<String, Object> getSelectivelyDisclosedClaims() {
        return selectivelyDisclosedClaims;
    }

    public void setSelectivelyDisclosedClaims(Map<String, Object> selectivelyDisclosedClaims) {
        this.selectivelyDisclosedClaims = selectivelyDisclosedClaims;
    }

    /**
     * The holder public JWK the credential is bound to via cnf.jwk; null
     * for bearer credentials.
     */
    public String getHolderPublicJwkJson() {
        return holderPublicJwkJson;
    }

    public void setHolderPublicJwkJson(String holderPublicJwkJson) {
        this.holderPublicJwkJson = holderPublicJwkJson;
    }
}
