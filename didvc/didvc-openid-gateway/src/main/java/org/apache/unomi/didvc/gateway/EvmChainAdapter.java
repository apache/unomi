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

import org.bouncycastle.crypto.digests.KeccakDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * EVM chain adapter: anchors DID documents through the anchor contract
 * ({@code anchor(bytes32,bytes32)}) and resolves them
 * ({@code resolve(bytes32)}) over an {@link EvmContractConnection}.
 * With an RPC connection configured from environment (RPC URL,
 * contract address, submitter credentials — never committed), this is
 * the testnet/mainnet path; with the in-memory connection it is the
 * local/demo path.
 */
public class EvmChainAdapter implements ChainAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(EvmChainAdapter.class);

    private final EvmContractConnection connection;
    private final String contractAddress;

    /**
     * Creates the adapter.
     *
     * @param connection       the contract connection (RPC or simulation)
     * @param contractAddress  the deployed anchor contract address
     */
    public EvmChainAdapter(EvmContractConnection connection, String contractAddress) {
        this.connection = connection;
        this.contractAddress = contractAddress;
    }

    @Override
    public String chainName() {
        return "evm";
    }

    @Override
    public DidAnchor anchor(String did, String documentHash, String controller) {
        String didWord = didWord(did);
        String tx = connection.submit(contractAddress, EvmAbi.encodeAnchor(didWord, documentHash));
        LOGGER.info("Anchored {} (docHash {}) on EVM contract {} via tx {}", did, documentHash,
                contractAddress, tx);
        DidAnchor resolved = resolve(did);
        return resolved != null ? resolved : new DidAnchor(did, documentHash, controller,
                System.currentTimeMillis() / 1000);
    }

    @Override
    public DidAnchor resolve(String did) {
        String result = connection.call(contractAddress, EvmAbi.encodeResolve(didWord(did)));
        if (result == null || EvmAbi.strip0x(result).length() < 64 * 3) {
            return null;
        }
        String[] decoded = EvmAbi.decodeResolveResult(result);
        if (decoded[0].replaceAll("^0x0+", "0x0").equals("0x0")
                || EvmAbi.strip0x(decoded[0]).chars().allMatch(c -> c == '0')) {
            return null; // zero docHash = never anchored
        }
        return new DidAnchor(did, decoded[0], decoded[2], Long.parseLong(decoded[1]));
    }

    /**
     * The contract keys anchors by keccak-256(did) — the word form used
     * in calldata.
     *
     * @param did the DID
     * @return 0x-prefixed 32-byte hash
     */
    static String didWord(String did) {
        KeccakDigest keccak = new KeccakDigest(256);
        byte[] input = did.getBytes(StandardCharsets.US_ASCII);
        keccak.update(input, 0, input.length);
        byte[] digest = new byte[32];
        keccak.doFinal(digest, 0);
        return "0x" + EvmAbi.hex(digest);
    }

    /**
     * In-memory EVM connection simulating the anchor contract's storage
     * semantics (demo/local path; the RPC connection replaces it in
     * testnet deployments).
     */
    public static class SimulatedConnection implements EvmContractConnection {

        private final Map<String, String> anchoredDocHashes = new LinkedHashMap<>();
        private final Map<String, Long> anchorsTimestamps = new LinkedHashMap<>();
        private final Map<String, String> controllers = new LinkedHashMap<>();
        private long txCounter;

        @Override
        public String submit(String contractAddress, String calldata) {
            requireContract(contractAddress);
            if (!calldata.startsWith(EvmAbi.ANCHOR_SELECTOR)) {
                throw new IllegalStateException("contract rejects unknown selector: " + calldata);
            }
            String body = EvmAbi.strip0x(calldata).substring(8);
            String didHash = "0x" + body.substring(0, 64);
            String docHash = "0x" + body.substring(64, 128);
            long timestamp = System.currentTimeMillis() / 1000;
            anchoredDocHashes.put(didHash, docHash);
            anchorsTimestamps.put(didHash, timestamp);
            // controller is msg.sender in the real contract; the
            // simulation records a placeholder sender
            controllers.put(didHash, simulatedSender());
            return "0x" + Long.toHexString(++txCounter);
        }

        @Override
        public String call(String contractAddress, String calldata) {
            requireContract(contractAddress);
            if (!calldata.startsWith(EvmAbi.RESOLVE_SELECTOR)) {
                throw new IllegalStateException("contract rejects unknown selector: " + calldata);
            }
            String didHash = "0x" + EvmAbi.strip0x(calldata).substring(8, 8 + 64);
            String docHash = anchoredDocHashes.get(didHash);
            if (docHash == null) {
                return "0x" + "00".repeat(96);
            }
            return "0x" + EvmAbi.word(docHash)
                    + EvmAbi.word("0x" + Long.toHexString(anchorsTimestamps.get(didHash)))
                    + EvmAbi.word(controllers.get(didHash));
        }

        private void requireContract(String contractAddress) {
            if (contractAddress == null || !contractAddress.startsWith("0x")) {
                throw new IllegalStateException("no anchor contract configured");
            }
        }

        private static String simulatedSender() {
            // A 20-byte hex sender, as an EVM address is
            return "0x" + "0000000000000000000000000000000000000001";
        }
    }
}
