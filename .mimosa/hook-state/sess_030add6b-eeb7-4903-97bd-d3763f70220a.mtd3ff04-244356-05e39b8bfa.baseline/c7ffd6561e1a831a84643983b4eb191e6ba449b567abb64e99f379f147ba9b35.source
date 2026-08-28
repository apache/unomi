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

import org.apache.unomi.didvc.api.CredentialIssueRequest;
import org.apache.unomi.didvc.api.items.CredentialRecord;

/**
 * Credential issuance orchestration: schema validation, consent-gated claim
 * minimization, status-index allocation, formatting and persistence. The
 * event-driven entry point is the {@code issueCredential} action executor;
 * this service is also called directly by the credential edge over REST.
 */
public interface IssuanceService {

    /**
     * Validates, formats and persists a credential.
     *
     * @param request the issue request
     * @return the persisted credential record including the serialized credential
     */
    CredentialRecord issueCredential(CredentialIssueRequest request);

    /**
     * Loads a credential record.
     *
     * @param recordId the record identifier
     * @return the record, or null if unknown
     */
    CredentialRecord getCredential(String recordId);

    /**
     * Revokes a credential: flips its status-list bit and marks the record.
     * Takes effect at the next verification.
     *
     * @param recordId the record identifier
     * @return the updated record, or null if unknown
     */
    CredentialRecord revokeCredential(String recordId);

    /**
     * Re-issues an existing credential with holder key binding
     * ({@code cnf.jwk}), using the claims stored at original issuance.
     * This is the OID4VCI key-binding step: the credential is first issued
     * bearer at offer time, then rebound to the wallet's key carried in the
     * credential request proof.
     *
     * @param recordId          the record identifier
     * @param holderPublicJwkJson the holder's public JWK
     * @return the updated record, or null if unknown
     */
    CredentialRecord rebindCredential(String recordId, String holderPublicJwkJson);

    /**
     * Checks whether a credential is revoked.
     *
     * @param recordId the record identifier
     * @return true when the status bit is set
     */
    boolean isCredentialRevoked(String recordId);
}
