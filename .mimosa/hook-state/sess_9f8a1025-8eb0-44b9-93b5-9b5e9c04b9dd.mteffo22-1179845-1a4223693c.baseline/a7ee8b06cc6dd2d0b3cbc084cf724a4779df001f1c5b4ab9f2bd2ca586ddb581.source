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
import graphql.annotations.annotationTypes.GraphQLNonNull;

import java.time.OffsetDateTime;

@GraphQLName("CDP_PersonaConsentInput")
public class CDPPersonaConsentInput {

    @GraphQLField
    @GraphQLNonNull
    private String type;

    @GraphQLField
    private String status;

    @GraphQLField
    private OffsetDateTime lastUpdate;

    @GraphQLField
    private OffsetDateTime expiration;

    public CDPPersonaConsentInput(final @GraphQLNonNull @GraphQLName("type") String type,
                                  final @GraphQLName("status") String status,
                                  final @GraphQLName("lastUpdate") OffsetDateTime lastUpdate,
                                  final @GraphQLName("expiration") OffsetDateTime expiration) {
        this.type = type;
        this.status = status;
        this.lastUpdate = lastUpdate;
        this.expiration = expiration;
    }

    public String getType() {
        return type;
    }

    public String getStatus() {
        return status;
    }

    public OffsetDateTime getLastUpdate() {
        return lastUpdate;
    }

    public OffsetDateTime getExpiration() {
        return expiration;
    }
}
