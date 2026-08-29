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

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wallet backend API for the HKT subscriber app (FR-P3): credential
 * storage listing, credential-offer redemption and presentation
 * building. Per-wallet holder keys are generated at first offer
 * redemption. Production deployments front this API with app-session
 * authentication.
 */
@RestController
@RequestMapping("/wallet")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    /**
     * Redeems a credential offer and holds the delivered credential.
     */
    @PostMapping("/{walletId}/offers")
    public ResponseEntity<StoredCredential> redeemOffer(@PathVariable("walletId") String walletId,
                                                        @RequestBody RedeemRequest request) {
        if (request == null || request.getOffer() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "offer is required");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(walletService.redeemOffer(walletId, request.getOffer()));
    }

    /**
     * Lists the wallet's held credentials (credential metadata without
     * holder key material).
     */
    @GetMapping("/{walletId}/credentials")
    public List<Map<String, Object>> listCredentials(@PathVariable("walletId") String walletId) {
        return walletService.listCredentials(walletId).stream().map(WalletController::summary).toList();
    }

    /**
     * Loads one held credential (full credential string included — the
     * wallet owns it).
     */
    @GetMapping("/{walletId}/credentials/{credentialId}")
    public StoredCredential getCredential(@PathVariable("walletId") String walletId,
                                          @PathVariable("credentialId") String credentialId) {
        return walletService.getCredential(walletId, credentialId);
    }

    @DeleteMapping("/{walletId}/credentials/{credentialId}")
    public ResponseEntity<Void> deleteCredential(@PathVariable("walletId") String walletId,
                                                 @PathVariable("credentialId") String credentialId) {
        walletService.deleteCredential(walletId, credentialId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Builds a key-bound presentation for an authorization request and
     * submits it to the verifier's response endpoint; returns the
     * verification result.
     */
    @PostMapping("/{walletId}/presentations")
    public Map<String, Object> present(@PathVariable("walletId") String walletId,
                                       @RequestBody PresentRequest request) {
        if (request == null || request.getRequestUri() == null || request.getRequestUri().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "requestUri is required");
        }
        return walletService.present(walletId, request.getRequestUri());
    }

    /**
     * The wallet's public holder key (JWK), for issuers and verifiers to
     * verify the wallet's proofs and key bindings.
     */
    @GetMapping("/{walletId}/jwks")
    public Map<String, Object> holderJwks(@PathVariable("walletId") String walletId) {
        return walletService.holderJwks(walletId);
    }

    /** Offer-redemption request body. */
    public static class RedeemRequest {
        private JsonNode offer;

        public JsonNode getOffer() {
            return offer;
        }

        public void setOffer(JsonNode offer) {
            this.offer = offer;
        }
    }

    /** Presentation request body. */
    public static class PresentRequest {
        private String requestUri;

        public String getRequestUri() {
            return requestUri;
        }

        public void setRequestUri(String requestUri) {
            this.requestUri = requestUri;
        }
    }

    /**
     * Listing projection: the credential string stays server-side; the
     * app lists what it holds by metadata.
     */
    private static Map<String, Object> summary(StoredCredential credential) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("credentialId", credential.getCredentialId());
        summary.put("format", credential.getFormat());
        summary.put("vct", credential.getVct());
        summary.put("issuerDid", credential.getIssuerDid());
        summary.put("subjectId", credential.getSubjectId());
        summary.put("issuedAt", credential.getIssuedAt());
        summary.put("expiresAt", credential.getExpiresAt());
        return summary;
    }
}
