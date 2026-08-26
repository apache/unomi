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

package org.apache.unomi.didvc.api.items;

import org.apache.unomi.api.Item;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Credential schema definition: which claims a credential of a given type may
 * carry, which of them are required, and the JSON type of each claim. The
 * allowed-claim set is the claim-minimization whitelist — any claim not listed
 * is rejected at issuance, so raw PII never enters a credential payload.
 */
public class DidSchema extends Item {
    /**
     * The DidSchema ITEM_TYPE.
     */
    public static final String ITEM_TYPE = "didvc-schema";
    private static final long serialVersionUID = 1298364344951983734L;

    private String name;
    private String vct;
    private String description;
    private Set<String> allowedClaims = new HashSet<>();
    private Set<String> requiredClaims = new HashSet<>();
    private Map<String, String> claimTypes = new HashMap<>();

    /**
     * Default constructor.
     */
    public DidSchema() {
    }

    /**
     * Creates a schema with the given identifier.
     *
     * @param schemaId the schema identifier
     */
    public DidSchema(String schemaId) {
        super(schemaId);
        this.itemType = ITEM_TYPE;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * The SD-JWT Verifiable Credential Type (vct) used when credentials of this
     * schema are issued.
     */
    public String getVct() {
        return vct;
    }

    public void setVct(String vct) {
        this.vct = vct;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Claims permitted in credentials of this schema. Anything outside this
     * whitelist is rejected by claim validation.
     */
    public Set<String> getAllowedClaims() {
        return allowedClaims;
    }

    public void setAllowedClaims(Set<String> allowedClaims) {
        this.allowedClaims = allowedClaims;
    }

    public Set<String> getRequiredClaims() {
        return requiredClaims;
    }

    public void setRequiredClaims(Set<String> requiredClaims) {
        this.requiredClaims = requiredClaims;
    }

    /**
     * Maps claim names to JSON types ({@code string}, {@code number},
     * {@code boolean}, {@code array}, {@code object}).
     */
    public Map<String, String> getClaimTypes() {
        return claimTypes;
    }

    public void setClaimTypes(Map<String, String> claimTypes) {
        this.claimTypes = claimTypes;
    }
}
