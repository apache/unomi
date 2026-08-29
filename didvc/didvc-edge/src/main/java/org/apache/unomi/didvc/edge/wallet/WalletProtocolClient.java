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

import java.util.Map;

/**
 * The OID4VCI/OID4VP client operations the wallet backend performs on
 * remote issuers and verifiers: issuer metadata discovery, token
 * exchange, credential delivery, authorization-request fetching and
 * presentation submission. The HTTP implementation talks to remote
 * endpoints; tests substitute an in-process variant.
 */
public interface WalletProtocolClient {

    /**
     * Fetches an issuer's OID4VCI metadata.
     *
     * @param credentialIssuer the issuer identifier (base URL)
     * @return the metadata document
     */
    JsonNode fetchIssuerMetadata(String credentialIssuer);

    /**
     * OID4VCI token exchange (form-encoded).
     *
     * @param tokenEndpoint the issuer's token endpoint
     * @param grantType     the grant type
     * @param grantParam    the grant parameter name ({@code pre-authorized_code})
     * @param grantValue    the grant parameter value
     * @return the token response
     */
    JsonNode tokenRequest(String tokenEndpoint, String grantType, String grantParam, String grantValue);

    /**
     * OID4VCI credential request (JSON, bearer access token).
     *
     * @param credentialEndpoint the issuer's credential endpoint
     * @param accessToken        the access token
     * @param body               the credential request
     * @return the credential response
     */
    JsonNode credentialRequest(String credentialEndpoint, String accessToken, Map<String, Object> body);

    /**
     * Fetches an OID4VP authorization request object.
     *
     * @param requestUri the {@code request_uri}
     * @return the raw request object (compact JWS)
     */
    String fetchRequestObject(String requestUri);

    /**
     * Submits a presentation to a verifier's {@code direct_post}
     * endpoint (JSON form) and returns the verification result.
     *
     * @param responseUri the verifier's response URI
     * @param submission  the submission ({@code state}, {@code nonce},
     *                    {@code vp_token})
     * @return the verification result
     */
    JsonNode postPresentation(String responseUri, Map<String, Object> submission);
}
