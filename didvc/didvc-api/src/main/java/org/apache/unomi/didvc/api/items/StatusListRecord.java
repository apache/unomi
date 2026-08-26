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

/**
 * A W3C Bitstring Status List (v1.0): the compressed bitstring encoding
 * credential status, the purpose it serves (revocation or suspension), and —
 * once published — the signed status-list JWT verifiers fetch at check time.
 */
public class StatusListRecord extends Item {
    /**
     * The StatusListRecord ITEM_TYPE.
     */
    public static final String ITEM_TYPE = "didvc:status-list";
    private static final long serialVersionUID = 2951849889360368812L;

    private String statusPurpose;
    private String encodedList;
    private int size;
    private int nextIndex;
    private String issuerDid;
    private String kid;
    private String signedJwt;
    private String statusListId;

    /**
     * Default constructor.
     */
    public StatusListRecord() {
    }

    /**
     * Creates a status list with the given identifier.
     *
     * @param recordId the record identifier
     */
    public StatusListRecord(String recordId) {
        super(recordId);
        this.itemType = ITEM_TYPE;
    }

    /**
     * {@code revocation} or {@code suspension}.
     */
    public String getStatusPurpose() {
        return statusPurpose;
    }

    public void setStatusPurpose(String statusPurpose) {
        this.statusPurpose = statusPurpose;
    }

    /**
     * GZIP-compressed, base64url-encoded bitstring per Bitstring Status List.
     */
    public String getEncodedList() {
        return encodedList;
    }

    public void setEncodedList(String encodedList) {
        this.encodedList = encodedList;
    }

    /**
     * Number of status entries (bits) this list can hold.
     */
    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    /**
     * Index of the next status entry to allocate.
     */
    public int getNextIndex() {
        return nextIndex;
    }

    public void setNextIndex(int nextIndex) {
        this.nextIndex = nextIndex;
    }

    public String getIssuerDid() {
        return issuerDid;
    }

    public void setIssuerDid(String issuerDid) {
        this.issuerDid = issuerDid;
    }

    public String getKid() {
        return kid;
    }

    public void setKid(String kid) {
        this.kid = kid;
    }

    /**
     * The signed status-list JWT, present after {@code publish}.
     */
    public String getSignedJwt() {
        return signedJwt;
    }

    public void setSignedJwt(String signedJwt) {
        this.signedJwt = signedJwt;
    }

    /**
     * Public identifier used as the {@code id} of the published status list.
     */
    public String getStatusListId() {
        return statusListId;
    }

    public void setStatusListId(String statusListId) {
        this.statusListId = statusListId;
    }
}
