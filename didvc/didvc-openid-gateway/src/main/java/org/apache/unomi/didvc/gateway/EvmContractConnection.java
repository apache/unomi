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
 * The connection to a deployed anchor contract on an EVM network:
 * submit sends a transaction (returns its hash), call performs a
 * read-only eth_call (returns the hex result). Implementations range
 * from an RPC-backed connection (testnet/mainnet, wired from
 * environment configuration) to the in-memory contract simulation used
 * for demos and tests.
 */
public interface EvmContractConnection {

    /**
     * Submits a transaction to the contract.
     *
     * @param contractAddress the anchor contract address (0x-hex)
     * @param calldata        0x-prefixed calldata
     * @return the transaction hash
     * @throws IllegalStateException on submission failure
     */
    String submit(String contractAddress, String calldata);

    /**
     * Performs a read-only call against the contract.
     *
     * @param contractAddress the anchor contract address (0x-hex)
     * @param calldata        0x-prefixed calldata
     * @return the 0x-prefixed hex return data (may be empty)
     * @throws IllegalStateException on call failure
     */
    String call(String contractAddress, String calldata);
}
