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

package org.apache.unomi.didvc.api;

/**
 * Credential format serializer SPI. Implementations produce one of the W3C
 * Verifiable Credentials Data Model 2.0 formats; the SD-JWT VC formatter
 * ({@code vc+sd-jwt}) is the default.
 */
public interface CredentialFormatter {

    /**
     * The format identifier, e.g. {@code vc+sd-jwt} or {@code ldp_vc}.
     *
     * @return the format identifier
     */
    String getFormat();

    /**
     * Builds the serialized credential for a validated issue request.
     *
     * @param request the issue request (already claim- and consent-validated)
     * @return the serialized credential
     */
    String format(CredentialIssueRequest request);
}
