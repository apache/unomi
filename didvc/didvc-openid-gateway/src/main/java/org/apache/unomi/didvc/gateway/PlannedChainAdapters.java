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
 * Placeholder adapters for the remaining OpenDID networks (FR-ID5):
 * Tron, Solana and Aptos oracle-contract integrations follow the same
 * {@link ChainAdapter} seam; each activates with its network's contract
 * wiring.
 */
public final class PlannedChainAdapters {

    private PlannedChainAdapters() {
    }

    /** Tron adapter stub — activates with the TRON oracle contract wiring. */
    public static final class TronChainAdapter implements ChainAdapter {
        @Override
        public String chainName() {
            return "tron";
        }

        @Override
        public DidAnchor anchor(String did, String documentHash, String controller) {
            throw new UnsupportedOperationException(
                    "Tron anchoring arrives with the TRON oracle-contract integration");
        }

        @Override
        public DidAnchor resolve(String did) {
            throw new UnsupportedOperationException(
                    "Tron resolution arrives with the TRON oracle-contract integration");
        }
    }

    /** Solana adapter stub. */
    public static final class SolanaChainAdapter implements ChainAdapter {
        @Override
        public String chainName() {
            return "solana";
        }

        @Override
        public DidAnchor anchor(String did, String documentHash, String controller) {
            throw new UnsupportedOperationException(
                    "Solana anchoring arrives with the Solana program integration");
        }

        @Override
        public DidAnchor resolve(String did) {
            throw new UnsupportedOperationException(
                    "Solana resolution arrives with the Solana program integration");
        }
    }

    /** Aptos adapter stub. */
    public static final class AptosChainAdapter implements ChainAdapter {
        @Override
        public String chainName() {
            return "aptos";
        }

        @Override
        public DidAnchor anchor(String did, String documentHash, String controller) {
            throw new UnsupportedOperationException(
                    "Aptos anchoring arrives with the Aptos module integration");
        }

        @Override
        public DidAnchor resolve(String did) {
            throw new UnsupportedOperationException(
                    "Aptos resolution arrives with the Aptos module integration");
        }
    }
}
