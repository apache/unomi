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

@GraphQLName("CDP_InterestFilterInput")
public class CDPInterestFilterInput {

    @GraphQLField
    private List<CDPInterestFilterInput> and = new ArrayList<>();

    @GraphQLField
    private List<CDPInterestFilterInput> or = new ArrayList<>();

    @GraphQLField
    private String topic_equals;

    @GraphQLField
    private Float score_equals;

    @GraphQLField
    private Float score_lt;

    @GraphQLField
    private Float score_lte;

    @GraphQLField
    private Float score_gt;

    @GraphQLField
    private Float score_gte;

//    public CDPInterestFilterInput() {
//    }

    public CDPInterestFilterInput(@GraphQLName("and") List<CDPInterestFilterInput> and,
                                  @GraphQLName("or") List<CDPInterestFilterInput> or,
                                  @GraphQLName("topic_equals") String topic_equals,
                                  @GraphQLName("score_equals") Float score_equals,
                                  @GraphQLName("score_lt") Float score_lt,
                                  @GraphQLName("score_lte") Float score_lte,
                                  @GraphQLName("score_gt") Float score_gt,
                                  @GraphQLName("score_gte") Float score_gte) {
        this.and = and;
        this.or = or;
        this.topic_equals = topic_equals;
        this.score_equals = score_equals;
        this.score_lt = score_lt;
        this.score_lte = score_lte;
        this.score_gt = score_gt;
        this.score_gte = score_gte;
    }

    public List<CDPInterestFilterInput> getAnd() {
        return and;
    }

    public CDPInterestFilterInput setAnd(List<CDPInterestFilterInput> and) {
        this.and = and;
        return this;
    }

    public List<CDPInterestFilterInput> getOr() {
        return or;
    }

    public CDPInterestFilterInput setOr(List<CDPInterestFilterInput> or) {
        this.or = or;
        return this;
    }

    public String getTopic_equals() {
        return topic_equals;
    }

    public CDPInterestFilterInput setTopic_equals(String topic_equals) {
        this.topic_equals = topic_equals;
        return this;
    }

    public Float getScore_equals() {
        return score_equals;
    }

    public CDPInterestFilterInput setScore_equals(Float score_equals) {
        this.score_equals = score_equals;
        return this;
    }

    public Float getScore_lt() {
        return score_lt;
    }

    public CDPInterestFilterInput setScore_lt(Float score_lt) {
        this.score_lt = score_lt;
        return this;
    }

    public Float getScore_lte() {
        return score_lte;
    }

    public CDPInterestFilterInput setScore_lte(Float score_lte) {
        this.score_lte = score_lte;
        return this;
    }

    public Float getScore_gt() {
        return score_gt;
    }

    public CDPInterestFilterInput setScore_gt(Float score_gt) {
        this.score_gt = score_gt;
        return this;
    }

    public Float getScore_gte() {
        return score_gte;
    }

    public CDPInterestFilterInput setScore_gte(Float score_gte) {
        this.score_gte = score_gte;
        return this;
    }
}
