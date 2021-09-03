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
import graphql.scalars.ExtendedScalars;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLType;
import org.apache.unomi.graphql.scalars.DateFunction;
import org.apache.unomi.graphql.scalars.DateTimeFunction;

public class PropertyValueTypeHelper {

    public static String getPropertyValueParameter(
            final String typeName, final String fieldName, final DataFetchingEnvironment environment) {
        final GraphQLObjectType objectType = environment.getGraphQLSchema().getObjectType(typeName);

        final GraphQLOutputType fieldType = objectType.getFieldDefinition(fieldName).getType();

        return getPropertyValueParameter(fieldType);
    }

    public static String getPropertyValueParameterForInputType(
            final String typeName, final String fieldName, final DataFetchingEnvironment environment) {
        final GraphQLInputObjectType objectType = (GraphQLInputObjectType) environment.getGraphQLSchema().getType(typeName);

        final GraphQLInputType fieldType = objectType.getFieldDefinition(fieldName).getType();

        return getPropertyValueParameter(fieldType);
    }

    public static String getPropertyValueParameter(final GraphQLType fieldType) {
        if (!(fieldType instanceof GraphQLScalarType)) {
            return "propertyValue";
        }

        final GraphQLScalarType scalarType = (GraphQLScalarType) fieldType;

        if (Scalars.GraphQLFloat.getName().equals(scalarType.getName())
                || Scalars.GraphQLInt.getName().equals(scalarType.getName())
                || ExtendedScalars.GraphQLLong.getName().equals(scalarType.getName())
                || Scalars.GraphQLFloat.getName().equals(scalarType.getName())
                || ExtendedScalars.GraphQLBigDecimal.getName().equals(scalarType.getName())
                || ExtendedScalars.GraphQLBigInteger.getName().equals(scalarType.getName())) {
            return "propertyValueInteger";
        } else if (DateTimeFunction.DATE_TIME_SCALAR.getName().equals(scalarType.getName())
                || DateFunction.DATE_SCALAR.getName().equals(scalarType.getName())) {
            return "propertyValueDate";
        } else {
            return "propertyValue";
        }
    }

}
