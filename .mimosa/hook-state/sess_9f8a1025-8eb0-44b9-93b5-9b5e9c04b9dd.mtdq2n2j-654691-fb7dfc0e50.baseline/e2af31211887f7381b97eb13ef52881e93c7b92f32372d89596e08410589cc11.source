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
 * A trust-registry entry: a relying tenant accepts credentials of a given type
 * (vct) issued by a given issuer DID, at an accreditation level, within a
 * validity window. Checked on every verification.
 */
public class TrustEntry extends Item {
    /**
     * The TrustEntry ITEM_TYPE.
     */
    public static final String ITEM_TYPE = "didvc-trust-entry";
    private static final long serialVersionUID = 120900975795369222L;

    private String issuerDid;
    private String vct;
    private String accreditationLevel;
    private Date validFrom;
    private Date validUntil;
    private String status;

    /**
     * Default constructor.
     */
    public TrustEntry() {
    }

    /**
     * Creates a trust entry with the given identifier.
     *
     * @param entryId the entry identifier
     */
    public TrustEntry(String entryId) {
        super(entryId);
        this.itemType = ITEM_TYPE;
    }

    public String getIssuerDid() {
        return issuerDid;
    }

    public void setIssuerDid(String issuerDid) {
        this.issuerDid = issuerDid;
    }

    public String getVct() {
        return vct;
    }

    public void setVct(String vct) {
        this.vct = vct;
    }

    public String getAccreditationLevel() {
        return accreditationLevel;
    }

    public void setAccreditationLevel(String accreditationLevel) {
        this.accreditationLevel = accreditationLevel;
    }

    public Date getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(Date validFrom) {
        this.validFrom = validFrom;
    }

    public Date getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(Date validUntil) {
        this.validUntil = validUntil;
    }

    /**
     * {@code active} or {@code revoked}.
     */
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
