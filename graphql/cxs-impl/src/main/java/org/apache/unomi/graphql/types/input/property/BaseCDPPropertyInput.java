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
import graphql.annotations.annotationTypes.GraphQLID;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import graphql.annotations.annotationTypes.GraphQLPrettify;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.PropertyType;
import org.apache.unomi.api.query.NumericRange;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.unomi.graphql.CDPGraphQLConstants.DEFAULT_RANGE_NAME;

public abstract class BaseCDPPropertyInput {

    private String name;
    private Integer minOccurrences;
    private Integer maxOccurrences;
    private List<String> tags;

    public BaseCDPPropertyInput(@GraphQLName("name") String name,
                                @GraphQLName("minOccurrences") Integer minOccurrences,
                                @GraphQLName("maxOccurrences") Integer maxOccurrences,
                                @GraphQLName("tags") List<String> tags) {
        this.name = name;
        this.minOccurrences = minOccurrences;
        this.maxOccurrences = maxOccurrences;
        this.tags = tags;
    }

    public BaseCDPPropertyInput(BaseCDPPropertyInput input) {
        this.name = input.name;
        this.minOccurrences = input.minOccurrences;
        this.maxOccurrences = input.maxOccurrences;
        this.tags = input.tags;
    }

    @GraphQLID
    @GraphQLNonNull
    @GraphQLField
    @GraphQLPrettify
    public String getName() {
        return name;
    }

    @GraphQLField
    @GraphQLPrettify
    public Integer getMinOccurrences() {
        return minOccurrences;
    }

    @GraphQLField
    @GraphQLPrettify
    public Integer getMaxOccurrences() {
        return maxOccurrences;
    }

    @GraphQLField
    @GraphQLPrettify
    public List<String> getTags() {
        return tags;
    }

    public abstract String getCDPPropertyType();

    public void updateType(final PropertyType propertyType) {
        if (propertyType == null) {
            return;
        }

        if ("set".equals(propertyType.getValueTypeId()) && !getCDPPropertyType().equals(propertyType.getValueTypeId())) {
            propertyType.setChildPropertyTypes(Collections.emptySet());
        }

        propertyType.setTarget("profiles");
        propertyType.setItemId(name);
        propertyType.setValueTypeId(getCDPPropertyType());
        propertyType.setMetadata(this.updateMetadata(propertyType.getMetadata()));
        //TODO fix after unomi supports min/max occurrences
        propertyType.setMultivalued(maxOccurrences != null && maxOccurrences > 1);
    }

    public PropertyType toPropertyType() {
        final PropertyType type = new PropertyType();
        updateType(type);
        return type;
    }

    private Metadata updateMetadata(Metadata metadata) {
        if (metadata == null) {
            metadata = new Metadata();
            final Set<String> systemTags = new HashSet<>();

            systemTags.add("profileProperties");
            systemTags.add("properties");
            systemTags.add("systemProfileProperties");

            metadata.setSystemTags(systemTags);
        }

        metadata.setId(name);
        metadata.setName(name);
        metadata.setTags(tags != null ? new HashSet<>(tags) : Collections.emptySet());

        return metadata;
    }

    protected void updateDefaultNumericRange(final PropertyType type, Double from, Double to) {
        if (type == null) {
            return;
        }
        NumericRange defaultRange = new NumericRange();
        defaultRange.setKey(DEFAULT_RANGE_NAME);
        List<NumericRange> ranges = type.getNumericRanges();
        if (ranges == null || ranges.isEmpty()) {
            type.setNumericRanges(Collections.singletonList(defaultRange));
        } else {
            defaultRange = ranges.stream()
                    .filter(range -> DEFAULT_RANGE_NAME.equals(range.getKey()))
                    .findFirst()
                    .orElse(defaultRange);
        }
        defaultRange.setFrom(from);
        defaultRange.setTo(to);
    }

    protected void deleteDefaultNumericRange(final PropertyType type) {
        if (type == null || type.getNumericRanges() == null) {
            return;
        }
        type.setNumericRanges(type.getNumericRanges().stream()
                .filter(range -> !DEFAULT_RANGE_NAME.equals(range.getKey()))
                .collect(Collectors.toList())
        );

    }
}
