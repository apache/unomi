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
import java.util.ArrayList;
import java.util.List;

@GraphQLName("CDP_EventFilter")
public class CDPEventFilterInput {

    @GraphQLField
    private List<CDPEventFilterInput> and = new ArrayList<>();

    @GraphQLField
    private List<CDPEventFilterInput> or = new ArrayList<>();

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
            final @GraphQLName("cdp_timestamp_gte") OffsetDateTime cdp_timestamp_gte) {
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
    }

    public static CDPEventFilterInput from(final String cdp_profileID_equals) {
        return new CDPEventFilterInput(null, null, null, null, null, cdp_profileID_equals, null, null, null, null, null);
    }

    public List<CDPEventFilterInput> getAnd() {
        return and;
    }

    public void setAnd(List<CDPEventFilterInput> and) {
        this.and = and;
    }

    public List<CDPEventFilterInput> getOr() {
        return or;
    }

    public void setOr(List<CDPEventFilterInput> or) {
        this.or = or;
    }

    public String getId_equals() {
        return id_equals;
    }

    public void setId_equals(String id_equals) {
        this.id_equals = id_equals;
    }

    public String getCdp_clientID_equals() {
        return cdp_clientID_equals;
    }

    public void setCdp_clientID_equals(String cdp_clientID_equals) {
        this.cdp_clientID_equals = cdp_clientID_equals;
    }

    public String getCdp_sourceID_equals() {
        return cdp_sourceID_equals;
    }

    public void setCdp_sourceID_equals(String cdp_sourceID_equals) {
        this.cdp_sourceID_equals = cdp_sourceID_equals;
    }

    public String getCdp_profileID_equals() {
        return cdp_profileID_equals;
    }

    public void setCdp_profileID_equals(String cdp_profileID_equals) {
        this.cdp_profileID_equals = cdp_profileID_equals;
    }

    public OffsetDateTime getCdp_timestamp_equals() {
        return cdp_timestamp_equals;
    }

    public CDPEventFilterInput setCdp_timestamp_equals(OffsetDateTime cdp_timestamp_equals) {
        this.cdp_timestamp_equals = cdp_timestamp_equals;
        return this;
    }

    public OffsetDateTime getCdp_timestamp_lt() {
        return cdp_timestamp_lt;
    }

    public CDPEventFilterInput setCdp_timestamp_lt(OffsetDateTime cdp_timestamp_lt) {
        this.cdp_timestamp_lt = cdp_timestamp_lt;
        return this;
    }

    public OffsetDateTime getCdp_timestamp_lte() {
        return cdp_timestamp_lte;
    }

    public CDPEventFilterInput setCdp_timestamp_lte(OffsetDateTime cdp_timestamp_lte) {
        this.cdp_timestamp_lte = cdp_timestamp_lte;
        return this;
    }

    public OffsetDateTime getCdp_timestamp_gt() {
        return cdp_timestamp_gt;
    }

    public CDPEventFilterInput setCdp_timestamp_gt(OffsetDateTime cdp_timestamp_gt) {
        this.cdp_timestamp_gt = cdp_timestamp_gt;
        return this;
    }

    public OffsetDateTime getCdp_timestamp_gte() {
        return cdp_timestamp_gte;
    }

    public CDPEventFilterInput setCdp_timestamp_gte(OffsetDateTime cdp_timestamp_gte) {
        this.cdp_timestamp_gte = cdp_timestamp_gte;
        return this;
    }

}
