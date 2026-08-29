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

package org.apache.unomi.didvc.edge;

import org.apache.unomi.didvc.batch.KafkaManifestResultSink;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.UUID;
/**
 * Edge configuration. The request-signing secret defaults to a random
 * per-boot value (valid for a single instance); set it explicitly when the
 * verifier front end is load-balanced.
 */
@ConfigurationProperties(prefix = "didvc.edge")
public class EdgeProperties {

    /**
     * Public base URL of this edge, used as the credential_issuer value and
     * as the key-binding audience.
     */
    private String issuerBaseUrl = "http://localhost:8080";

    /**
     * Base URL of the Unomi platform REST API.
     */
    private String platformBaseUrl = "http://localhost:8181";

    /**
     * API key presented to the platform.
     */
    private String platformApiKey = "";

    /**
     * API key required on the internal offer-creation endpoint.
     */
    private String internalApiKey = "";

    /**
     * Fee billed per successful verification, in minor units.
     */
    private long verificationFeeMinorUnits = 150;

    private String verificationFeeCurrency = "HKD";

    private String requestSigningSecret = UUID.randomUUID().toString();

    /**
     * API keys accepted by the M2M verification endpoint (FR-L2). Keys
     * are provisioned out of band and read from configuration /
     * environment (e.g. {@code DIDVC_EDGE_M2MAPIKEYS_0}); an empty list
     * keeps the endpoint closed. Never set usable values in source.
     */
    private java.util.List<String> m2mApiKeys = new java.util.ArrayList<>();

    /**
     * Demo-only: issuer identifier of an external credential issuer whose
     * credentials the demo verifier should accept (e.g. a conformance
     * suite wallet acting as credential issuer). Key material is supplied
     * via environment or vault — never committed.
     */
    private String demoExternalIssuerDid = null;

    /**
     * Demo-only: public JWK (JSON) of the external credential issuer; both
     * this and the issuer DID must be set for the demo verifier to resolve
     * and trust the external issuer.
     */
    private String demoExternalIssuerJwk = null;

    /**
     * Demo-only: vct of the external issuer's credential to register in
     * the demo trust entries (default {@code hkt_kyc_v1}).
     */
    private String demoExternalIssuerVct = "hkt_kyc_v1";

    public String getIssuerBaseUrl() {
        return issuerBaseUrl;
    }

    public void setIssuerBaseUrl(String issuerBaseUrl) {
        this.issuerBaseUrl = issuerBaseUrl;
    }

    public String getPlatformBaseUrl() {
        return platformBaseUrl;
    }

    public void setPlatformBaseUrl(String platformBaseUrl) {
        this.platformBaseUrl = platformBaseUrl;
    }

    public String getPlatformApiKey() {
        return platformApiKey;
    }

    public void setPlatformApiKey(String platformApiKey) {
        this.platformApiKey = platformApiKey;
    }

    public String getInternalApiKey() {
        return internalApiKey;
    }

    public String getDemoExternalIssuerDid() {
        return demoExternalIssuerDid;
    }

    public void setDemoExternalIssuerDid(String demoExternalIssuerDid) {
        this.demoExternalIssuerDid = demoExternalIssuerDid;
    }

    public String getDemoExternalIssuerJwk() {
        return demoExternalIssuerJwk;
    }

    public void setDemoExternalIssuerJwk(String demoExternalIssuerJwk) {
        this.demoExternalIssuerJwk = demoExternalIssuerJwk;
    }

    public String getDemoExternalIssuerVct() {
        return demoExternalIssuerVct;
    }

    public void setDemoExternalIssuerVct(String demoExternalIssuerVct) {
        this.demoExternalIssuerVct = demoExternalIssuerVct;
    }

    public void setInternalApiKey(String internalApiKey) {
        this.internalApiKey = internalApiKey;
    }

    public long getVerificationFeeMinorUnits() {
        return verificationFeeMinorUnits;
    }

    public void setVerificationFeeMinorUnits(long verificationFeeMinorUnits) {
        this.verificationFeeMinorUnits = verificationFeeMinorUnits;
    }

    public String getVerificationFeeCurrency() {
        return verificationFeeCurrency;
    }

    public void setVerificationFeeCurrency(String verificationFeeCurrency) {
        this.verificationFeeCurrency = verificationFeeCurrency;
    }

    public String getRequestSigningSecret() {
        return requestSigningSecret;
    }

    public void setRequestSigningSecret(String requestSigningSecret) {
        this.requestSigningSecret = requestSigningSecret;
    }

    public java.util.List<String> getM2mApiKeys() {
        return m2mApiKeys;
    }

    public void setM2mApiKeys(java.util.List<String> m2mApiKeys) {
        this.m2mApiKeys = m2mApiKeys;
    }

    /**
     * Kafka bootstrap servers for the manifest-result topic; empty
     * disables Kafka publishing (audit-only batch processing).
     */
    private String manifestKafkaBootstrapServers = "";

    /**
     * Kafka topic for manifest-verification results.
     */
    private String manifestKafkaTopic = KafkaManifestResultSink.DEFAULT_TOPIC;

    public String getManifestKafkaBootstrapServers() {
        return manifestKafkaBootstrapServers;
    }

    public void setManifestKafkaBootstrapServers(String manifestKafkaBootstrapServers) {
        this.manifestKafkaBootstrapServers = manifestKafkaBootstrapServers;
    }

    public String getManifestKafkaTopic() {
        return manifestKafkaTopic;
    }

    public void setManifestKafkaTopic(String manifestKafkaTopic) {
        this.manifestKafkaTopic = manifestKafkaTopic;
    }

    /**
     * GB/Z 185 interop-bridge issuer keys (FR-ID6): issuer DID/URL →
     * public JWK JSON. Read from configuration/environment (provisioned
     * out of band with the mainland trust counterparties); never
     * committed.
     */
    private java.util.Map<String, String> gbz185IssuerJwks = new java.util.LinkedHashMap<>();

    /**
     * GB/Z 185 per-tenant policy mappings: entries of the form
     * {@code tenantId|issuerId=scope1,scope2} (empty scope list accepts
     * any scope from that issuer).
     */
    private java.util.List<String> gbz185Policies = new java.util.ArrayList<>();

    public java.util.Map<String, String> getGbz185IssuerJwks() {
        return gbz185IssuerJwks;
    }

    public void setGbz185IssuerJwks(java.util.Map<String, String> gbz185IssuerJwks) {
        this.gbz185IssuerJwks = gbz185IssuerJwks;
    }

    public java.util.List<String> getGbz185Policies() {
        return gbz185Policies;
    }

    public void setGbz185Policies(java.util.List<String> gbz185Policies) {
        this.gbz185Policies = gbz185Policies;
    }

    /**
     * The accepted policy scopes for an issuer under a tenant, or null
     * when the issuer is not mapped for that tenant.
     *
     * @param tenantId the relying tenant
     * @param issuerId the GB/Z 185 linkage-VP issuer
     * @return the accepted scopes (empty list = any), or null
     */
    public java.util.List<String> gbz185AllowedScopes(String tenantId, String issuerId) {
        String prefix = tenantId + "|" + issuerId + "=";
        for (String entry : gbz185Policies) {
            if (entry.startsWith(prefix)) {
                String scopes = entry.substring(prefix.length()).trim();
                if (scopes.isEmpty()) {
                    return java.util.List.of();
                }
                return java.util.Arrays.asList(scopes.split(","));
            }
        }
        return null;
    }
}
