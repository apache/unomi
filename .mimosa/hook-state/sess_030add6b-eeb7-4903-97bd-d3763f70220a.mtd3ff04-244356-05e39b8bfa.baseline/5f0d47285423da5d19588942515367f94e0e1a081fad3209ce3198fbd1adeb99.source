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
package org.apache.unomi.graphql.condition.parsers;

import org.apache.unomi.api.conditions.Condition;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class SegmentProfileIDsConditionParser {

    private static final Predicate<Condition> IS_PROFILE_PROPERTY_CONDITION_TYPE =
            condition -> "profilePropertyCondition".equals(condition.getConditionTypeId());

    private final List<Condition> conditions;

    public SegmentProfileIDsConditionParser(List<Condition> conditions) {
        this.conditions = conditions;
    }

    public List<String> parse() {
        return conditions.stream()
                .filter(condition -> IS_PROFILE_PROPERTY_CONDITION_TYPE.test(condition)
                        && "itemId".equals(condition.getParameter("propertyName"))
                        && "inContains".equals(condition.getParameter("comparisonOperator"))
                        && Objects.nonNull(condition.getParameter("propertyValues")))
                .map(condition -> (List<String>) condition.getParameter("propertyValues"))
                .reduce(new ArrayList<>(), (List<String> all, List<String> ids) -> {
                    all.addAll(ids);
                    return all;
                });
    }

}
