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

package org.apache.unomi.didvc.api.services;

import org.apache.unomi.didvc.api.DidDocumentData;

import java.util.List;

/**
 * W3C DID Core operations for {@code did:web} identifiers: creation,
 * resolution, key rotation and deactivation, backed by persisted DID document
 * records.
 */
public interface DidService {

    /**
     * Creates a new did:web DID and its DID document.
     *
     * @param tenantId  the tenant that owns the DID
     * @param domain    the domain the DID resolves from, e.g. {@code id.example.hkt}
     * @param path      optional path, e.g. {@code didvc/issuers/bank-a}; may be null
     * @param algorithm key algorithm for the initial verification method,
     *                  {@code EdDSA} or {@code ES256}
     * @return the created DID document
     */
    DidDocumentData createDid(String tenantId, String domain, String path, String algorithm);

    /**
     * Resolves a DID to its DID document, or null if unknown.
     *
     * @param did the DID
     * @return the DID document, or null
     */
    DidDocumentData resolveDid(String did);

    /**
     * Generates a new verification method (key rotation) and adds it to the DID
     * document, keeping the previous key usable until it is removed.
     *
     * @param did       the DID
     * @param algorithm key algorithm for the new verification method
     * @return the updated DID document, or null if the DID is unknown
     */
    DidDocumentData rotateKey(String did, String algorithm);

    /**
     * Deactivates a DID: marks it as no longer resolvable. The record is kept
     * for audit purposes.
     *
     * @param did the DID
     * @return the updated DID document, or null if the DID is unknown
     */
    DidDocumentData deactivateDid(String did);

    /**
     * Lists DID documents owned by a tenant.
     *
     * @param tenantId the tenant
     * @return the tenant's DID documents
     */
    List<DidDocumentData> listDids(String tenantId);
}
