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

import org.apache.unomi.didvc.api.items.ConsentGrantRecord;

import java.util.Set;

/**
 * Consent bridge over the consent-grant store: governs which claims a
 * subject has authorized for disclosure to a given verifier category, per
 * credential type. Issuance refuses to build credentials whose disclosure
 * exceeds the granted claim set.
 */
public interface ConsentBridgeService {

    /**
     * Saves a consent grant.
     *
     * @param grant the grant
     */
    void saveGrant(ConsentGrantRecord grant);

    /**
     * Returns the claims granted to a subject for a schema and verifier
     * category.
     *
     * @param subjectId        the subject
     * @param schemaId         the credential schema
     * @param verifierCategory the verifier category
     * @return the granted claims (empty when no grant exists)
     */
    Set<String> getGrantedClaims(String subjectId, String schemaId, String verifierCategory);

    /**
     * Verifies that every claim in {@code claims} is granted. Throws
     * {@link IllegalArgumentException} otherwise.
     *
     * @param subjectId        the subject
     * @param schemaId         the credential schema
     * @param verifierCategory the verifier category
     * @param claims           the claims to disclose
     */
    void verifyDisclosure(String subjectId, String schemaId, String verifierCategory, Set<String> claims);
}
