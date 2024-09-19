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
package org.apache.unomi.graphql.providers.sample;

import graphql.Scalars;
import graphql.annotations.processor.GraphQLAnnotations;
import graphql.schema.DataFetcher;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.visibility.BlockedFields;
import graphql.schema.visibility.GraphqlFieldVisibility;
import org.apache.unomi.graphql.providers.GraphQLAdditionalTypesProvider;
import org.apache.unomi.graphql.providers.GraphQLCodeRegistryProvider;
import org.apache.unomi.graphql.providers.GraphQLExtensionsProvider;
import org.apache.unomi.graphql.providers.GraphQLFieldVisibilityProvider;
import org.apache.unomi.graphql.providers.GraphQLMutationProvider;
import org.apache.unomi.graphql.providers.GraphQLProvider;
import org.apache.unomi.graphql.providers.GraphQLQueryProvider;
import org.apache.unomi.graphql.types.output.CDPProfile;
import org.apache.unomi.graphql.types.output.RootMutation;
import org.apache.unomi.graphql.types.output.RootQuery;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Component(immediate = true, service = GraphQLProvider.class)
public class CDPProviderSample
        implements GraphQLExtensionsProvider, GraphQLMutationProvider, GraphQLQueryProvider, GraphQLCodeRegistryProvider, GraphQLAdditionalTypesProvider, GraphQLFieldVisibilityProvider {

    private boolean isActivated;

    @Activate
    public void activate(final BundleContext context) {
        this.isActivated = true;
    }

    @Deactivate
    public void deactivate() {
        this.isActivated = false;
    }

    @Override
    public Set<Class<?>> getExtensions() {
        return Collections.singleton(CDPProfileExtension.class);
    }

    @Override
    public Set<GraphQLFieldDefinition> getMutations(GraphQLAnnotations graphQLAnnotations) {
        final Set<GraphQLFieldDefinition> mutations = new HashSet<>();

        mutations.add(GraphQLFieldDefinition.newFieldDefinition()
                .type(Scalars.GraphQLBoolean)
                .name("mutation1")
                .build());

        return mutations;
    }

    @Override
    public Set<GraphQLFieldDefinition> getQueries(GraphQLAnnotations graphQLAnnotations) {
        final Set<GraphQLFieldDefinition> queries = new HashSet<>();

        queries.add(GraphQLFieldDefinition.newFieldDefinition()
                .type(graphQLAnnotations.object(SampleType.class))
                .name("query1")
                .build());
        queries.add(GraphQLFieldDefinition.newFieldDefinition()
                .type(graphQLAnnotations.object(SampleType.class))
                .name("query2")
                .argument(GraphQLArgument.newArgument()
                        .type(Scalars.GraphQLString)
                        .name("param")
                        .build())
                .build());

        return queries;
    }

    @Override
    public GraphQLCodeRegistry.Builder getCodeRegistry(final GraphQLCodeRegistry codeRegistry) {
        return GraphQLCodeRegistry
                .newCodeRegistry(codeRegistry)
                .dataFetcher(FieldCoordinates.coordinates(RootQuery.TYPE_NAME, "query1"),
                        (DataFetcher<SampleType>) environment -> {
                            SampleType sampleType = new SampleType();
                            sampleType.field = "I'm a result of query1";
                            return sampleType;
                        })
                .dataFetcher(FieldCoordinates.coordinates(RootQuery.TYPE_NAME, "query2"),
                        (DataFetcher<SampleType>) environment -> {
                            SampleType sampleType = new SampleType();
                            sampleType.field = "I'm a result of query2";
                            return sampleType;
                        })
                .dataFetcher(FieldCoordinates.coordinates(RootMutation.TYPE_NAME, "mutation1"),
                        (DataFetcher<Boolean>) environment -> true)
                .dataFetcher(FieldCoordinates.coordinates(CDPProfile.TYPE_NAME, "extensionField"),
                        (DataFetcher<String>) environment -> "Hello " + ((CDPProfile) environment.getSource()).getProfile().getItemId() + " :)");
    }

    @Override
    public Set<Class<?>> getAdditionalOutputTypes() {
        final Set<Class<?>> types = new HashSet<>();

        types.add(MyEvent.class);
        types.add(SampleType.class);

        return types;
    }

    @Override
    public Set<Class<?>> getAdditionalInputTypes() {
        final Set<Class<?>> types = new HashSet<>();

        types.add(MyEventInput.class);
        types.add(MyEventFilterInput.class);
        types.add(VENDOR_PageViewEventInput.class);

        return types;
    }

    @Override
    public GraphqlFieldVisibility getGraphQLFieldVisibility() {
        // Blocks fields based on patterns
        return BlockedFields.newBlock()
                .addPattern("CDP_SegmentInput.name")
                .addPattern(".*\\.delete.*") // regular expressions allowed
                .build();
    }

    @Override
    public int getPriority() {
        return 0;
    }
}
