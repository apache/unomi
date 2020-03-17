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

@GraphQLName("CDP_EventOccurrenceFilter")
public class CDPEventOccurrenceFilterInput {

    @GraphQLField
    private String eventId;

    @GraphQLField
    private String beforeTime;

    @GraphQLField
    private String afterTime;

    @GraphQLField
    private String betweenTime;

    @GraphQLField
    private int count;

    public CDPEventOccurrenceFilterInput(
            final @GraphQLName("eventId") String eventId,
            final @GraphQLName("beforeTime") String beforeTime,
            final @GraphQLName("afterTime") String afterTime,
            final @GraphQLName("betweenTime") String betweenTime,
            final @GraphQLName("count") int count) {
        this.eventId = eventId;
        this.beforeTime = beforeTime;
        this.afterTime = afterTime;
        this.betweenTime = betweenTime;
        this.count = count;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getBeforeTime() {
        return beforeTime;
    }

    public void setBeforeTime(String beforeTime) {
        this.beforeTime = beforeTime;
    }

    public String getAfterTime() {
        return afterTime;
    }

    public void setAfterTime(String afterTime) {
        this.afterTime = afterTime;
    }

    public String getBetweenTime() {
        return betweenTime;
    }

    public void setBetweenTime(String betweenTime) {
        this.betweenTime = betweenTime;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

}
