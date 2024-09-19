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
package org.apache.unomi.graphql.types.input;

import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.apache.unomi.graphql.types.input.CDPEventFilterInput.TYPE_NAME;

@GraphQLName(TYPE_NAME)
public class CDPEventFilterInput {

    public static final String TYPE_NAME = "CDP_EventFilterInput";

    @GraphQLField
    private List<CDPEventFilterInput> and;

    @GraphQLField
    private List<CDPEventFilterInput> or;

    @GraphQLField
    private String id_equals;

    @GraphQLField
    private String cdp_clientID_equals;

    @GraphQLField
    private String cdp_sourceID_equals;

    @GraphQLField
    private String cdp_profileID_equals;

    @GraphQLField
    private OffsetDateTime cdp_timestamp_equals;

    @GraphQLField
    private OffsetDateTime cdp_timestamp_lt;

    @GraphQLField
    private OffsetDateTime cdp_timestamp_lte;

    @GraphQLField
    private OffsetDateTime cdp_timestamp_gt;

    @GraphQLField
    private OffsetDateTime cdp_timestamp_gte;

    @GraphQLField
    private CDPListsUpdateEventFilterInput cdp_listsUpdateEvent;

    @GraphQLField
    private CDPConsentUpdateEventFilterInput cdp_consentUpdateEvent;

    @GraphQLField
    private CDPSessionEventFilterInput cdp_sessionEvent;

    @GraphQLField
    private CDPProfileUpdateEventFilterInput cdp_profileUpdateEvent;

    public CDPEventFilterInput(
            final @GraphQLName("and") List<CDPEventFilterInput> and,
            final @GraphQLName("or") List<CDPEventFilterInput> or,
            final @GraphQLName("id_equals") String id_equals,
            final @GraphQLName("cdp_clientID_equals") String cdp_clientID_equals,
            final @GraphQLName("cdp_sourceID_equals") String cdp_sourceID_equals,
            final @GraphQLName("cdp_profileID_equals") String cdp_profileID_equals,
            final @GraphQLName("cdp_timestamp_equals") OffsetDateTime cdp_timestamp_equals,
            final @GraphQLName("cdp_timestamp_lt") OffsetDateTime cdp_timestamp_lt,
            final @GraphQLName("cdp_timestamp_lte") OffsetDateTime cdp_timestamp_lte,
            final @GraphQLName("cdp_timestamp_gt") OffsetDateTime cdp_timestamp_gt,
            final @GraphQLName("cdp_timestamp_gte") OffsetDateTime cdp_timestamp_gte,
            final @GraphQLName("cdp_consentUpdateEvent") CDPConsentUpdateEventFilterInput cdp_consentUpdateEvent,
            final @GraphQLName("cdp_listsUpdateEvent") CDPListsUpdateEventFilterInput cdp_listsUpdateEvent,
            final @GraphQLName("cdp_sessionEvent") CDPSessionEventFilterInput cdp_sessionEvent,
            final @GraphQLName("cdp_profileUpdateEvent") CDPProfileUpdateEventFilterInput cdp_profileUpdateEvent) {
        this.and = and;
        this.or = or;
        this.id_equals = id_equals;
        this.cdp_clientID_equals = cdp_clientID_equals;
        this.cdp_sourceID_equals = cdp_sourceID_equals;
        this.cdp_profileID_equals = cdp_profileID_equals;
        this.cdp_timestamp_equals = cdp_timestamp_equals;
        this.cdp_timestamp_lt = cdp_timestamp_lt;
        this.cdp_timestamp_lte = cdp_timestamp_lte;
        this.cdp_timestamp_gt = cdp_timestamp_gt;
        this.cdp_timestamp_gte = cdp_timestamp_gte;
        this.cdp_listsUpdateEvent = cdp_listsUpdateEvent;
        this.cdp_consentUpdateEvent = cdp_consentUpdateEvent;
        this.cdp_sessionEvent = cdp_sessionEvent;
        this.cdp_profileUpdateEvent = cdp_profileUpdateEvent;
    }

