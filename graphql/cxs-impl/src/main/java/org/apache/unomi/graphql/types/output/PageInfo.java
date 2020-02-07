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

import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;

@GraphQLName("CDP_PageInfo")
// TODO clarify what name should be used. we catch the following exception:
// graphql.AssertException: All types within a GraphQL schema must have unique names. No two provided types may have the same name.
//        No provided type may have a name which conflicts with any built in types (including Scalar and Introspection types).
//        You have redefined the type 'PageInfo' from being a 'GraphQLObjectType' to a 'GraphQLObjectType'
public class PageInfo {

    @GraphQLField
    public boolean hasPreviousPage;
    @GraphQLField
    public boolean hasNextPage;

}
