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
 * A published {@code did:web} DID document: the DID, the domain and optional
 * path it resolves from, and the DID-document JSON as published. Deactivation
 * flags the DID as no longer resolvable without deleting the record.
 */
public class DidDocumentRecord extends Item {
    /**
     * The DidDocumentRecord ITEM_TYPE.
     */
    public static final String ITEM_TYPE = "didvc-did-document";
    private static final long serialVersionUID = -4347390670551173831L;

    private String did;
    private String domain;
    private String path;
    private String json;
    private boolean deactivated;

    /**
     * Default constructor.
     */
    public DidDocumentRecord() {
    }

    /**
     * Creates a DID document record with the given identifier (the DID).
     *
     * @param did the DID
     */
    public DidDocumentRecord(String did) {
        super(did);
        this.did = did;
        this.itemType = ITEM_TYPE;
    }

    public String getDid() {
        return did;
    }

    public void setDid(String did) {
        this.did = did;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    /**
     * The DID-document JSON as published.
     */
    public String getJson() {
        return json;
    }

    public void setJson(String json) {
        this.json = json;
    }

    public boolean isDeactivated() {
        return deactivated;
    }

    public void setDeactivated(boolean deactivated) {
        this.deactivated = deactivated;
    }
}
