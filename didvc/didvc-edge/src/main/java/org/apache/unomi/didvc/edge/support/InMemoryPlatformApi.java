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

package org.apache.unomi.didvc.edge.support;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.Ed25519Signer;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator;
import org.apache.unomi.didvc.edge.platform.PlatformApi;
import org.apache.unomi.didvc.sdjwt.SdJwtBuilder;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory platform fake with real SD-JWT issuance: exercises the full
 * credential lifecycle against the edge without a Unomi runtime.
 */
public class InMemoryPlatformApi implements PlatformApi {

    public static final String ISSUER_DID = "did:web:id.example.hkt";
    public static final String STATUS_LIST_ID = "urn:didvc:status:revocation:fake-list-1";

    private final OctetKeyPair issuerKey;
    private final Map<String, IssuedCredential> credentials = new ConcurrentHashMap<>();
    private final Map<String, IssueRequest> issueRequests = new ConcurrentHashMap<>();
    private final Set<String> trustedPairs = ConcurrentHashMap.newKeySet();
    private final Set<String> revokedStatuses = ConcurrentHashMap.newKeySet();
    private final AtomicInteger statusCounter = new AtomicInteger();

    public InMemoryPlatformApi() {
        try {
            this.issuerKey = new OctetKeyPairGenerator(Curve.Ed25519).generate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public String getIssuerKid() {
        try {
            return issuerKey.computeThumbprint().toString();
        } catch (com.nimbusds.jose.JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    public OctetKeyPair getIssuerKey() {
        return issuerKey;
    }

    public void trust(String verifierTenantId, String issuerDid, String vct) {
        trustedPairs.add(verifierTenantId + "|" + issuerDid + "|" + vct);
    }

    public void untrustAll() {
        trustedPairs.clear();
    }

    public Map<String, IssuedCredential> getCredentialsSnapshot() {
        return credentials;
    }

    public void markRevoked(String recordId) {
        IssuedCredential issued = credentials.get(recordId);
        if (issued != null && issued.getStatusListIndex() != null) {
            revokedStatuses.add(statusKey(STATUS_LIST_ID, issued.getStatusListIndex()));
            issued.setRevoked(true);
        }
    }

    @Override
    public IssuedCredential issueCredential(String tenantId, IssueRequest request) {
        return doIssue("cred-" + System.nanoTime(), request, null, null);
    }

    @Override
    public IssuedCredential rebindCredential(String tenantId, String recordId, String holderPublicJwkJson) {
        IssuedCredential existing = credentials.get(recordId);
        IssueRequest original = issueRequests.get(recordId);
        if (existing == null || original == null) {
            return null;
        }
        IssueRequest rebound = new IssueRequest();
        rebound.setTenantId(original.getTenantId());
        rebound.setSchemaId(original.getSchemaId());
        rebound.setSubjectId(original.getSubjectId());
        rebound.setKid(original.getKid());
        rebound.setHolderPublicJwkJson(holderPublicJwkJson);
        rebound.setAlwaysDisclosedClaims(original.getAlwaysDisclosedClaims());
        rebound.setSelectivelyDisclosedClaims(original.getSelectivelyDisclosedClaims());
        return doIssue(recordId, rebound, existing.getStatusListIndex(), existing.getStatusListId());
    }

    private IssuedCredential doIssue(String recordId, IssueRequest request,
                                     Integer statusIndex, String statusListId) {
        try {
            SdJwtBuilder.CredentialPayload payload = new SdJwtBuilder.CredentialPayload();
            payload.setVct(vctForSchema(request.getSchemaId()));
            payload.setIss(ISSUER_DID);
            payload.setSub(request.getSubjectId());
            payload.setIssuedAt(new Date());
            payload.setExpiresAt(new Date(System.currentTimeMillis() + 365L * 24 * 3600 * 1000));
            int index = statusIndex != null ? statusIndex : statusCounter.getAndIncrement();
            Map<String, Object> statusList = new LinkedHashMap<>();
            statusList.put("idx", index);
            statusList.put("uri", statusListId == null ? STATUS_LIST_ID : statusListId);
            Map<String, Object> status = new LinkedHashMap<>();
            status.put("status_list", statusList);
            payload.setStatus(status);
            if (request.getHolderPublicJwkJson() != null) {
                JWK holderJwk = JWK.parse(request.getHolderPublicJwkJson());
                payload.setCnf(SdJwtBuilder.cnfForJwk(holderJwk.toPublicJWK()));
            }
            payload.getAlwaysDisclosed().putAll(request.getAlwaysDisclosedClaims() == null
                    ? Map.of() : request.getAlwaysDisclosedClaims());
            payload.getSelectivelyDisclosed().putAll(request.getSelectivelyDisclosedClaims() == null
                    ? Map.of() : request.getSelectivelyDisclosedClaims());
            String credential = new SdJwtBuilder().build(payload, new Ed25519Signer(issuerKey),
                    JWSAlgorithm.EdDSA, getIssuerKid());

            IssuedCredential issued = new IssuedCredential();
            issued.setItemId(recordId);
            issued.setSchemaId(request.getSchemaId());
            issued.setSubjectId(request.getSubjectId());
            issued.setFormat("vc+sd-jwt");
            issued.setCredential(credential);
            issued.setStatusListIndex(index);
            issued.setStatusListId(statusListId == null ? STATUS_LIST_ID : statusListId);
            issued.setExpiresAt(payload.getExpiresAt().getTime());
            credentials.put(issued.getItemId(), issued);
            issueRequests.put(issued.getItemId(), request);
            return issued;
        } catch (Exception e) {
            throw new IllegalStateException("fake issuance failed", e);
        }
    }

    @Override
    public IssuedCredential getCredential(String tenantId, String recordId) {
        return credentials.get(recordId);
    }

    @Override
    public boolean isStatusRevoked(String tenantId, String statusListId, int index) {
        return revokedStatuses.contains(statusKey(statusListId, index));
    }

    @Override
    public boolean isTrusted(String tenantId, String issuerDid, String vct) {
        return trustedPairs.contains(tenantId + "|" + issuerDid + "|" + vct);
    }

    @Override
    public JWK resolveIssuerKey(String issuerDid, String kid) {
        if (ISSUER_DID.equals(issuerDid) && getIssuerKid().equals(kid)) {
            return issuerKey.toPublicJWK();
        }
        return null;
    }

    private String vctForSchema(String schemaId) {
        return "hkt_kyc_v1";
    }

    private String statusKey(String statusListId, int index) {
        return statusListId + "|" + index;
    }
}
