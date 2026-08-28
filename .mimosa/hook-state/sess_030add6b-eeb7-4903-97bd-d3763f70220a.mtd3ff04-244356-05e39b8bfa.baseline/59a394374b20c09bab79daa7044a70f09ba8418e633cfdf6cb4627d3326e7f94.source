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

import static org.apache.unomi.graphql.types.input.CDPPersonaInput.TYPE_NAME;

@GraphQLName(TYPE_NAME)
public class CDPPersonaInput {

    public static final String TYPE_NAME = "CDP_PersonaInput";

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

    public CDPPersonaInput(final @GraphQLID @GraphQLName("id") String id,
                           final @GraphQLNonNull @GraphQLName("cdp_name") String cdp_name,
                           final @GraphQLNonNull @GraphQLName("cdp_view") String cdp_view,
                           final @GraphQLName("cdp_profileIDs") List<CDPProfileIDInput> cdp_profileIDs,
                           final @GraphQLName("cdp_segments") Set<String> cdp_segments,
                           final @GraphQLName("cdp_interests") List<CDPInterestInput> cdp_interests,
                           final @GraphQLName("cdp_consents") List<CDPPersonaConsentInput> cdp_consents) {
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
