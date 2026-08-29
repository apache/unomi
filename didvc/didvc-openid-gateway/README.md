<!--

    Licensed to the Apache Software Foundation (ASF) under one or more
    contributor license agreements.  See the NOTICE file distributed with
    this work for additional information regarding copyright ownership.
    The ASF licenses this file to You under the Apache License, Version 2.0
    (the "License"); you may not use this file except in compliance with
    the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
-->

# OpenDID Gateway (unomi-did-vc-openid-gateway)

OpenDID Web2↔Web3 gateway (FR-ID5, phase 7): anchors DID documents to
blockchains through the OpenDID-Labs `gateway-java` pattern — one
`ChainAdapter` per network, oracle/registry contract per network — and
resolves the latest anchor back.

## Anchor contract (EVM first)

The EVM adapter targets a minimal registry contract:

```solidity
contract DidAnchorRegistry {
    function anchor(bytes32 didHash, bytes32 docHash) external;
    function resolve(bytes32 didHash) external view
        returns (bytes32 docHash, uint64 timestamp, address controller);
}
```

`EvmAbi` hand-encodes the two calls (selectors are keccak-256 of the
canonical signatures; the implementation reproduces the canonical
Ethereum selector for `transfer(address,uint256)` as a known-constant
check). Anchors are keyed by `keccak256(did)`; re-anchoring a rotated
document resolves to the latest hash.

## Chains

| Adapter | Status | Notes |
|---|---|---|
| `EvmChainAdapter` | implemented | Calldata for the contract above over any `EvmContractConnection` |
| — RPC connection | ops wiring | Point at a testnet RPC + deployed contract from environment (URL, contract address, submitter key from a secret service — never committed); anchor + resolve then run on-chain |
| — `SimulatedConnection` | built-in | In-memory contract semantics for demos, local development and the module tests |
| `TronChainAdapter` / `SolanaChainAdapter` / `AptosChainAdapter` | stubs | Explicit `UnsupportedOperationException` until each network's oracle integration lands |

## Testnet deployment (acceptance path)

1. Deploy `DidAnchorRegistry` to the target testnet; note the contract
   address.
2. Implement/wire an `EvmContractConnection` over the testnet JSON-RPC
   (submit = signed transaction, call = `eth_call`), configured from
   environment.
3. Run the same anchor/resolve round trip exercised by
   `OpenDidGatewayTest` — the adapter path is identical; only the
   connection differs.
