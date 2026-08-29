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
}
