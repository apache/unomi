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

package org.apache.unomi.didvc.edge.vp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * DCQL (Digital Credentials Query Language) parser for the subset the
 * verifier enforces: credential queries with a vct selector (either the
 * {@code format} object form or {@code meta.vct_values}), and claim
 * queries with a {@code path} and optional expected {@code values}.
 */
public class DcqlQueryParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * One parsed claim query.
     */
    public static class ClaimQuery {
        private final List<String> path;
        private final List<Object> values;

        ClaimQuery(List<String> path, List<Object> values) {
            this.path = path;
            this.values = values;
        }

        public List<String> getPath() {
            return path;
        }

        public List<Object> getValues() {
            return values;
        }
    }

    /**
     * One parsed credential query.
     */
    public static class CredentialQuery {
        private final String id;
        private final String format;
        private final String vct;
        private final List<ClaimQuery> claims;

        CredentialQuery(String id, String format, String vct, List<ClaimQuery> claims) {
            this.id = id;
            this.format = format;
            this.vct = vct;
            this.claims = claims;
        }

        public String getId() {
            return id;
        }

        public String getFormat() {
            return format;
        }

        public String getVct() {
            return vct;
        }

        public List<ClaimQuery> getClaims() {
            return claims;
        }
    }

    /**
     * A parsed DCQL query.
     */
    public static class Query {
        private final List<CredentialQuery> credentials;

        Query(List<CredentialQuery> credentials) {
            this.credentials = credentials;
        }

        public List<CredentialQuery> getCredentials() {
            return credentials;
        }

        /**
         * The first requested vct, or null.
         */
        public String expectedVct() {
            for (CredentialQuery credential : credentials) {
                if (credential.getVct() != null) {
                    return credential.getVct();
                }
            }
            return null;
        }

        /**
         * Whether the query requests the given credential type.
         */
        public boolean matchesVct(String vct) {
            return vct != null && vct.equals(expectedVct());
        }
    }

    /**
     * Parses a DCQL query JSON document.
     *
     * @param json the DCQL query JSON
     * @return the parsed query
     */
    public Query parse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.has("credentials") || !root.get("credentials").isArray()) {
                throw new IllegalArgumentException("DCQL query must contain a credentials array");
            }
            List<CredentialQuery> credentials = new ArrayList<>();
            for (JsonNode credentialNode : root.get("credentials")) {
                String id = credentialNode.path("id").asText(null);
                String format = parseFormatName(credentialNode.get("format"));
                String vct = parseVct(credentialNode);
                List<ClaimQuery> claims = new ArrayList<>();
                JsonNode claimsNode = credentialNode.get("claims");
                if (claimsNode != null && claimsNode.isArray()) {
                    for (JsonNode claimNode : claimsNode) {
                        List<String> path = parsePath(claimNode.get("path"));
                        List<Object> values = parseValues(claimNode.get("values"));
                        claims.add(new ClaimQuery(path, values));
                    }
                }
                credentials.add(new CredentialQuery(id, format, vct, claims));
            }
            return new Query(credentials);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Unreadable DCQL query", e);
        }
    }

    private String parseFormatName(JsonNode formatNode) {
        if (formatNode == null) {
            return null;
        }
        if (formatNode.isTextual()) {
            return formatNode.asText();
        }
        if (formatNode.isObject()) {
            Iterator<String> fields = formatNode.fieldNames();
            return fields.hasNext() ? fields.next() : null;
        }
        return null;
    }

    private String parseVct(JsonNode credentialNode) {
        JsonNode formatNode = credentialNode.get("format");
        if (formatNode != null && formatNode.isObject()) {
            // { "vc+sd-jwt": { "vct": "..." } } form
            Iterator<Map.Entry<String, JsonNode>> fields = formatNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                if ("vc+sd-jwt".equals(entry.getKey()) && entry.getValue().isObject()) {
                    String vct = entry.getValue().path("vct").asText(null);
                    if (vct != null) {
                        return vct;
                    }
                }
            }
        }
        JsonNode metaVctValues = credentialNode.path("meta").path("vct_values");
        if (metaVctValues.isArray() && metaVctValues.size() > 0) {
            return metaVctValues.get(0).asText(null);
        }
        return null;
    }

    private List<String> parsePath(JsonNode pathNode) {
        List<String> path = new ArrayList<>();
        if (pathNode == null || !pathNode.isArray()) {
            return path;
        }
        for (JsonNode element : pathNode) {
            String text = element.asText();
            // Support JSON-pointer style paths: "/givenName/legalName"
            if (text.startsWith("/")) {
                for (String segment : text.split("/")) {
                    if (!segment.isEmpty()) {
                        path.add(segment);
                    }
                }
            } else {
                path.add(text);
            }
        }
        return path;
    }

    private List<Object> parseValues(JsonNode valuesNode) {
        List<Object> values = new ArrayList<>();
        if (valuesNode == null || !valuesNode.isArray()) {
            return values;
        }
        for (JsonNode value : valuesNode) {
            if (value.isNumber()) {
                values.add(value.numberValue());
            } else if (value.isBoolean()) {
                values.add(value.booleanValue());
            } else {
                values.add(value.asText());
            }
        }
        return values;
    }
}
