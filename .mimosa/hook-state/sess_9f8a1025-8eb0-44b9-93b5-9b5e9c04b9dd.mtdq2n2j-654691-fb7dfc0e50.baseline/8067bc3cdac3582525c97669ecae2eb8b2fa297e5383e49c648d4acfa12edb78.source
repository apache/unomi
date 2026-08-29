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
import java.util.HashSet;
import java.util.Set;

/**
 * A consent grant: which claims of a credential schema a subject has
 * authorized for disclosure to a given verifier category. The issuance and
 * verification paths check these grants — selective disclosure never exceeds
 * the granted claim set.
 */
public class ConsentGrantRecord extends Item {
    /**
     * The ConsentGrantRecord ITEM_TYPE.
     */
    public static final String ITEM_TYPE = "didvc-consent-grant";
    private static final long serialVersionUID = 4309255390466992494L;

    private String subjectId;
    private String schemaId;
    private String verifierCategory;
    private Set<String> claims = new HashSet<>();
    private Date grantedAt;

    /**
     * Default constructor.
     */
    public ConsentGrantRecord() {
    }

    /**
     * Creates a consent grant with the given identifier.
     *
     * @param grantId the grant identifier
     */
    public ConsentGrantRecord(String grantId) {
        super(grantId);
        this.itemType = ITEM_TYPE;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(String subjectId) {
        this.subjectId = subjectId;
    }

    public String getSchemaId() {
        return schemaId;
    }

    public void setSchemaId(String schemaId) {
        this.schemaId = schemaId;
    }

    public String getVerifierCategory() {
        return verifierCategory;
    }

    public void setVerifierCategory(String verifierCategory) {
        this.verifierCategory = verifierCategory;
    }

    /**
     * The claims the subject authorized for disclosure.
     */
    public Set<String> getClaims() {
        return claims;
    }

    public void setClaims(Set<String> claims) {
        this.claims = claims;
    }

    public Date getGrantedAt() {
        return grantedAt;
    }

    public void setGrantedAt(Date grantedAt) {
        this.grantedAt = grantedAt;
    }
}
