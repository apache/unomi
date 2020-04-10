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
import graphql.annotations.annotationTypes.GraphQLID;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;

import java.util.List;
import java.util.Set;

import static org.apache.unomi.graphql.types.input.CDPPersonaInput.TYPE_NAME_INTERNAL;

@GraphQLName(TYPE_NAME_INTERNAL)
public class CDPPersonaInput {

    public static final String TYPE_NAME_INTERNAL = "CDP_Persona";

    public static final String TYPE_NAME = TYPE_NAME_INTERNAL + "Input";

    @GraphQLID
    @GraphQLField
    private String id;

    @GraphQLField
    @GraphQLNonNull
    private String cdp_name;

    @GraphQLField
    @GraphQLNonNull
    private String cdp_view;

    @GraphQLField
    private List<CDPProfileIDInput> cdp_profileIDs;

    @GraphQLField
    private Set<String> cdp_segments;

    @GraphQLField
    private List<CDPInterestInput> cdp_interests;

    @GraphQLField
    private List<CDPPersonaConsentInput> cdp_consents;

    // # fields will be added here according to registered profile properties

    public CDPPersonaInput(@GraphQLID @GraphQLName("id") final String id,
                           @GraphQLNonNull @GraphQLName("cdp_name") final String cdp_name,
                           @GraphQLNonNull @GraphQLName("cdp_view") final String cdp_view,
                           @GraphQLName("cdp_profileIDs") final List<CDPProfileIDInput> cdp_profileIDs,
                           @GraphQLName("cdp_segments") final Set<String> cdp_segments,
                           @GraphQLName("cdp_interests") final List<CDPInterestInput> cdp_interests,
                           @GraphQLName("cdp_consents") final List<CDPPersonaConsentInput> cdp_consents) {
        this.id = id;
        this.cdp_name = cdp_name;
        this.cdp_view = cdp_view;
        this.cdp_profileIDs = cdp_profileIDs;
        this.cdp_segments = cdp_segments;
        this.cdp_interests = cdp_interests;
        this.cdp_consents = cdp_consents;
    }

    public String getId() {
        return id;
    }

    public String getCdp_name() {
        return cdp_name;
    }

    public String getCdp_view() {
        return cdp_view;
    }

    public List<CDPProfileIDInput> getCdp_profileIDs() {
        return cdp_profileIDs;
    }

    public Set<String> getCdp_segments() {
        return cdp_segments;
    }

    public List<CDPInterestInput> getCdp_interests() {
        return cdp_interests;
    }

    public List<CDPPersonaConsentInput> getCdp_consents() {
        return cdp_consents;
    }
}
