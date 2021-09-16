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

package org.apache.unomi.graphql.providers;

import com.google.common.collect.Lists;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLInputFieldsContainer;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLNamedSchemaElement;
import graphql.schema.visibility.GraphqlFieldVisibility;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class CompositeGraphQLFieldVisibility implements GraphqlFieldVisibility {

    private final List<GraphQLFieldVisibilityProvider> providers;

    public CompositeGraphQLFieldVisibility(List<GraphQLFieldVisibilityProvider> providers) {
        this.providers = providers;
        if (providers != null && !providers.isEmpty()) {
            providers.sort(Comparator.comparingInt(GraphQLFieldVisibilityProvider::getPriority).reversed());
        }
    }

    @Override
    public List<GraphQLFieldDefinition> getFieldDefinitions(GraphQLFieldsContainer fieldsContainer) {
        if (providers == null) {
            return Lists.newArrayList();
        }
        return providers.stream().
                map(provider -> provider.getGraphQLFieldVisibility().getFieldDefinitions(fieldsContainer)).
                reduce(CompositeGraphQLFieldVisibility::intersect).
                orElse(Lists.newArrayList());
    }

    @Override
    public GraphQLFieldDefinition getFieldDefinition(GraphQLFieldsContainer fieldsContainer, String fieldName) {
        if (providers == null) {
            return null;
        }
        List<GraphQLFieldDefinition> list = providers.stream().
                map(provider -> provider.getGraphQLFieldVisibility().getFieldDefinition(fieldsContainer, fieldName)).
                collect(Collectors.toList());

        return extractWithPriority(list);
    }

    @Override
    public List<GraphQLInputObjectField> getFieldDefinitions(GraphQLInputFieldsContainer fieldsContainer) {
        if (providers == null) {
            return Lists.newArrayList();
        }
        return providers.stream().
                map(provider -> provider.getGraphQLFieldVisibility().getFieldDefinitions(fieldsContainer)).
                reduce(CompositeGraphQLFieldVisibility::intersect).
                orElse(Lists.newArrayList());
    }

    @Override
    public GraphQLInputObjectField getFieldDefinition(GraphQLInputFieldsContainer fieldsContainer, String fieldName) {
        if (providers == null) {
            return null;
        }
        List<GraphQLInputObjectField> list = providers.stream().
                map(provider -> provider.getGraphQLFieldVisibility().getFieldDefinition(fieldsContainer, fieldName)).
                collect(Collectors.toList());

        return extractWithPriority(list);
    }

    private static <T extends GraphQLNamedSchemaElement> List<T> intersect(List<T> prev, List<T> curr) {
        return curr.
                stream().
                distinct().
                filter(prev::contains).
                collect(Collectors.toList());
    }

    private static <T> T extractWithPriority(List<T> list) {
        boolean anyBlocks = list.stream().anyMatch(Objects::isNull);
        boolean noItems = list.size() == 0;
        if (anyBlocks || noItems) {
            // some provider blocks it or none describes at all
            return null;
        } else {
            // return first as they are sorted by priority
            return list.get(0);
        }
    }
}
