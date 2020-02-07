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
package org.apache.unomi.graphql.types.input;

import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;

import java.util.ArrayList;
import java.util.List;

@GraphQLName("CDP_EventFilter")
public class CDPEventFilterInput {

    @GraphQLField
    public List<CDPEventFilterInput> and = new ArrayList<>();

    @GraphQLField
    public List<CDPEventFilterInput> or = new ArrayList<>();

    @GraphQLField
    public String id_equals;

    @GraphQLField
    public String cdp_clientID_equals;

    @GraphQLField
    public String cdp_sourceID_equals;

    @GraphQLField
    public String cdp_profileID_equals;

}
