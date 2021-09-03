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
import graphql.scalars.ExtendedScalars;
import graphql.scalars.datetime.DateTimeScalar;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLType;

import java.lang.reflect.AnnotatedType;
import java.time.OffsetDateTime;

public class DateTimeFunction implements TypeFunction {

    public static final GraphQLScalarType DATE_TIME_SCALAR = ExtendedScalars.DateTime;

    @Override
    public String getTypeName(Class<?> aClass, AnnotatedType annotatedType) {
        return ExtendedScalars.DateTime.getName();
    }

    @Override
    public boolean canBuildType(Class<?> aClass, AnnotatedType annotatedType) {
        return aClass == OffsetDateTime.class;
    }

    @Override
    public GraphQLType buildType(boolean input, Class<?> aClass, AnnotatedType annotatedType, ProcessingElementsContainer container) {
        return ExtendedScalars.DateTime;
    }

}
