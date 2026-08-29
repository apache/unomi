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

package org.apache.unomi.didvc.sdjwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.jwk.JWK;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Builds SD-JWT verifiable credentials (format {@code vc+sd-jwt}): a signed
 * payload carrying the mandatory VC claims ({@code vct}, {@code iss}, time
 * claims), holder binding ({@code cnf.jwk}), credential status, and the
 * salted digests ({@code _sd}) of selectively disclosable claims, followed by
 * the disclosure strings.
 */
public class SdJwtBuilder {

    /**
     * Payload of the credential to build.
     */
    public static class CredentialPayload {
        private String vct;
        private String iss;
        private String sub;
        private Date issuedAt = new Date();
        private Date notBefore;
        private Date expiresAt;
        private Map<String, Object> status;
        private Map<String, Object> cnf;
        private final Map<String, Object> alwaysDisclosed = new LinkedHashMap<>();
        private final Map<String, Object> selectivelyDisclosed = new LinkedHashMap<>();

        public String getVct() {
            return vct;
        }

        public void setVct(String vct) {
            this.vct = vct;
        }

        public String getIss() {
            return iss;
        }

        public void setIss(String iss) {
            this.iss = iss;
        }

        public String getSub() {
            return sub;
        }

        public void setSub(String sub) {
            this.sub = sub;
        }

        public Date getIssuedAt() {
            return issuedAt;
        }

        public void setIssuedAt(Date issuedAt) {
            this.issuedAt = issuedAt;
        }

        public Date getNotBefore() {
            return notBefore;
        }

        public void setNotBefore(Date notBefore) {
            this.notBefore = notBefore;
        }

        public Date getExpiresAt() {
            return expiresAt;
        }

        public void setExpiresAt(Date expiresAt) {
            this.expiresAt = expiresAt;
        }

        /**
         * Credential status object, e.g.
         * {@code {"status_list": {"idx": 7, "uri": "https://..."}}}.
         */
        public Map<String, Object> getStatus() {
            return status;
        }

        public void setStatus(Map<String, Object> status) {
            this.status = status;
        }

        /**
         * Confirmation method, e.g. {@code {"jwk": {...}}} binding the
         * credential to the holder's key.
         */
        public Map<String, Object> getCnf() {
            return cnf;
        }

        public void setCnf(Map<String, Object> cnf) {
            this.cnf = cnf;
        }

        public Map<String, Object> getAlwaysDisclosed() {
            return alwaysDisclosed;
        }

        public Map<String, Object> getSelectivelyDisclosed() {
            return selectivelyDisclosed;
        }
    }

    /**
     * The signed claims plus the disclosure strings for a payload.
     */
    public static class BuildResult {
        private final Map<String, Object> claims;
        private final List<String> disclosures;

        BuildResult(Map<String, Object> claims, List<String> disclosures) {
            this.claims = claims;
            this.disclosures = disclosures;
        }

        public Map<String, Object> getClaims() {
            return claims;
        }

        public List<String> getDisclosures() {
            return disclosures;
        }
    }

    /**
     * Builds the signed claims (including {@code _sd} digests) and the
     * disclosure strings for a payload, without signing. Callers that sign
     * through a key service use this and assemble
     * {@code <JWS>~<disclosure>~...} themselves.
     *
     * @param payload the credential payload
     * @return the claims and disclosures
     */
    public static BuildResult buildClaims(CredentialPayload payload) {
        Map<String, Object> claims = new TreeMap<>();
        claims.put("vct", payload.getVct());
        claims.put("iss", payload.getIss());
        if (payload.getSub() != null) {
            claims.put("sub", payload.getSub());
        }
        claims.put("iat", payload.getIssuedAt().getTime() / 1000);
        if (payload.getNotBefore() != null) {
            claims.put("nbf", payload.getNotBefore().getTime() / 1000);
        }
        if (payload.getExpiresAt() != null) {
            claims.put("exp", payload.getExpiresAt().getTime() / 1000);
        }
        if (payload.getStatus() != null) {
            claims.put("status", payload.getStatus());
        }
        if (payload.getCnf() != null) {
            claims.put("cnf", payload.getCnf());
        }
        claims.putAll(payload.getAlwaysDisclosed());

        List<String> disclosures = new ArrayList<>();
        List<String> digests = new ArrayList<>();
        for (Map.Entry<String, Object> entry : payload.getSelectivelyDisclosed().entrySet()) {
            String disclosure = SdJwtDigest.buildDisclosure(entry.getKey(), entry.getValue());
            disclosures.add(disclosure);
            digests.add(SdJwtDigest.digestOf(disclosure));
        }
        if (!digests.isEmpty()) {
            claims.put("_sd", digests);
            claims.put("_sd_alg", "sha-256");
        }
        return new BuildResult(claims, disclosures);
    }

    /**
     * Builds the SD-JWT (signed payload plus disclosures).
     *
     * @param payload  the credential payload
     * @param signer   the issuer signer
     * @param algorithm the signing algorithm
     * @param kid      the issuer key identifier
     * @return the SD-JWT string (JWS {@code ~} disclosures)
     * @throws JOSEException on signing failure
     */
    public String build(CredentialPayload payload, JWSSigner signer, JWSAlgorithm algorithm, String kid)
            throws JOSEException {
        BuildResult result = buildClaims(payload);

        JWSHeader header = new JWSHeader.Builder(algorithm)
                .type(new com.nimbusds.jose.JOSEObjectType("dc+sd-jwt"))
                .keyID(kid)
                .build();
        com.nimbusds.jose.JWSObject jwsObject = new com.nimbusds.jose.JWSObject(header,
                new Payload(SdJwtDigest.toJsonBytes(result.getClaims())));
        jwsObject.sign(signer);

        StringBuilder sb = new StringBuilder(jwsObject.serialize());
        for (String disclosure : result.getDisclosures()) {
            sb.append('~').append(disclosure);
        }
        // RFC 9901: the issuance serialization ends with a trailing '~'
        sb.append('~');
        return sb.toString();
    }

    /**
     * Serializes a holder public JWK for the {@code cnf.jwk} claim.
     *
     * @param publicJwk the holder public JWK
     * @return the cnf claim object
     */
    public static Map<String, Object> cnfForJwk(JWK publicJwk) {
        Map<String, Object> cnf = new LinkedHashMap<>();
        cnf.put("jwk", publicJwk.toJSONObject());
        return cnf;
    }
}
