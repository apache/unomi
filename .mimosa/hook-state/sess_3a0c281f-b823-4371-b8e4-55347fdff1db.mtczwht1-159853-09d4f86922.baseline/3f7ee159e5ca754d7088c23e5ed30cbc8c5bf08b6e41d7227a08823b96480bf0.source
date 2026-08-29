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

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLID;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.api.Consent;
import org.apache.unomi.graphql.fetchers.consent.ConsentEventConnectionDataFetcher;
import org.apache.unomi.graphql.utils.DateUtils;

import java.time.OffsetDateTime;

@GraphQLName("CDP_Consent")
@GraphQLDescription("CDP_Consent represents a persisted Consent, always attached to a specific profile.")
public class CDPConsent {

    private String token;
    private Consent consent;

    public CDPConsent(String token, Consent consent) {
        this.token = token;
        this.consent = consent;
    }

    public String getToken() {
        return token;
    }

    @GraphQLID
    @GraphQLField
    @GraphQLNonNull
    public String token() {
        return token;
    }

    @GraphQLField
    public CDPSource source() {
        return consent != null ? new CDPSource(consent.getScope()) : null;
    }

    @GraphQLField
    public CDPClient client() {
        return CDPClient.DEFAULT;
    }

    @GraphQLField
    public String type() {
        return consent != null ? consent.getTypeIdentifier() : null;
    }

    @GraphQLField
    public CDPConsentStatus status() {
        return consent != null ? CDPConsentStatus.from(consent.getStatus()) : null;
    }

    @GraphQLField
    public OffsetDateTime lastUpdate() {
        return consent != null ? DateUtils.toOffsetDateTime(consent.getStatusDate()) : null;
    }

    @GraphQLField
    public OffsetDateTime expiration() {
        return consent != null ? DateUtils.toOffsetDateTime(consent.getRevokeDate()) : null;
    }

    @GraphQLField
    public CDPProfileInterface profile() {
        return null;
    }

    @GraphQLField
    public CDPEventConnection events(final DataFetchingEnvironment environment) throws Exception {
        return new ConsentEventConnectionDataFetcher().get(environment);
    }

}
