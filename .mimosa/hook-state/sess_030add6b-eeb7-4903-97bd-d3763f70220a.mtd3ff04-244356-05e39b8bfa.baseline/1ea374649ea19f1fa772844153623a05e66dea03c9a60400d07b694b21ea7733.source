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

import java.util.List;

@GraphQLName("CDP_ProfileFilterInput")
public class CDPProfileFilterInput {

    @GraphQLField
    private List<String> profileIDs_contains;

    @GraphQLField
    private List<String> segments_contains;

    @GraphQLField
    private List<String> consents_contains;

    @GraphQLField
    private List<String> lists_contains;

    @GraphQLField
    private CDPProfilePropertiesFilterInput properties;

    @GraphQLField
    private CDPInterestFilterInput interests;

    @GraphQLField
    private CDPProfileEventsFilterInput events;

    public CDPProfileFilterInput(final @GraphQLName("profileIDs_contains") List<String> profileIDs_contains,
                                 final @GraphQLName("segments_contains") List<String> segments_contains,
                                 final @GraphQLName("consents_contains") List<String> consents_contains,
                                 final @GraphQLName("lists_contains") List<String> lists_contains,
                                 final @GraphQLName("properties") CDPProfilePropertiesFilterInput properties,
                                 final @GraphQLName("interests") CDPInterestFilterInput interests,
                                 final @GraphQLName("events") CDPProfileEventsFilterInput events) {
        this.profileIDs_contains = profileIDs_contains;
        this.segments_contains = segments_contains;
        this.consents_contains = consents_contains;
        this.lists_contains = lists_contains;
        this.properties = properties;
        this.interests = interests;
        this.events = events;
    }

    public List<String> getProfileIDs_contains() {
        return profileIDs_contains;
    }

    public List<String> getSegments_contains() {
        return segments_contains;
    }

    public List<String> getConsents_contains() {
        return consents_contains;
    }

    public List<String> getLists_contains() {
        return lists_contains;
    }

    public CDPProfilePropertiesFilterInput getProperties() {
        return properties;
    }

    public CDPInterestFilterInput getInterests() {
        return interests;
    }

    public CDPProfileEventsFilterInput getEvents() {
        return events;
    }
}
