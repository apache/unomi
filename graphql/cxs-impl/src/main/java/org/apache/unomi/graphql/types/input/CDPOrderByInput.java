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
import org.apache.commons.lang3.StringUtils;
import org.apache.unomi.graphql.types.output.CDPSortOrder;

@GraphQLName("CDP_OrderByInput")
public class CDPOrderByInput {

    @GraphQLField
    private String fieldName;

    @GraphQLField
    private CDPSortOrder sortOrder;

    public CDPOrderByInput(
            final @GraphQLName("fieldName") String fieldName,
            final @GraphQLName("sortOrder") CDPSortOrder sortOrder) {
        this.fieldName = fieldName;
        this.sortOrder = sortOrder;
    }

    public String getFieldName() {
        return fieldName;
    }

    public CDPSortOrder getSortOrder() {
        return sortOrder;
    }

    public String asString() {
        if (StringUtils.isEmpty(fieldName)) {
            return null;
        } else if (sortOrder == null || CDPSortOrder.UNSPECIFIED == sortOrder) {
            return fieldName;
        }
        return String.format("%s:%s", fieldName, sortOrder.toString().toLowerCase());
    }
}
