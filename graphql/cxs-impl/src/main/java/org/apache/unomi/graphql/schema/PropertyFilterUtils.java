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
package org.apache.unomi.graphql.schema;

import graphql.Scalars;
import graphql.annotations.processor.GraphQLAnnotations;
import graphql.scalars.ExtendedScalars;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import org.apache.unomi.api.PropertyType;
import org.apache.unomi.graphql.scalars.DateTimeFunction;
import org.apache.unomi.graphql.scalars.GeoPointFunction;
import org.apache.unomi.graphql.types.input.CDPGeoDistanceFilterInput;
import org.apache.unomi.graphql.utils.ReflectionUtil;
import org.apache.unomi.graphql.utils.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class PropertyFilterUtils {

    public static List<GraphQLInputObjectField> buildInputPropertyFilters(final Collection<PropertyType> propertyTypes, final GraphQLAnnotations graphQLAnnotations) {
        if (propertyTypes == null || propertyTypes.isEmpty()) {
            return Collections.emptyList();
        }

        final List<GraphQLInputObjectField> fieldDefinitions = new ArrayList<>();

        propertyTypes.forEach(propertyType -> addFilters(fieldDefinitions, propertyType, graphQLAnnotations));

        return fieldDefinitions;
    }

    private static void addFilters(final List<GraphQLInputObjectField> fieldDefinitions, final PropertyType propertyType, final GraphQLAnnotations graphQLAnnotations) {
        final String propertyName = PropertyNameTranslator.translateFromUnomiToGraphQL(propertyType.getItemId());

        if ("integer".equals(propertyType.getValueTypeId())) {
            fieldDefinitions.add(GraphQLInputObjectField.newInputObjectField()
                    .name(propertyName + "_equals")
                    .type(Scalars.GraphQLInt)
                    .build());
            fieldDefinitions.add(GraphQLInputObjectField.newInputObjectField()
                    .name(propertyName + "_lt")
                    .type(Scalars.GraphQLInt)
                    .build());
            fieldDefinitions.add(GraphQLInputObjectField.newInputObjectField()
                    .name(propertyName + "_lte")
                    .type(Scalars.GraphQLInt)
                    .build());
            fieldDefinitions.add(GraphQLInputObjectField.newInputObjectField()
                    .name(propertyName + "_gt")
                    .type(Scalars.GraphQLInt)
                    .build());
            fieldDefinitions.add(GraphQLInputObjectField.newInputObjectField()
                    .name(propertyName + "_gte")
                    .type(Scalars.GraphQLInt)
                    .build());
        } else if ("long".equals(propertyType.getValueTypeId())) {
            fieldDefinitions.add(GraphQLInputObjectField.newInputObjectField()
                    .name(propertyName + "_equals")
                    .type(ExtendedScalars.GraphQLLong)
                    .build());
            fieldDefinitions.add(GraphQLInputObjectField.newInputObjectField()
                    .name(propertyName + "_lt")
                    .type(ExtendedScalars.GraphQLLong)
                    .build());
            fieldDefinitions.add(GraphQLInputObjectField.newInputObjectField()
                    .name(propertyName + "_lte")
                    .type(ExtendedScalars.GraphQLLong)
                    .build());
            fieldDefinitions.add(GraphQLInputObjectField.newInputObjectField()
                    .name(propertyName + "_gt")
                    .type(ExtendedScalars.GraphQLLong)
                    .build());
            fieldDefinitions.add(GraphQLInputObjectField.newInputObjectField()
                    .name(propertyName + "_gte")
                    .type(ExtendedScalars.GraphQLLong)
                    .build());
        } else if ("float".equals(propertyType.getValueTypeId())) {

            fieldDefinitions.add(GraphQLInputObjectField.newInputObjectField()
                    .name(propertyName + "_equals")
                    .type(Scalars.GraphQLFloat)
                    .build());
            fieldDefinitions.add(GraphQLInputObjectField.newInputObjectField()
                    .name(propertyName + "_lt")
                    .type(Scalars.GraphQLFloat)
                    .build());
            fieldDefinitions.add(GraphQLInputObjectField.newInputObjectField()
                    .name(propertyName + "_lte")
                    .type(Scalars.GraphQLFloat)
                    .build());
            fieldDefinitions.add(GraphQLInputObjectField.newInputObjectField()
                    .name(propertyName + "_gt")
                    .type(Scalars.GraphQLFloat)
                    .build());
            fieldDefinitions.add(GraphQLInputObjectField.newInputObjectField()
                    .name(propertyName + "_gte")
                    .type(Scalars.GraphQLFloat)
                    .build());
        } else if ("date".equals(propertyType.getValueTypeId())) {
            fieldDefinitions.add(GraphQLInputObjectField.newInputObjectField()
                    .name(propertyName + "_equals")
                    .type(ExtendedScalars.DateTime)
                    .build());
            fieldDefinitions.add(GraphQLInputObjectField.newInputObjectField()
                    .name(propertyName + "_lt")
                    .type(DateTimeFunction.DATE_TIME_SCALAR)
                    .build());
            fieldDefinitions.add(GraphQLInputObjectField.newInputObjectField()
                    .name(propertyName + "_lte")
                    .type(DateTimeFunction.DATE_TIME_SCALAR)
                    .build());
            fieldDefinitions.add(GraphQLInputObjectField.newInputObjectField()
                    .name(propertyName + "_gt")
                    .type(DateTimeFunction.DATE_TIME_SCALAR)
                    .build());
            fieldDefinitions.add(GraphQLInputObjectField.newInputObjectField()
                    .name(propertyName + "_gte")
                    .type(DateTimeFunction.DATE_TIME_SCALAR)
                    .build());
        } else if ("boolean".equals(propertyType.getValueTypeId())) {
            fieldDefinitions.add(GraphQLInputObjectField.newInputObjectField()
                    .name(propertyName + "_equals")
                    .type(Scalars.GraphQLBoolean)
                    .build());
        } else if ("id".equals(propertyType.getValueTypeId())) {
            fieldDefinitions.add(GraphQLInputObjectField.newInputObjectField()
                    .name(propertyName + "_equals")
                    .type(Scalars.GraphQLString)
                    .build());
        } else if ("set".equals(propertyType.getValueTypeId())) {
            if (propertyType.getChildPropertyTypes() != null && !propertyType.getChildPropertyTypes().isEmpty()) {
                final String typeName = StringUtils.capitalize(propertyName) + "FilterInput";

                GraphQLInputObjectType inputObjectType;
                if (!graphQLAnnotations.getContainer().getTypeRegistry().containsKey(typeName)) {
                    final GraphQLInputObjectType.Builder dynamicTypeBuilder = GraphQLInputObjectType.newInputObject()
                            .name(typeName);

                    final List<GraphQLInputObjectField> setFieldDefinitions = new ArrayList<>();

                    propertyType.getChildPropertyTypes().forEach(childPropertyType ->
                            addFilters(setFieldDefinitions, childPropertyType, graphQLAnnotations));

                    dynamicTypeBuilder.fields(setFieldDefinitions);

                    inputObjectType = dynamicTypeBuilder.build();

                    graphQLAnnotations.getContainer().getTypeRegistry().put(typeName, inputObjectType);
                } else {
                    inputObjectType = (GraphQLInputObjectType) graphQLAnnotations.getContainer().getTypeRegistry().get(typeName);
                }

                fieldDefinitions.add(GraphQLInputObjectField.newInputObjectField()
                        .name(propertyName)
                        .type(inputObjectType)
                        .build());
            }
        } else if ("geopoint".equals(propertyType.getValueTypeId())) {

            fieldDefinitions.add(GraphQLInputObjectField.newInputObjectField()
                    .name(propertyName + "_equals")
                    .type(GeoPointFunction.GEOPOINT_SCALAR)
                    .build());

            final String geoDistanceFilterTypeName = ReflectionUtil.resolveTypeName(CDPGeoDistanceFilterInput.class);
            final GraphQLInputType geoDistanceFilterType = (GraphQLInputObjectType) graphQLAnnotations.getContainer().getTypeRegistry().get(geoDistanceFilterTypeName);
            if (geoDistanceFilterType != null) {
                fieldDefinitions.add(GraphQLInputObjectField.newInputObjectField()
                        .name(propertyName + "_distance")
                        .type(geoDistanceFilterType)
                        .build());
            }
        } else {
            fieldDefinitions.add(GraphQLInputObjectField.newInputObjectField()
                    .name(propertyName + "_equals")
                    .type(Scalars.GraphQLString)
                    .build());
            fieldDefinitions.add(GraphQLInputObjectField.newInputObjectField()
                    .name(propertyName + "_startsWith")
                    .type(Scalars.GraphQLString)
                    .build());
            fieldDefinitions.add(GraphQLInputObjectField.newInputObjectField()
                    .name(propertyName + "_endsWith")
                    .type(Scalars.GraphQLString)
                    .build());
            fieldDefinitions.add(GraphQLInputObjectField.newInputObjectField()
                    .name(propertyName + "_contains")
                    .type(Scalars.GraphQLString)
                    .build());
            fieldDefinitions.add(GraphQLInputObjectField.newInputObjectField()
                    .name(propertyName + "_regexp")
                    .type(Scalars.GraphQLString)
                    .build());
        }
    }

}
