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
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLTypeResolver;
import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.graphql.types.resolvers.CDPProfileTypeResolver;

import java.util.List;

import static org.apache.unomi.graphql.types.output.CDPProfileInterface.TYPE_NAME;

@GraphQLName(TYPE_NAME)
@GraphQLTypeResolver(CDPProfileTypeResolver.class)
@GraphQLDescription("Common interface for Profiles and Personas.")
public interface CDPProfileInterface {

    String TYPE_NAME = "CDP_ProfileInterface";

    Object getProperty(final String propertyName);

    @GraphQLField
    List<CDPProfileID> cdp_profileIDs(final DataFetchingEnvironment environment) throws Exception;

    @GraphQLField
    List<CDPSegment> cdp_segments(
            final @GraphQLName("views") List<String> viewIds,
            final DataFetchingEnvironment environment) throws Exception;

    @GraphQLField
    List<CDPInterest> cdp_interests(
            final @GraphQLName("views") List<String> viewIds,
            final DataFetchingEnvironment environment) throws Exception;

    @GraphQLField
    List<CDPConsent> cdp_consents(final DataFetchingEnvironment environment) throws Exception;

    @GraphQLField
    List<CDPList> cdp_lists(final @GraphQLName("views") List<String> viewIds,
                            final DataFetchingEnvironment environment) throws Exception;

}
