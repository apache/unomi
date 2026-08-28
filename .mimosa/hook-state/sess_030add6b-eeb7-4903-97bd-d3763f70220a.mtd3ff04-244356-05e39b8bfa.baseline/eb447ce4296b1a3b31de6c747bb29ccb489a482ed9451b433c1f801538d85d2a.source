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

package org.apache.unomi.didvc.api.services;

/**
 * Per-verifier pseudonymous subject references: each relying tenant receives
 * a different opaque reference for the same profile, so verifiers cannot
 * correlate a subject across institutions. This service is the linkage half
 * of the split-knowledge pattern — profile resolution stays inside the
 * platform and is never exposed to the verification edge.
 */
public interface PairwiseBindingService {

    /**
     * Creates (or returns the existing) opaque reference for a profile
     * towards a verifier tenant.
     *
     * @param profileId        the profile identifier
     * @param verifierTenantId the relying tenant
     * @return the verifier-scoped opaque reference
     */
    String getOrCreateOpaqueReference(String profileId, String verifierTenantId);

    /**
     * Resolves an opaque reference back to the profile identifier. Internal
     * use only — never returned to verifiers.
     *
     * @param verifierTenantId the relying tenant that the reference was scoped to
     * @param opaqueReference  the opaque reference
     * @return the profile identifier, or null if unknown
     */
    String resolveProfileId(String verifierTenantId, String opaqueReference);
}
