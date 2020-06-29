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
package org.apache.unomi.graphql.utils;

import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.processor.util.NamingKit;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class ReflectionUtil {

    private static final String UNOMI_TYPE_FIELD = "UNOMI_TYPE";

    public static String getStaticField(final Class clazz, final String field) {
        try {
            return (String) clazz.getField(field).get(null);
        } catch (final NoSuchFieldException e) {
            throw new RuntimeException(String.format("Class %s doesn't have a publicly accessible \"TYPE_NAME\" field", clazz.getName()), e);
        } catch (final IllegalAccessException e) {
            throw new RuntimeException(String.format("Error resolving \"TYPE_NAME\" for class %s", clazz.getName()), e);
        }
    }

    public static String getUnomiType(final Class clazz) {
        return getStaticField(clazz, UNOMI_TYPE_FIELD);
    }

    public static String resolveTypeName(final Class<?> annotatedClass) {
        final GraphQLName graphQLName = annotatedClass.getAnnotation(GraphQLName.class);

        return NamingKit.toGraphqlName(graphQLName != null ? graphQLName.value() : annotatedClass.getSimpleName());
    }

    public static String resolveFieldName(final Field annotatedField) {
        final GraphQLName graphQLName = annotatedField.getAnnotation(GraphQLName.class);

        return NamingKit.toGraphqlName(graphQLName != null ? graphQLName.value() : annotatedField.getName());
    }

    public static List<String> getNonDynamicFields(final Class<?> annotatedClass) {
        final Field[] declaredFields = annotatedClass.getDeclaredFields();

        final List<String> result = new ArrayList<>();

        for (final Field field : declaredFields) {
            result.add(resolveFieldName(field));
        }

        return result;
    }

}
