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

package org.apache.unomi.didvc.edge.customs;

import org.apache.unomi.didvc.edge.m2m.BearerCredentialVerifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Customs/EDI adapter (FR-L1/L4): translates between Single Window
 * message shapes and the M2M verification pipeline. Inbound: a
 * Single-Window declaration message ({@code messageType: DECLARATION})
 * carrying per-line-item credential references becomes a manifest
 * verification batch. Outbound: the per-manifest outcomes become a
 * Single-Window response message ({@code messageType: VERIFICATION},
 * one status line per manifest) with the status codes the Single
 * Window community expects. Round-trip safe: translating a declaration,
 * verifying, and translating the outcomes back preserves every
 * correlation id.
 */
@Component
public class CustomsEdiAdapter {

    /** Single Window acceptance status: all credentials verified. */
    public static final String STATUS_ACCEPTED = "1";
    /** Single Window rejection status: at least one credential failed. */
    public static final String STATUS_REJECTED = "2";

    /**
     * Parses a Single Window declaration message into manifest records.
     *
     * @param ediMessage the declaration JSON
     * @return the manifests (one per line item group, keyed by item id)
     */
    public List<EdiManifest> toManifests(Map<String, Object> ediMessage) {
        Object messageType = ediMessage.get("messageType");
        if (!"DECLARATION".equals(messageType)) {
            throw new IllegalArgumentException("unsupported messageType: " + messageType
                    + " — expected DECLARATION");
        }
        Object sender = ediMessage.get("sender");
        Object declarationNumber = ediMessage.get("declarationNumber");
        if (declarationNumber == null || declarationNumber.toString().isEmpty()) {
            throw new IllegalArgumentException("declarationNumber is required");
        }
        Object lineItems = ediMessage.get("lineItems");
        if (!(lineItems instanceof List) || ((List<?>) lineItems).isEmpty()) {
            throw new IllegalArgumentException("lineItems are required");
        }
        List<EdiManifest> manifests = new ArrayList<>();
        for (Object item : (List<?>) lineItems) {
            if (!(item instanceof Map)) {
                throw new IllegalArgumentException("lineItem must be an object");
            }
            Map<?, ?> lineItem = (Map<?, ?>) item;
            Object itemId = lineItem.get("itemId");
            if (itemId == null || itemId.toString().isEmpty()) {
                throw new IllegalArgumentException("lineItem itemId is required");
            }
            Object credentials = lineItem.get("credentials");
            if (!(credentials instanceof List) || ((List<?>) credentials).isEmpty()) {
                throw new IllegalArgumentException("lineItem " + itemId + " carries no credentials");
            }
            List<String> credentialList = new ArrayList<>();
            for (Object credential : (List<?>) credentials) {
                if (credential == null || credential.toString().isEmpty()) {
                    throw new IllegalArgumentException("lineItem " + itemId + " carries an empty credential");
                }
                credentialList.add(credential.toString());
            }
            manifests.add(new EdiManifest(
                    declarationNumber.toString() + ":" + itemId.toString(),
                    sender == null ? null : sender.toString(),
                    credentialList));
        }
        return manifests;
    }

    /**
     * Renders verification outcomes as a Single Window response
     * message: one status line per manifest, statuses per the Single
     * Window code list (1 accepted, 2 rejected).
     *
     * @param ediMessage the original declaration (for echo fields)
     * @param outcomes   the per-manifest outcomes, keyed by manifest id
     * @return the response message
     */
    public Map<String, Object> toResponse(Map<String, Object> ediMessage,
                                          Map<String, BearerCredentialVerifier.Outcome> outcomes) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("messageType", "VERIFICATION");
        response.put("inReplyTo", ediMessage.get("declarationNumber"));
        List<Map<String, Object>> statusLines = new ArrayList<>();
        for (Map.Entry<String, BearerCredentialVerifier.Outcome> entry : outcomes.entrySet()) {
            BearerCredentialVerifier.Outcome outcome = entry.getValue();
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("itemId", entry.getKey());
            line.put("status", outcome != null && outcome.isValid() ? STATUS_ACCEPTED : STATUS_REJECTED);
            if (outcome != null && !outcome.isValid()) {
                line.put("statusReason", outcome.getReason());
            }
            statusLines.add(line);
        }
        response.put("statusLines", statusLines);
        return response;
    }

    /**
     * One manifest extracted from a declaration line item.
     */
    public static class EdiManifest {
        private final String manifestId;
        private final String sender;
        private final List<String> credentials;

        EdiManifest(String manifestId, String sender, List<String> credentials) {
            this.manifestId = manifestId;
            this.sender = sender;
            this.credentials = credentials;
        }

        public String getManifestId() {
            return manifestId;
        }

        public String getSender() {
            return sender;
        }

        public List<String> getCredentials() {
            return credentials;
        }
    }
}
