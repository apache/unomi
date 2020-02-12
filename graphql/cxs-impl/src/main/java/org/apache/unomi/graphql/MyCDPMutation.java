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
package org.apache.unomi.graphql;

import graphql.Scalars;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.processor.GraphQLAnnotations;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import org.apache.unomi.graphql.types.CDP_Profile;
import org.apache.unomi.graphql.types.input.CDPPropertyTypeInput;

import java.util.List;

@GraphQLName("MyCDP_Mutation")
public class MyCDPMutation {

    @GraphQLField
    public boolean createOrUpdateProfileProperties(
            final @GraphQLName("properties") List<CDPPropertyTypeInput> properties) {

        if (properties == null || properties.isEmpty()) {
            return false;
        }

        final GraphQLAnnotations graphQLAnnotations = new GraphQLAnnotations();

        final GraphQLFieldDefinition newField = GraphQLFieldDefinition.newFieldDefinition()
                .name(properties.get(0).stringPropertyTypeInput.getName())
                .type(Scalars.GraphQLString)
                .build();

        final GraphQLObjectType cdpProfileType = graphQLAnnotations.object(CDP_Profile.class).transform(builder -> builder.field(newField));

        graphQLAnnotations.getContainer().getTypeRegistry().put("CDP_Profile", cdpProfileType);

//        container.getCodeRegistryBuilder()
//                .dataFetcher(FieldCoordinates.coordinates("CDP_Profile", "newField"),
//                        (DataFetcher<String>) environment -> "I'm a new field");


        return true;
    }


}
