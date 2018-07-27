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

import java.util.List;

@GraphQLName("CXS_IntPropertyType")
public class CXSIntPropertyType extends CXSPropertyType {

    private Integer minValue;
    private Integer maxValue;
    private Integer defaultValue;

    public CXSIntPropertyType(@GraphQLName("id") String id,
                              @GraphQLName("name") String name,
                              @GraphQLName("minOccurrences") Integer minOccurrences,
                              @GraphQLName("maxOccurrences") Integer maxOccurrences,
                              @GraphQLName("tags") List<String> tags,
                              @GraphQLName("systemTags") List<String> systemTags,
                              @GraphQLName("personalData") Boolean personalData,
                              @GraphQLName("minValue") Integer minValue,
                              @GraphQLName("maxValue") Integer maxValue,
                              @GraphQLName("defaultValue") Integer defaultValue) {
        super(id, name, minOccurrences, maxOccurrences, tags, systemTags, personalData);
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.defaultValue = defaultValue;
    }

    @GraphQLField
    public Integer getMinValue() {
        return minValue;
    }

    @GraphQLField
    public Integer getMaxValue() {
        return maxValue;
    }

    @GraphQLField
    public Integer getDefaultValue() {
        return defaultValue;
    }
}
