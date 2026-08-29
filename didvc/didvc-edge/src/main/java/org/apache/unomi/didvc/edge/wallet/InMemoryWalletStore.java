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

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory wallet store: per-wallet credential lists and holder keys in
 * {@link ConcurrentHashMap}s. Credentials are lost on restart — the
 * wallet is a backend for the subscriber app, which persists its own
 * copy; this store is the single-instance/dev implementation and the
 * swap point for a Redis/JDBC-backed store.
 */
@Component
public class InMemoryWalletStore implements WalletCredentialStore {

    private final Map<String, Map<String, StoredCredential>> credentials = new ConcurrentHashMap<>();
    private final Map<String, OctetKeyPair> holderKeys = new ConcurrentHashMap<>();

    @Override
    public List<StoredCredential> list(String walletId) {
        Map<String, StoredCredential> wallet = credentials.get(walletId);
        return wallet == null ? List.of() : new ArrayList<>(wallet.values());
    }

    @Override
    public Optional<StoredCredential> get(String walletId, String credentialId) {
        Map<String, StoredCredential> wallet = credentials.get(walletId);
        return wallet == null ? Optional.empty() : Optional.ofNullable(wallet.get(credentialId));
    }

    @Override
    public StoredCredential save(StoredCredential credential) {
        if (credential.getCredentialId() == null || credential.getCredentialId().isEmpty()) {
            credential.setCredentialId("wallet-cred-" + UUID.randomUUID());
        }
        credentials.computeIfAbsent(credential.getWalletId(), w -> new ConcurrentHashMap<>())
                .put(credential.getCredentialId(), credential);
        return credential;
    }

    @Override
    public boolean delete(String walletId, String credentialId) {
        Map<String, StoredCredential> wallet = credentials.get(walletId);
        return wallet != null && wallet.remove(credentialId) != null;
    }

    @Override
    public Optional<OctetKeyPair> getHolderKey(String walletId) {
        return Optional.ofNullable(holderKeys.get(walletId));
    }

    @Override
    public OctetKeyPair getOrCreateHolderKey(String walletId) {
        return holderKeys.computeIfAbsent(walletId, w -> {
            try {
                return new OctetKeyPairGenerator(Curve.Ed25519).generate();
            } catch (Exception e) {
                throw new IllegalStateException("Holder key generation failed for wallet " + walletId, e);
            }
        });
    }
}
