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

import org.apache.unomi.didvc.api.items.StatusListRecord;

/**
 * W3C Bitstring Status List (v1.0) credential-status management: index
 * allocation, revocation bit flips, and publication of the signed status-list
 * JWT. Because every verification checks the list, revocation takes effect at
 * the next verification.
 */
public interface StatusService {

    /**
     * Creates a new status list.
     *
     * @param tenantId      the tenant owning the list
     * @param issuerDid     the issuer DID the list belongs to
     * @param statusPurpose {@code revocation} or {@code suspension}
     * @param size          initial number of status entries
     * @return the created status list
     */
    StatusListRecord createStatusList(String tenantId, String issuerDid, String statusPurpose, int size);

    /**
     * Allocates the next free status index.
     *
     * @param statusListId the status-list record identifier
     * @return the allocated index
     */
    int allocateIndex(String statusListId);

    /**
     * Marks the status at the given index as revoked (bit set). Idempotent.
     *
     * @param statusListId the status-list record identifier
     * @param index        the status index
     */
    void revoke(String statusListId, int index);

    /**
     * Reads the status bit for an index.
     *
     * @param statusListId the status-list record identifier
     * @param index        the status index
     * @return true when the credential status is set (e.g. revoked)
     */
    boolean isRevoked(String statusListId, int index);

    /**
     * Publishes (or re-publishes) the signed status-list JWT with the current
     * bitstring.
     *
     * @param statusListId the status-list record identifier
     * @param kid          the issuer key to sign with
     * @return the signed status-list JWT
     */
    String publish(String statusListId, String kid);

    /**
     * Builds a StatusList2021Credential-shaped JWT wrapping the same
     * bitstring, for verifiers that speak the older status-list profile.
     *
     * @param statusListId the status-list record identifier
     * @param kid          the issuer key to sign with
     * @return the signed StatusList2021 credential JWT
     */
    String buildStatusList2021Jwt(String statusListId, String kid);

    /**
     * Loads a status list by record identifier.
     *
     * @param statusListId the status-list record identifier
     * @return the status list, or null if unknown
     */
    StatusListRecord getStatusList(String statusListId);
}
