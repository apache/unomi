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
package org.apache.unomi.graphql;

import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.graphql.services.impl.CDPServiceManager;
import org.apache.unomi.graphql.types.CDP_Profile;
import org.apache.unomi.graphql.types.CDP_ProfileIDInput;

@GraphQLName("MyCDP_Query")
public class MyCDPQuery {

    private ObjectMapper objectMapper = new ObjectMapper();

    private CDPServiceManager cdpServiceManager;

    public MyCDPQuery() {
        this.cdpServiceManager = CDPServiceManager.getInstance();
    }

    @GraphQLField
    public CDP_Profile getProfile(
            final @GraphQLName("profileID") CDP_ProfileIDInput profileID,
            final @GraphQLName("createIfMissing") Boolean createIfMissing,
            final DataFetchingEnvironment environment) {
        final CDP_ProfileIDInput profileIDInput =
                objectMapper.convertValue(environment.getArgument("profileID"), CDP_ProfileIDInput.class);

        return new CDP_Profile(cdpServiceManager.getProfileService(), profileIDInput, createIfMissing);
    }

}
