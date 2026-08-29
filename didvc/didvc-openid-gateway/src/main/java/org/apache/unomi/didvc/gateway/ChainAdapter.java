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

package org.apache.unomi.didvc.gateway;

/**
 * A chain the OpenDID gateway can anchor DID documents to and resolve
 * them from (the OpenDID-Labs gateway-java pattern: one adapter per
 * chain, oracle contracts per network).
 */
public interface ChainAdapter {

    /**
     * The chain's name (e.g. {@code evm}, {@code tron}).
     *
     * @return the chain name
     */
    String chainName();

    /**
     * Anchors the current hash of a DID document.
     *
     * @param did         the DID the document belongs to
     * @param documentHash hex-encoded (0x-prefixed) document hash
     * @param controller  the anchoring party (address/DID)
     * @return the recorded anchor
     */
    DidAnchor anchor(String did, String documentHash, String controller);

    /**
     * Resolves the latest anchor for a DID, or null when unanchored.
     *
     * @param did the DID
     * @return the latest anchor, or null
     */
    DidAnchor resolve(String did);
}
