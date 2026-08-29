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

package org.apache.unomi.didvc.services.impl;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;

/**
 * Key-material provider seam behind {@link IssuerKeyService} (FR-G2):
 * owns the private key material and performs signing. The default
 * in-process provider keeps keys in memory; the PKCS#11 provider signs
 * inside an HSM/token so private keys never enter application memory —
 * only public JWKs are persisted either way.
 */
public interface KeyMaterialProvider {

    /**
     * Registers a newly generated key (public material is persisted by
     * the caller; the provider owns the private half).
     *
     * @param kid       the key identifier
     * @param jwk       the full (private) JWK
     * @param algorithm the signing algorithm
     */
    void register(String kid, JWK jwk, JWSAlgorithm algorithm);

    /**
     * Signs a payload and returns the compact JWS.
     *
     * @param kid         the key identifier
     * @param payloadJson the payload to sign
     * @param typ         the JOSE type header value; null for none
     * @return the compact JWS
     * @throws IllegalStateException when the key is unknown or signing fails
     */
    String sign(String kid, String payloadJson, String typ);

    /**
     * Drops the key material for a kid.
     *
     * @param kid the key identifier
     */
    void remove(String kid);
}
