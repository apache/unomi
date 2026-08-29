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
package org.apache.unomi.graphql.types.input.property;

import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLPrettify;
import org.apache.unomi.api.PropertyType;

import java.util.List;

@GraphQLName("CDP_LongPropertyInput")
public class CDPLongPropertyInput extends BaseCDPPropertyInput {

    private Long minValue;
    private Long maxValue;
    private Long defaultValue;

    public CDPLongPropertyInput(@GraphQLName("name") String name,
                                @GraphQLName("minOccurrences") Integer minOccurrences,
                                @GraphQLName("maxOccurrences") Integer maxOccurrences,
                                @GraphQLName("tags") List<String> tags,
                                @GraphQLName("minValue") Long minValue,
                                @GraphQLName("maxValue") Long maxValue,
                                @GraphQLName("defaultValue") Long defaultValue) {
        super(name, minOccurrences, maxOccurrences, tags);
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.defaultValue = defaultValue;
    }

    @GraphQLField
    @GraphQLPrettify
    public Long getMinValue() {
        return minValue;
    }

    @GraphQLField
    @GraphQLPrettify
    public Long getMaxValue() {
        return maxValue;
    }

    @GraphQLField
    @GraphQLPrettify
    public Long getDefaultValue() {
        return defaultValue;
    }

    @Override
    public String getCDPPropertyType() {
        return "long";
    }

    @Override
    public void updateType(final PropertyType type) {
        if (type == null) {
            return;
        }
        super.updateType(type);
        type.setDefaultValue(defaultValue != null ? defaultValue.toString() : null);
        if (minValue != null || maxValue != null) {
            final Double from = minValue != null ? minValue.doubleValue() : null;
            final Double to = maxValue != null && (minValue == null || maxValue >= minValue) ? maxValue.doubleValue() : null;
            updateDefaultNumericRange(type, from, to);
        } else {
            deleteDefaultNumericRange(type);
        }
    }
}
