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

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OpenDID gateway chain anchoring (FR-ID5): EVM ABI correctness
 * (selectors against a known Ethereum constant), calldata layout, and
 * the DID anchor → resolve round trip through the anchor contract
 * semantics. The simulated connection implements exactly the contract
 * the EVM adapter encodes for; an RPC connection against a deployed
 * testnet contract is the same path with different wiring.
 */
class OpenDidGatewayTest {

    private static final String CONTRACT = "0x5FbDB2315678afecb367f032d93F642f64180aa3";

    @Test
    void keccakSelectorsMatchEthereumConstants() {
        // The keccak-256/selector implementation must reproduce the
        // canonical Ethereum selector for transfer(address,uint256)
        assertEquals("0xa9059cbb", EvmAbi.selector("transfer(address,uint256)"),
                "selector implementation must match the known Ethereum constant");
        // and therefore the anchor-contract selectors are the canonical
        // ones for their signatures
        assertEquals(10, EvmAbi.ANCHOR_SELECTOR.length());
        assertEquals(10, EvmAbi.RESOLVE_SELECTOR.length());
    }

    @Test
    void anchorCalldataLayoutIsCanonical() {
        String didWord = "0x" + "ab".repeat(32);
        String docWord = "0x" + "cd".repeat(32);
        String calldata = EvmAbi.encodeAnchor(didWord, docWord);
        assertEquals(EvmAbi.ANCHOR_SELECTOR + "ab".repeat(32) + "cd".repeat(32), calldata);

        String resolve = EvmAbi.encodeResolve(didWord);
        assertEquals(EvmAbi.RESOLVE_SELECTOR + "ab".repeat(32), resolve);
    }

    @Test
    void didAnchorResolveRoundTrip() throws Exception {
        EvmChainAdapter adapter = new EvmChainAdapter(new EvmChainAdapter.SimulatedConnection(), CONTRACT);
        String did = "did:web:anchored.example.hkt:agent-1";
        String documentJson = "{\"id\":\"did:web:anchored.example.hkt:agent-1\",\"@context\":["
                + "\"https://www.w3.org/ns/did/v1\"]}";
        String documentHash = "0x" + EvmAbi.hex(
                MessageDigest.getInstance("SHA-256").digest(documentJson.getBytes(StandardCharsets.UTF_8)));

        // Not anchored yet
        assertNull(adapter.resolve(did));

        // Anchor → resolve round trip through the contract semantics
        adapter.anchor(did, documentHash, "0xController");
        DidAnchor resolved = adapter.resolve(did);
        assertNotNull(resolved);
        assertEquals(did, resolved.getDid());
        assertEquals(documentHash, resolved.getDocumentHash());
        assertTrue(resolved.getTimestamp() > 0);
        assertTrue(resolved.getController().startsWith("0x"));
        assertEquals("evm", adapter.chainName());

        // Re-anchoring with a rotated document resolves to the latest
        String rotatedHash = "0x" + "11".repeat(32);
        adapter.anchor(did, rotatedHash, "0xController");
        assertEquals(rotatedHash, adapter.resolve(did).getDocumentHash());
    }

    @Test
    void contractRejectsUnknownSelectors() {
        EvmChainAdapter.SimulatedConnection connection = new EvmChainAdapter.SimulatedConnection();
        assertThrows(IllegalStateException.class,
                () -> connection.submit(CONTRACT, "0xdeadbeef" + "00".repeat(64)));
        assertThrows(IllegalStateException.class,
                () -> connection.call(CONTRACT, "0xdeadbeef" + "00".repeat(32)));
        assertThrows(IllegalStateException.class,
                () -> connection.submit(null, EvmAbi.encodeAnchor("0x1", "0x2")));
    }

    @Test
    void plannedChainsAreExplicitStubs() {
        assertThrows(UnsupportedOperationException.class,
                () -> new PlannedChainAdapters.TronChainAdapter().anchor("did:x:y", "0x1", "0xc"));
        assertThrows(UnsupportedOperationException.class,
                () -> new PlannedChainAdapters.SolanaChainAdapter().resolve("did:x:y"));
        assertThrows(UnsupportedOperationException.class,
                () -> new PlannedChainAdapters.AptosChainAdapter().resolve("did:x:y"));
        assertEquals("tron", new PlannedChainAdapters.TronChainAdapter().chainName());
        assertEquals("solana", new PlannedChainAdapters.SolanaChainAdapter().chainName());
        assertEquals("aptos", new PlannedChainAdapters.AptosChainAdapter().chainName());
    }
}
