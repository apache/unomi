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

import org.apache.unomi.didvc.api.items.KeyDescriptor;

import java.util.List;

/**
 * Issuer signing-key lifecycle: generation, lookup, rotation windows, and
 * JWS signing/verification. Only public key material is persisted; private
 * keys are held by the key-material provider (HSM/KMS) and addressed by kid.
 */
public interface IssuerKeyService {

    /**
     * Generates a new signing key for an issuer.
     *
     * @param tenantId  the tenant owning the key
     * @param issuerDid the DID the key signs for
     * @param algorithm {@code EdDSA} (Ed25519) or {@code ES256} (P-256)
     * @return the key descriptor (public material only)
     */
    KeyDescriptor generateKey(String tenantId, String issuerDid, String algorithm);

    /**
     * Loads a key descriptor by kid.
     *
     * @param kid the key identifier
     * @return the descriptor, or null if unknown
     */
    KeyDescriptor getKey(String kid);

    /**
     * Lists a tenant's key descriptors.
     *
     * @param tenantId the tenant
     * @return the tenant's key descriptors
     */
    List<KeyDescriptor> getKeys(String tenantId);

    /**
     * Deletes a key (descriptor and, where the provider supports it, the key
     * material).
     *
     * @param kid the key identifier
     */
    void deleteKey(String kid);

    /**
     * Signs a payload and returns the compact JWS.
     *
     * @param kid         the key identifier
     * @param payloadJson the payload to sign
     * @return the compact JWS
     */
    String sign(String kid, String payloadJson);

    /**
     * Signs a payload with an explicit JOSE type header (e.g.
     * {@code vc+sd-jwt}) and returns the compact JWS.
     *
     * @param kid         the key identifier
     * @param payloadJson the payload to sign
     * @param typ         the JOSE type header value; null for none
     * @return the compact JWS
     */
    String signTyped(String kid, String payloadJson, String typ);

    /**
     * Verifies a compact JWS signature against a kid's public key.
     *
     * @param kid        the key identifier
     * @param jwsCompact the compact JWS
     * @return true when the signature validates
     */
    boolean verify(String kid, String jwsCompact);
}
