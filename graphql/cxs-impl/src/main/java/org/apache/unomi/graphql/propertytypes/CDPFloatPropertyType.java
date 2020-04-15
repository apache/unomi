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
package org.apache.unomi.graphql.propertytypes;

import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLPrettify;

import java.util.List;

@GraphQLName("CDP_FloatProperty")
public class CDPFloatPropertyType extends CDPPropertyType {

    private Double minValue;
    private Double maxValue;
    private Double defaultValue;

    public CDPFloatPropertyType(@GraphQLName("name") String name,
                                @GraphQLName("minOccurrences") Integer minOccurrences,
                                @GraphQLName("maxOccurrences") Integer maxOccurrences,
                                @GraphQLName("tags") List<String> tags,
                                @GraphQLName("minValue") Double minValue,
                                @GraphQLName("maxValue") Double maxValue,
                                @GraphQLName("defaultValue") Double defaultValue) {
        super(name, minOccurrences, maxOccurrences, tags);
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.defaultValue = defaultValue;
    }

    @GraphQLField
    @GraphQLPrettify
    public Double getMinValue() {
        return minValue;
    }

    @GraphQLField
    @GraphQLPrettify
    public Double getMaxValue() {
        return maxValue;
    }

    @GraphQLField
    @GraphQLPrettify
    public Double getDefaultValue() {
        return defaultValue;
    }

    @Override
    public String getCDPPropertyType() {
        return "float";
    }

}