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
    private Double score_equals;

    @GraphQLField
    private Double score_lt;

    @GraphQLField
    private Double score_lte;

    @GraphQLField
    private Double score_gt;

    @GraphQLField
    private Double score_gte;

    public CDPInterestFilterInput(@GraphQLName("and") List<CDPInterestFilterInput> and,
                                  @GraphQLName("or") List<CDPInterestFilterInput> or,
                                  @GraphQLName("topic_equals") String topic_equals,
                                  @GraphQLName("score_equals") Double score_equals,
                                  @GraphQLName("score_lt") Double score_lt,
                                  @GraphQLName("score_lte") Double score_lte,
                                  @GraphQLName("score_gt") Double score_gt,
                                  @GraphQLName("score_gte") Double score_gte) {
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

    public void setAnd(List<CDPInterestFilterInput> and) {
        this.and = and;
    }

    public List<CDPInterestFilterInput> getOr() {
        return or;
    }

    public void setOr(List<CDPInterestFilterInput> or) {
        this.or = or;
    }

    public String getTopic_equals() {
        return topic_equals;
    }

    public void setTopic_equals(String topic_equals) {
        this.topic_equals = topic_equals;
    }

    public Double getScore_equals() {
        return score_equals;
    }

    public void setScore_equals(Double score_equals) {
        this.score_equals = score_equals;
    }

    public Double getScore_lt() {
        return score_lt;
    }

    public void setScore_lt(Double score_lt) {
        this.score_lt = score_lt;
    }

    public Double getScore_lte() {
        return score_lte;
    }

    public void setScore_lte(Double score_lte) {
        this.score_lte = score_lte;
    }

    public Double getScore_gt() {
        return score_gt;
    }

    public void setScore_gt(Double score_gt) {
        this.score_gt = score_gt;
    }

    public Double getScore_gte() {
        return score_gte;
    }

    public void setScore_gte(Double score_gte) {
        this.score_gte = score_gte;
    }

}
