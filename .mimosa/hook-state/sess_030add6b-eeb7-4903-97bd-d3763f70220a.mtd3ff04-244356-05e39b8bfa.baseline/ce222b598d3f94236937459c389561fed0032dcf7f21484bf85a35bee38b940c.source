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

import java.util.Base64;
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
    private final Map<String, JWK> externalIssuerKeys = new ConcurrentHashMap<>();
    private final Set<String> trustedPairs = ConcurrentHashMap.newKeySet();
    private final Set<String> revokedStatuses = ConcurrentHashMap.newKeySet();
    private final AtomicInteger statusCounter = new AtomicInteger();
    /**
     * When set (e.g. {@code https://edge/hkt/status-lists/{id}}), credentials
     * carry a fetchable HTTP status-list URI instead of the bare URN.
     */
    private String statusListUriTemplate;

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

    /**
     * Registers an external credential issuer whose keys this fake can
     * resolve — used to let the demo verifier accept credentials issued by
     * an interop counterpart (e.g. a conformance-suite wallet).
     *
     * @param issuerDid the issuer identifier (the credential {@code iss})
     * @param jwk       the issuer's public JWK
     */
    public void addExternalIssuerKey(String issuerDid, JWK jwk) {
        externalIssuerKeys.put(issuerDid, jwk);
    }

    /**
     * The demo platform's key doubles as the verifier's request-object
     * signing key for every relying tenant (demo deployments have a single
     * trust domain).
     */
    @Override
    public JWK getVerifierSigningKey(String tenantId) {
        return issuerKey;
    }

    /**
     * Sets the status-list URI template ({@code {tenant}} and {@code {id}}
     * placeholders) so issued credentials reference the edge's fetchable
     * status-list endpoint rather than an opaque URN.
     *
     * @param statusListUriTemplate the template, or null for the URN form
     */
    public void setStatusListUriTemplate(String statusListUriTemplate) {
        this.statusListUriTemplate = statusListUriTemplate;
    }

    @Override
    public String getDefaultIssuerKid() {
        return getIssuerKid();
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
            statusList.put("uri", statusListUri(request.getTenantId(), statusListId));
            Map<String, Object> status = new LinkedHashMap<>();
            status.put("status_list", statusList);
            payload.setStatus(status);
            if (request.getHolderPublicJwkJson() != null) {
                JWK holderJwk = JWK.parse(request.getHolderPublicJwkJson());
                payload.setCnf(SdJwtBuilder.cnfForJwk(holderJwk.toPublicJWK()));
            }
            if (request.getAlwaysDisclosedClaims() == null || request.getAlwaysDisclosedClaims().isEmpty()) {
                payload.getAlwaysDisclosed().put("kycLevel", "REMOTE_FULL");
                payload.getAlwaysDisclosed().put("sanctionsClear", true);
            } else {
                payload.getAlwaysDisclosed().putAll(request.getAlwaysDisclosedClaims());
            }
            if (request.getSelectivelyDisclosedClaims() != null) {
                payload.getSelectivelyDisclosed().putAll(request.getSelectivelyDisclosedClaims());
            }
            String credential = new SdJwtBuilder().build(payload, new Ed25519Signer(issuerKey),
                    JWSAlgorithm.EdDSA, getIssuerKid());

            IssuedCredential issued = new IssuedCredential();
            issued.setItemId(recordId);
            issued.setSchemaId(request.getSchemaId());
            issued.setSubjectId(request.getSubjectId());
            issued.setFormat("dc+sd-jwt");
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
        JWK external = externalIssuerKeys.get(issuerDid);
        if (external != null && (kid == null || kid.equals(keyIdOf(external)))) {
            return external;
        }
        return null;
    }

    private static String keyIdOf(JWK jwk) {
        try {
            return jwk.computeThumbprint().toString();
        } catch (Exception e) {
            return null;
        }
    }

    private String vctForSchema(String schemaId) {
        return "hkt_kyc_v1";
    }

    private String statusListUri(String tenantId, String statusListId) {
        String id = statusListId == null ? STATUS_LIST_ID : statusListId;
        if (statusListUriTemplate == null) {
            return id;
        }
        return statusListUriTemplate
                .replace("{tenant}", tenantId == null ? "" : tenantId)
                .replace("{id}", id);
    }

    /**
     * Builds the OAuth Token Status List JWT for the demo list: a
     * {@code statuslist+jwt}-typed JWS with the public signing key embedded
     * in the header (the conformance wallet verifies via the embedded jwk),
     * claims {@code sub} (the list URI), {@code iat}/{@code exp}/{@code ttl}
     * and {@code status_list.bits}/{@code status_list.lst}, where
     * {@code lst} is the zlib-compressed, base64url-encoded bitstring with
     * bit 0 = valid and bit 1 = revoked at each allocated index.
     */
    @Override
    public String getStatusListToken(String tenantId, String statusListId) {
        try {
            int entries = Math.max(131072, (statusCounter.get() + 8));
            byte[] bits = new byte[(entries + 7) / 8];
            for (String key : revokedStatuses) {
                String[] parts = key.split("\\|", 2);
                if (parts.length == 2 && parts[0].equals(statusListId)) {
                    int index = Integer.parseInt(parts[1]);
                    if (index >= 0 && index < entries) {
                        // OTSL packs entries LSB-first (suite TokenStatusList decoder)
                        bits[index / 8] |= (byte) (1 << (index % 8));
                    }
                }
            }
            String lst = Base64.getUrlEncoder().withoutPadding().encodeToString(zlib(bits));

            Map<String, Object> statusListClaim = new LinkedHashMap<>();
            statusListClaim.put("bits", 1);
            statusListClaim.put("lst", lst);
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("sub", statusListUri(tenantId, statusListId));
            claims.put("iat", System.currentTimeMillis() / 1000);
            claims.put("exp", System.currentTimeMillis() / 1000 + 600);
            claims.put("ttl", 720);
            claims.put("status_list", statusListClaim);

            com.nimbusds.jose.JWSHeader header = new com.nimbusds.jose.JWSHeader.Builder(JWSAlgorithm.EdDSA)
                    .type(new com.nimbusds.jose.JOSEObjectType("statuslist+jwt"))
                    .jwk(issuerKey.toPublicJWK())
                    .build();
            com.nimbusds.jose.JWSObject jws = new com.nimbusds.jose.JWSObject(header,
                    new com.nimbusds.jose.Payload(toJsonBytes(claims)));
            jws.sign(new Ed25519Signer(issuerKey));
            return jws.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("failed to build status list token", e);
        }
    }

    private static byte[] toJsonBytes(Map<String, Object> claims) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(claims);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] zlib(byte[] data) {
        java.util.zip.Deflater deflater = new java.util.zip.Deflater(java.util.zip.Deflater.BEST_COMPRESSION, false);
        deflater.setInput(data);
        deflater.finish();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        while (!deflater.finished()) {
            int n = deflater.deflate(buf);
            out.write(buf, 0, n);
        }
        deflater.end();
        return out.toByteArray();
    }

    private String statusKey(String statusListId, int index) {
        return statusListId + "|" + index;
    }
}
