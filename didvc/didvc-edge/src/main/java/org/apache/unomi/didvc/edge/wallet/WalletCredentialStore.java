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

package org.apache.unomi.didvc.edge.wallet;

import com.nimbusds.jose.jwk.OctetKeyPair;

import java.util.List;
import java.util.Optional;

/**
 * Credential and holder-key storage for the wallet backend. The wallet
 * holds its own credentials and its holder signing key; the in-memory
 * implementation is the single-instance/dev store and the swap point for
 * a Redis/JDBC-backed store in production.
 */
public interface WalletCredentialStore {

    /**
     * Lists a wallet's held credentials.
     *
     * @param walletId the wallet (subscriber-app instance)
     * @return the credentials, oldest first
     */
    List<StoredCredential> list(String walletId);

    /**
     * Loads one held credential.
     *
     * @param walletId     the wallet
     * @param credentialId the credential id
     * @return the credential, or empty when unknown
     */
    Optional<StoredCredential> get(String walletId, String credentialId);

    /**
     * Stores a credential; assigns a credential id when absent.
     *
     * @param credential the credential
     * @return the stored credential
     */
    StoredCredential save(StoredCredential credential);

    /**
     * Removes a held credential.
     *
     * @param walletId     the wallet
     * @param credentialId the credential id
     * @return true when a credential was removed
     */
    boolean delete(String walletId, String credentialId);

    /**
     * The wallet's holder signing key (private), or empty when the wallet
     * has not redeemed an offer yet.
     *
     * @param walletId the wallet
     * @return the holder key
     */
    Optional<OctetKeyPair> getHolderKey(String walletId);

    /**
     * Returns the wallet's holder signing key, generating an Ed25519 key
     * on first use.
     *
     * @param walletId the wallet
     * @return the holder key
     */
    OctetKeyPair getOrCreateHolderKey(String walletId);
}
