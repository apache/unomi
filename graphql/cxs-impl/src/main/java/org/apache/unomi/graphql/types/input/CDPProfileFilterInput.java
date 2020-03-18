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

@GraphQLName("CDP_ProfileFilter")
public class CDPProfileFilterInput {

    @GraphQLField
    private List<String> profileIDs_contains = new ArrayList<>();

    @GraphQLField
    private List<String> segments_contains = new ArrayList<>();

    @GraphQLField
    private List<String> consents_contains = new ArrayList<>();

    @GraphQLField
    private List<String> lists_contains = new ArrayList<>();

    @GraphQLField
    private CDPProfilePropertiesFilterInput properties;

    @GraphQLField
    private CDPInterestFilterInput interests;

    @GraphQLField
    private CDPProfileEventsFilterInput events;

    public CDPProfileFilterInput(@GraphQLName("profileIDs_contains") List<String> profileIDs_contains,
                                 @GraphQLName("segments_contains") List<String> segments_contains,
                                 @GraphQLName("consents_contains") List<String> consents_contains,
                                 @GraphQLName("lists_contains") List<String> lists_contains,
                                 @GraphQLName("properties") CDPProfilePropertiesFilterInput properties,
                                 @GraphQLName("interests") CDPInterestFilterInput interests,
                                 @GraphQLName("events") CDPProfileEventsFilterInput events) {
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

    public void setProfileIDs_contains(List<String> profileIDs_contains) {
        this.profileIDs_contains = profileIDs_contains;
    }

    public List<String> getSegments_contains() {
        return segments_contains;
    }

    public void setSegments_contains(List<String> segments_contains) {
        this.segments_contains = segments_contains;
    }

    public List<String> getConsents_contains() {
        return consents_contains;
    }

    public void setConsents_contains(List<String> consents_contains) {
        this.consents_contains = consents_contains;
    }

    public List<String> getLists_contains() {
        return lists_contains;
    }

    public void setLists_contains(List<String> lists_contains) {
        this.lists_contains = lists_contains;
    }

    public CDPProfilePropertiesFilterInput getProperties() {
        return properties;
    }

    public void setProperties(CDPProfilePropertiesFilterInput properties) {
        this.properties = properties;
    }

    public CDPInterestFilterInput getInterests() {
        return interests;
    }

    public void setInterests(CDPInterestFilterInput interests) {
        this.interests = interests;
    }

    public CDPProfileEventsFilterInput getEvents() {
        return events;
    }

    public void setEvents(CDPProfileEventsFilterInput events) {
        this.events = events;
    }
}
