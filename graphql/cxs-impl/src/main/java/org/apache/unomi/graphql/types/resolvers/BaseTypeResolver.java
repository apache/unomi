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
package org.apache.unomi.graphql.types.resolvers;

import graphql.TypeResolutionEnvironment;
import graphql.schema.GraphQLObjectType;
import graphql.schema.TypeResolver;

public class BaseTypeResolver implements TypeResolver {

    private static final String TYPE_NAME_FIELD = "TYPE_NAME";

    @Override
    public GraphQLObjectType getType(final TypeResolutionEnvironment env) {
        final Class clazz = env.getObject().getClass();

        try {
            final String typeName = (String) clazz.getField(TYPE_NAME_FIELD).get(null);

            return env.getSchema().getObjectType(typeName);
        } catch (final NoSuchFieldException e) {
            throw new RuntimeException(String.format("Class %s doesn't have a publicly accessible \"TYPE_NAME\" field", clazz.getName()), e);
        } catch (final IllegalAccessException e) {
            throw new RuntimeException(String.format("Error resolving \"TYPE_NAME\" for class %s", clazz.getName()), e);
        }
    }

}
