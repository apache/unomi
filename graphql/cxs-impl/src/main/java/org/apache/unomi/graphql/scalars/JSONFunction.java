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
import graphql.scalars.object.JsonScalar;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLType;

import java.lang.reflect.AnnotatedType;

public class JSONFunction implements TypeFunction {

    public static final GraphQLScalarType JSON_SCALAR = ExtendedScalars.Json;

    @Override
    public String getTypeName(Class<?> aClass, AnnotatedType annotatedType) {
        return JSON_SCALAR.getName();
    }

    @Override
    public boolean canBuildType(Class<?> aClass, AnnotatedType annotatedType) {
        return aClass == Object.class;
    }

    @Override
    public GraphQLType buildType(boolean input, Class<?> aClass, AnnotatedType annotatedType, ProcessingElementsContainer container) {
        return JSON_SCALAR;
    }

}
