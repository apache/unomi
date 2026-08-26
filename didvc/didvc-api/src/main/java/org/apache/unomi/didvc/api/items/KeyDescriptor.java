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
 * Metadata for an issuer signing key. Holds the public JWK only; private key
 * material lives in the key-material provider (HSM/KMS) and is referenced by
 * kid, never persisted in plaintext.
 */
public class KeyDescriptor extends Item {
    /**
     * The KeyDescriptor ITEM_TYPE.
     */
    public static final String ITEM_TYPE = "didvc:key-descriptor";
    private static final long serialVersionUID = 6806377569896859132L;

    private String kid;
    private String alg;
    private String keyType;
    private String issuerDid;
    private String publicJwk;
    private Date validFrom;
    private Date validUntil;
    private Date rotationDueDate;

    /**
     * Default constructor.
     */
    public KeyDescriptor() {
    }

    /**
     * Creates a key descriptor with the given identifier (the kid).
     *
     * @param kid the key identifier
     */
    public KeyDescriptor(String kid) {
        super(kid);
        this.kid = kid;
        this.itemType = ITEM_TYPE;
    }

    public String getKid() {
        return kid;
    }

    public void setKid(String kid) {
        this.kid = kid;
    }

    /**
     * JWS algorithm name, e.g. {@code EdDSA} or {@code ES256}.
     */
    public String getAlg() {
        return alg;
    }

    public void setAlg(String alg) {
        this.alg = alg;
    }

    /**
     * JWK key type, e.g. {@code OKP} or {@code EC}.
     */
    public String getKeyType() {
        return keyType;
    }

    public void setKeyType(String keyType) {
        this.keyType = keyType;
    }

    public String getIssuerDid() {
        return issuerDid;
    }

    public void setIssuerDid(String issuerDid) {
        this.issuerDid = issuerDid;
    }

    /**
     * Public JWK as JSON. Must never contain private parameters ({@code d}).
     */
    public String getPublicJwk() {
        return publicJwk;
    }

    public void setPublicJwk(String publicJwk) {
        this.publicJwk = publicJwk;
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

    public Date getRotationDueDate() {
        return rotationDueDate;
    }

    public void setRotationDueDate(Date rotationDueDate) {
        this.rotationDueDate = rotationDueDate;
    }
}
