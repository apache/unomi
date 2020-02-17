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

import graphql.GraphQL;
import graphql.annotations.AnnotationsSchemaCreator;
import graphql.annotations.processor.GraphQLAnnotations;
import graphql.annotations.processor.ProcessingElementsContainer;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLSchema;
import org.apache.unomi.api.PropertyType;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.graphql.fetchers.CustomerPropertyDataFetcher;
import org.apache.unomi.graphql.types.RootMutation;
import org.apache.unomi.graphql.types.RootQuery;
import org.apache.unomi.graphql.types.output.CDPProfile;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static graphql.Scalars.GraphQLBoolean;
import static graphql.Scalars.GraphQLFloat;
import static graphql.Scalars.GraphQLInt;
import static graphql.Scalars.GraphQLString;

@Component(service = GraphQLSchemaUpdater.class)
public class GraphQLSchemaUpdater {

    private GraphQL graphQL;

    private ProfileService profileService;

    @Reference
    public void setProfileService(ProfileService profileService) {
        this.profileService = profileService;
    }

    @Activate
    public void activate() {
        updateSchema();
    }

    public void updateSchema() {
        final GraphQLAnnotations graphQLAnnotations = new GraphQLAnnotations();

        this.graphQL = GraphQL.newGraphQL(createGraphQLSchema(graphQLAnnotations)).build();
    }

    public GraphQL getGraphQL() {
        return graphQL;
    }

    private GraphQLSchema createGraphQLSchema(final GraphQLAnnotations graphQLAnnotations) {
        final ProcessingElementsContainer container = graphQLAnnotations.getContainer();

        container.setInputPrefix("");
        container.setInputSuffix("Input");

        registerDynamicFields(graphQLAnnotations, "profiles", "CDP_Profile", CDPProfile.class);

        return AnnotationsSchemaCreator.newAnnotationsSchema()
                .query(RootQuery.class)
                .mutation(RootMutation.class)
                .setAnnotationsProcessor(graphQLAnnotations)
                .build();
    }

    private void registerDynamicFields(
            final GraphQLAnnotations graphQLAnnotations, final String target, final String graphQLTypeName, final Class<?> clazz) {
        final Collection<PropertyType> propertyTypes = profileService.getTargetPropertyTypes(target);

        final GraphQLCodeRegistry.Builder codeRegisterBuilder = graphQLAnnotations.getContainer().getCodeRegistryBuilder();

        final List<GraphQLFieldDefinition> fieldDefinitions = propertyTypes.stream().map(propertyType -> {
            final GraphQLFieldDefinition.Builder fieldBuilder = GraphQLFieldDefinition.newFieldDefinition();

            fieldBuilder.type(convert(propertyType.getValueTypeId()));
            fieldBuilder.name(propertyType.getItemId());

            codeRegisterBuilder.dataFetcher(
                    FieldCoordinates.coordinates(graphQLTypeName, propertyType.getItemId()),
                    new CustomerPropertyDataFetcher(propertyType.getItemId()));

            return fieldBuilder.build();
        }).collect(Collectors.toList());

        final GraphQLObjectType transformedObjectType = graphQLAnnotations.object(clazz)
                .transform(builder -> fieldDefinitions.forEach(builder::field));

        graphQLAnnotations.getContainer().getTypeRegistry().put(graphQLTypeName, transformedObjectType);
    }

    private GraphQLOutputType convert(final String type) {
        switch (type) {
            case "integer":
                return GraphQLInt;
            case "float":
                return GraphQLFloat;
            case "set":
                return null; // TODO
            case "boolean":
                return GraphQLBoolean;
            default: {
                return GraphQLString;
            }
        }
    }

}
