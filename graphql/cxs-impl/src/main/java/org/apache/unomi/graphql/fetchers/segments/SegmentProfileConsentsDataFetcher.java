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
package org.apache.unomi.graphql.fetchers.segments;

import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.api.conditions.Condition;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class SegmentProfileConsentsDataFetcher extends BaseSegmentContainsDataFetcher<List<String>> {

    @Override
    @SuppressWarnings("unchecked")
    public List<String> get(DataFetchingEnvironment environment) throws Exception {
        return getSubConditions(environment).stream()
                .filter(condition -> "booleanCondition".equals(condition.getConditionTypeId())
                        && "or".equals(condition.getParameter("operator"))
                        && Objects.nonNull(condition.getParameter("subConditions")))
                .flatMap(condition -> ((ArrayList<Condition>) condition.getParameter("subConditions")).stream())
                .filter(condition -> "booleanCondition".equals(condition.getConditionTypeId())
                        && "and".equals(condition.getParameter("operator"))
                        && Objects.nonNull(condition.getParameter("subConditions")))
                .flatMap(condition -> ((ArrayList<Condition>) condition.getParameter("subConditions")).stream())
                .filter(condition -> "profilePropertyCondition".equals(condition.getConditionTypeId())
                        && condition.getParameter("propertyName").toString().startsWith("consents.")
                        && condition.getParameter("propertyName").toString().endsWith(".status")
                        && "equals".equals(condition.getParameter("comparisonOperator"))
                        && Objects.nonNull(condition.getParameter("propertyValue"))
                        && "GRANTED".equalsIgnoreCase(condition.getParameter("propertyValue").toString()))
                .map(condition ->
                        condition.getParameter("propertyName").toString()
                                .replaceAll("consents.", "")
                                .replaceAll(".status", "")
                )
                .collect(Collectors.toList());
    }

}
