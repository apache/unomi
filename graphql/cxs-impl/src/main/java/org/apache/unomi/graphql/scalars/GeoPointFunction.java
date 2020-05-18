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
package org.apache.unomi.graphql.scalars;

import graphql.annotations.processor.ProcessingElementsContainer;
import graphql.annotations.processor.typeFunctions.TypeFunction;
import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLType;
import org.apache.unomi.api.GeoPoint;

import java.lang.reflect.AnnotatedType;

import static graphql.scalars.util.Kit.typeName;

public class GeoPointFunction implements TypeFunction {

    @Override
    public String getTypeName(Class<?> aClass, AnnotatedType annotatedType) {
        return GEOPOINT_SCALAR.getName();
    }

    @Override
    public boolean canBuildType(Class<?> aClass, AnnotatedType annotatedType) {
        return aClass == GeoPoint.class;
    }

    @Override
    public GraphQLType buildType(boolean input, Class<?> aClass, AnnotatedType annotatedType, ProcessingElementsContainer container) {
        return GEOPOINT_SCALAR;
    }

    public static GraphQLScalarType GEOPOINT_SCALAR = GraphQLScalarType.newScalar()
            .name("GeoPoint")
            .description("GeoPoint scalar type: \"lat,long\"")
            .coercing(new Coercing<GeoPoint, String>() {

                @Override
                public String serialize(Object dataFetcherResult) throws CoercingSerializeException {
                    if (dataFetcherResult instanceof GeoPoint) {
                        return ((GeoPoint) dataFetcherResult).asString();
                    } else if (dataFetcherResult != null) {
                        return dataFetcherResult.toString();
                    }
                    return null;
                }

                @Override
                public GeoPoint parseValue(Object input) throws CoercingParseValueException {
                    final GeoPoint result = convertImpl(input);
                    if (result == null) {
                        throw new CoercingParseValueException(
                                "Expected type 'GeoPoint' but was '" + typeName(input) + "'."
                        );
                    }
                    return result;
                }

                @Override
                public GeoPoint parseLiteral(Object input) {
                    final GeoPoint result = convertImpl(input);
                    if (result == null) {
                        throw new CoercingParseLiteralException(
                                "Expected AST type 'GeoPoint' but was '" + typeName(input) + "'."
                        );
                    }
                    return result;
                }

                private GeoPoint convertImpl(Object input) {
                    if (input instanceof GeoPoint) {
                        return (GeoPoint) input;
                    } else if (input instanceof StringValue) {
                        return GeoPoint.fromString(((StringValue) input).getValue());
                    } else if (input instanceof String) {
                        return GeoPoint.fromString((String) input);
                    }
                    return null;
                }
            })
            .build();

}