    public List<CDPEventFilterInput> getAnd() {
        return and;
    }

    public List<CDPEventFilterInput> getOr() {
        return or;
    }

    public String getId_equals() {
        return id_equals;
    }

    public String getCdp_clientID_equals() {
        return cdp_clientID_equals;
    }

    public String getCdp_sourceID_equals() {
        return cdp_sourceID_equals;
    }

    public String getCdp_profileID_equals() {
        return cdp_profileID_equals;
    }

    public OffsetDateTime getCdp_timestamp_equals() {
        return cdp_timestamp_equals;
    }

    public OffsetDateTime getCdp_timestamp_lt() {
        return cdp_timestamp_lt;
    }

    public OffsetDateTime getCdp_timestamp_lte() {
        return cdp_timestamp_lte;
    }

    public OffsetDateTime getCdp_timestamp_gt() {
        return cdp_timestamp_gt;
    }

    public OffsetDateTime getCdp_timestamp_gte() {
        return cdp_timestamp_gte;
    }

    public CDPListsUpdateEventFilterInput getCdp_listsUpdateEvent() {
        return cdp_listsUpdateEvent;
    }

    public CDPConsentUpdateEventFilterInput getCdp_consentUpdateEvent() {
        return cdp_consentUpdateEvent;
    }

    public CDPSessionEventFilterInput getCdp_sessionEvent() {
        return cdp_sessionEvent;
    }

    public CDPProfileUpdateEventFilterInput getCdp_profileUpdateEvent() {
        return cdp_profileUpdateEvent;
    }

    public static CDPEventFilterInput fromMap(final Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        final List<CDPEventFilterInput> andFltrs = recursiveList(map.get("and"));
        final List<CDPEventFilterInput> orFltrs = recursiveList(map.get("or"));
        final String idEq = (String) map.get("id_equals");
        final String clientIdEq = (String) map.get("cdp_clientID_equals");
        final String sourceIdEq = (String) map.get("cdp_sourceID_equals");
        final String profileIdEq = (String) map.get("cdp_profileID_equals");
        final OffsetDateTime timeEq = (OffsetDateTime) map.get("cdp_timestamp_equals");
        final OffsetDateTime timeLt = (OffsetDateTime) map.get("cdp_timestamp_lt");
        final OffsetDateTime timeLte = (OffsetDateTime) map.get("cdp_timestamp_lte");
        final OffsetDateTime timeGt = (OffsetDateTime) map.get("cdp_timestamp_gt");
        final OffsetDateTime timeGte = (OffsetDateTime) map.get("cdp_timestamp_gte");
        final CDPConsentUpdateEventFilterInput consentFltr = CDPConsentUpdateEventFilterInput.fromMap((Map<String, Object>) map.get("cdp_consentUpdateEvent"));
        final CDPListsUpdateEventFilterInput listsFltr = CDPListsUpdateEventFilterInput.fromMap((Map<String, Object>) map.get("cdp_listsUpdateEvent"));
        final CDPSessionEventFilterInput sessionFltr = CDPSessionEventFilterInput.fromMap((Map<String, Object>) map.get("cdp_sessionEvent"));
        final CDPProfileUpdateEventFilterInput profileFltr = CDPProfileUpdateEventFilterInput.fromMap((Map<String, Object>) map.get("cdp_profileUpdateEvent"));

        return new CDPEventFilterInput(andFltrs, orFltrs, idEq, clientIdEq, sourceIdEq, profileIdEq, timeEq, timeLt,
                timeLte, timeGt, timeGte, consentFltr, listsFltr, sessionFltr, profileFltr);
    }

    private static List<CDPEventFilterInput> recursiveList(Object list) {
        if (list == null) {
            return null;
        }
        return ((List<Map<String, Object>>) list).stream()
                .map(CDPEventFilterInput::fromMap)
                .collect(Collectors.toList());
    }

}
