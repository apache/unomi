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

package org.apache.unomi.graphql.types.output;

import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLID;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import graphql.annotations.annotationTypes.GraphQLPrettify;
import org.apache.unomi.api.Consent;
import org.apache.unomi.api.Persona;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.unomi.graphql.types.output.CDPPersona.TYPE_NAME;

@GraphQLName(TYPE_NAME)
public class CDPPersona {

    public static final String TYPE_NAME = "CDP_Persona";

    private Persona persona;


    public CDPPersona(Persona persona) {
        this.persona = persona;
    }

    @GraphQLID
    @GraphQLField
    @GraphQLPrettify
    public String getId() {
        return persona != null ? persona.getItemId() : null;
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLPrettify
    public String getCdp_name() {
        return persona != null ? (String) persona.getProperty("cdp_name") : null;
    }

    @GraphQLField
    @GraphQLNonNull
    @GraphQLPrettify
    public String getCdp_view() {
        return persona != null ? (String) persona.getProperty("cdp_view") : null;
    }

    @GraphQLField
    @GraphQLPrettify
    public List<CDPProfileID> getCdp_profileIDs() {
        if (persona == null) {
            return null;
        }

        List<String> profileIds = (List<String>) persona.getProperty("mergedWith");
        return profileIds != null ? profileIds.stream().map(CDPProfileID::new).collect(Collectors.toList()) : null;
    }

    @GraphQLField
    @GraphQLPrettify
    public Set<String> getCdp_segments() {
        return persona != null ? persona.getSegments() : null;
    }

    @GraphQLField
    @GraphQLPrettify
    public List<CDPInterest> getCdp_interests() {
        if (persona == null) {
            return null;
        }

        Map<String, Double> interests = (Map<String, Double>) persona.getProperty("interests");
        return interests != null ? interests.entrySet().stream().map(entry -> new CDPInterest(entry.getKey(), entry.getValue())).collect(Collectors.toList()) : null;
    }

    @GraphQLField
    @GraphQLPrettify
    public List<CDPConsent> getCdp_consents() {
        if (persona == null) {
            return null;
        }

        Map<String, Consent> consents = persona.getConsents();
        return consents != null ? consents.entrySet().stream().map(entry -> new CDPConsent(entry.getKey(), entry.getValue())).collect(Collectors.toList()) : null;
    }

    public Persona getPersona() {
        return persona;
    }
}
