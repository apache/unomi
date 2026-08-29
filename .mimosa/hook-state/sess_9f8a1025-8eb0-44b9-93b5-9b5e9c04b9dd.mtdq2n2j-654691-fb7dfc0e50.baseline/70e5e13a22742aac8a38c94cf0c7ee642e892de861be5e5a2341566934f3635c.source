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

package org.apache.unomi.graphql.types.output;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLID;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import graphql.annotations.annotationTypes.GraphQLTypeResolver;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.PropertyType;
import org.apache.unomi.graphql.types.resolvers.CDPPropertyInterfaceResolver;

import java.util.Set;

import static org.apache.unomi.graphql.types.output.CDPPropertyInterface.TYPE_NAME;

@GraphQLName(TYPE_NAME)
@GraphQLTypeResolver(CDPPropertyInterfaceResolver.class)
@GraphQLDescription("The property interface defines the common fields for the different value types.")
public interface CDPPropertyInterface {

    String TYPE_NAME = "CDP_PropertyInterface";

    PropertyType getType();

    @GraphQLID
    @GraphQLNonNull
    @GraphQLField
    default String name() {
        final PropertyType type = getType();
        if (type == null) {
            return null;
        }
        final Metadata meta = type.getMetadata();
        return meta != null ? meta.getName() : null;
    }

    @GraphQLField
    default Integer minOccurrences() {
        return 0;
    }

    @GraphQLField
    default Integer maxOccurrences() {
        // TODO: fix min/max occurrences after unomi supports it
        final PropertyType type = getType();
        if (type == null) {
            return null;
        }
        // Using 0-1 for regular field and 0-1000 for multivalued
        final boolean multi = type.isMultivalued() != null && type.isMultivalued();
        return multi ? 1000 : 1;
    }

    @GraphQLField
    default Set<String> tags() {
        final PropertyType type = getType();
        if (type == null) {
            return null;
        }
        final Metadata meta = type.getMetadata();
        return meta != null ? meta.getTags() : null;
    }
}
