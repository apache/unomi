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

import com.google.common.base.Strings;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import org.apache.unomi.graphql.types.output.CDPSortOrder;

@GraphQLName("CDP_OrderBy")
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

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public CDPSortOrder getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(CDPSortOrder sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String asString() {
        if (Strings.isNullOrEmpty(fieldName)) {
            return null;
        } else if (sortOrder == null || CDPSortOrder.UNSPECIFIED == sortOrder) {
            return fieldName;
        }
        return String.format("%s:%s", fieldName, sortOrder.toString());
    }
}
