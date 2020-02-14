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
import graphql.schema.*;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.PropertyType;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.graphql.fetchers.CustomerPropertyDataFetcher;
import org.apache.unomi.graphql.types.CDP_Profile;
import org.apache.unomi.graphql.types.input.CDPPropertyTypeInput;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.*;

import static graphql.Scalars.*;

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
        this.graphQL = GraphQL.newGraphQL(createGraphQLSchema(new GraphQLAnnotations())).build();
    }

    public void updateSchema(final List<CDPPropertyTypeInput> properties) {
        if (properties == null || properties.isEmpty()) {
            return;
        }

        final GraphQLAnnotations graphQLAnnotations = new GraphQLAnnotations();

        properties.forEach(property -> {
            final PropertyType propertyType = new PropertyType();

            propertyType.setTarget("profiles");
            propertyType.setItemId(property.stringPropertyTypeInput.getName());
            propertyType.setValueTypeId(convert(property));

            final Metadata metadata = new Metadata();

            metadata.setId(property.stringPropertyTypeInput.getName());
            metadata.setName(property.stringPropertyTypeInput.getName());

            final Set<String> systemTags = new HashSet<>();
            systemTags.add("profileProperties");
            systemTags.add("properties");
            systemTags.add("systemProfileProperties");
            metadata.setSystemTags(systemTags);

            propertyType.setMetadata(metadata);


            profileService.setPropertyType(propertyType);
        });

        this.graphQL = GraphQL.newGraphQL(createGraphQLSchema(graphQLAnnotations)).build();
    }

    public GraphQL getGraphQL() {
        return graphQL;
    }

    private GraphQLSchema createGraphQLSchema(final GraphQLAnnotations graphQLAnnotations) {
        final ProcessingElementsContainer container = graphQLAnnotations.getContainer();

        container.setInputPrefix("");
        container.setInputSuffix("Input");

        registerDynamicFields(graphQLAnnotations, "profiles", "CDP_Profile");

        return AnnotationsSchemaCreator.newAnnotationsSchema()
                .query(RootQuery.class)
                .mutation(RootMutation.class)
                .setAnnotationsProcessor(graphQLAnnotations)
                .build();
    }


    private void registerDynamicFields(
            final GraphQLAnnotations graphQLAnnotations, final String target, final String graphQLTypeName) {
        final Collection<PropertyType> propertyTypes = profileService.getTargetPropertyTypes(target);

        final List<GraphQLFieldDefinition> fieldDefinitions = new ArrayList<>();

        final GraphQLCodeRegistry.Builder codeRegisterBuilder = graphQLAnnotations.getContainer().getCodeRegistryBuilder();

        propertyTypes.forEach(propertyType -> {
            final GraphQLFieldDefinition.Builder fieldBuilder = GraphQLFieldDefinition.newFieldDefinition();

            fieldBuilder.type(convert(propertyType.getValueTypeId()));
            fieldBuilder.name(propertyType.getItemId());

            fieldDefinitions.add(fieldBuilder.build());

            codeRegisterBuilder.dataFetcher(
                    FieldCoordinates.coordinates(graphQLTypeName, propertyType.getItemId()),
                    new CustomerPropertyDataFetcher(propertyType.getItemId()));
        });

        final GraphQLObjectType transformedCdpProfileType = graphQLAnnotations.object(CDP_Profile.class)
                .transform(builder -> fieldDefinitions.forEach(builder::field));

        graphQLAnnotations.getContainer().getTypeRegistry().put(graphQLTypeName, transformedCdpProfileType);
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

    private String convert(final CDPPropertyTypeInput propertyTypeInput) {
        if (propertyTypeInput.integerPropertyTypeInput != null) {
            return "integer";
        } else if (propertyTypeInput.identifierPropertyTypeInput != null) {
            return "string"; // TODO
        } else if (propertyTypeInput.booleanPropertyTypeInput != null) {
            return "boolean";
        } else if (propertyTypeInput.datePropertyTypeInput != null) {
            return "date";
        } else if (propertyTypeInput.floatPropertyTypeInput != null) {
            return "float";
        } else if (propertyTypeInput.setPropertyTypeInput != null) {
            return "set";
        } else {
            return "string";
        }

    }

}
