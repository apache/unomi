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
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputObjectField;
import org.apache.unomi.api.PropertyType;
import org.apache.unomi.graphql.function.DateTimeFunction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class PropertyFilterUtils {

    public static List<GraphQLInputObjectField> buildInputPropertyFilters(final Collection<PropertyType> propertyTypes) {
        if (propertyTypes == null || propertyTypes.isEmpty()) {
            return Collections.emptyList();
        }

        final List<GraphQLInputObjectField> fieldDefinitions = new ArrayList<>();

        propertyTypes.forEach(propertyType -> {
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
                        .type(DateTimeFunction.DATE_TIME_SCALAR)
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
            } else if ("identifier".equals(propertyType.getValueTypeId())) {
                fieldDefinitions.add(GraphQLInputObjectField.newInputObjectField()
                        .name(propertyName + "_equals")
                        .type(Scalars.GraphQLString)
                        .build());
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
        });

        return fieldDefinitions;
    }

    public static List<GraphQLFieldDefinition> buildOutputPropertyFilters(final Collection<PropertyType> propertyTypes) {
        if (propertyTypes == null || propertyTypes.isEmpty()) {
            return Collections.emptyList();
        }

        final List<GraphQLFieldDefinition> fieldDefinitions = new ArrayList<>();


        propertyTypes.forEach(propertyType -> {
            final String propertyName = PropertyNameTranslator.translateFromUnomiToGraphQL(propertyType.getItemId());

            if ("integer".equals(propertyType.getValueTypeId())) {

                fieldDefinitions.add(GraphQLFieldDefinition.newFieldDefinition()
                        .name(propertyName + "_equals")
                        .type(Scalars.GraphQLInt)
                        .build());
                fieldDefinitions.add(GraphQLFieldDefinition.newFieldDefinition()
                        .name(propertyName + "_lt")
                        .type(Scalars.GraphQLInt)
                        .build());
                fieldDefinitions.add(GraphQLFieldDefinition.newFieldDefinition()
                        .name(propertyName + "_lte")
                        .type(Scalars.GraphQLInt)
                        .build());
                fieldDefinitions.add(GraphQLFieldDefinition.newFieldDefinition()
                        .name(propertyName + "_gt")
                        .type(Scalars.GraphQLInt)
                        .build());
                fieldDefinitions.add(GraphQLFieldDefinition.newFieldDefinition()
                        .name(propertyName + "_gte")
                        .type(Scalars.GraphQLInt)
                        .build());
            } else if ("float".equals(propertyType.getValueTypeId())) {

                fieldDefinitions.add(GraphQLFieldDefinition.newFieldDefinition()
                        .name(propertyName + "_equals")
                        .type(Scalars.GraphQLFloat)
                        .build());
                fieldDefinitions.add(GraphQLFieldDefinition.newFieldDefinition()
                        .name(propertyName + "_lt")
                        .type(Scalars.GraphQLFloat)
                        .build());
                fieldDefinitions.add(GraphQLFieldDefinition.newFieldDefinition()
                        .name(propertyName + "_lte")
                        .type(Scalars.GraphQLFloat)
                        .build());
                fieldDefinitions.add(GraphQLFieldDefinition.newFieldDefinition()
                        .name(propertyName + "_gt")
                        .type(Scalars.GraphQLFloat)
                        .build());
                fieldDefinitions.add(GraphQLFieldDefinition.newFieldDefinition()
                        .name(propertyName + "_gte")
                        .type(Scalars.GraphQLFloat)
                        .build());
            } else if ("date".equals(propertyType.getValueTypeId())) {

                fieldDefinitions.add(GraphQLFieldDefinition.newFieldDefinition()
                        .name(propertyName + "_equals")
                        .type(DateTimeFunction.DATE_TIME_SCALAR)
                        .build());
                fieldDefinitions.add(GraphQLFieldDefinition.newFieldDefinition()
                        .name(propertyName + "_lt")
                        .type(DateTimeFunction.DATE_TIME_SCALAR)
                        .build());
                fieldDefinitions.add(GraphQLFieldDefinition.newFieldDefinition()
                        .name(propertyName + "_lte")
                        .type(DateTimeFunction.DATE_TIME_SCALAR)
                        .build());
                fieldDefinitions.add(GraphQLFieldDefinition.newFieldDefinition()
                        .name(propertyName + "_gt")
                        .type(DateTimeFunction.DATE_TIME_SCALAR)
                        .build());
                fieldDefinitions.add(GraphQLFieldDefinition.newFieldDefinition()
                        .name(propertyName + "_gte")
                        .type(DateTimeFunction.DATE_TIME_SCALAR)
                        .build());
            } else if ("boolean".equals(propertyType.getValueTypeId())) {
                fieldDefinitions.add(GraphQLFieldDefinition.newFieldDefinition()
                        .name(propertyName + "_equals")
                        .type(Scalars.GraphQLBoolean)
                        .build());
            } else if ("identifier".equals(propertyType.getValueTypeId())) {
                fieldDefinitions.add(GraphQLFieldDefinition.newFieldDefinition()
                        .name(propertyName + "_equals")
                        .type(Scalars.GraphQLString)
                        .build());
            } else {
                fieldDefinitions.add(GraphQLFieldDefinition.newFieldDefinition()
                        .name(propertyName + "_equals")
                        .type(Scalars.GraphQLString)
                        .build());
                fieldDefinitions.add(GraphQLFieldDefinition.newFieldDefinition()
                        .name(propertyName + "_startsWith")
                        .type(Scalars.GraphQLString)
                        .build());
                fieldDefinitions.add(GraphQLFieldDefinition.newFieldDefinition()
                        .name(propertyName + "_endsWith")
                        .type(Scalars.GraphQLString)
                        .build());
                fieldDefinitions.add(GraphQLFieldDefinition.newFieldDefinition()
                        .name(propertyName + "_contains")
                        .type(Scalars.GraphQLString)
                        .build());
                fieldDefinitions.add(GraphQLFieldDefinition.newFieldDefinition()
                        .name(propertyName + "_regexp")
                        .type(Scalars.GraphQLString)
                        .build());
            }
        });


        return fieldDefinitions;
    }

}
