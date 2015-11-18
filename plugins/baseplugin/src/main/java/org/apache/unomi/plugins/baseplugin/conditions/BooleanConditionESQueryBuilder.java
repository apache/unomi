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

package org.apache.unomi.plugins.baseplugin.conditions;

import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.persistence.elasticsearch.conditions.ConditionESQueryBuilder;
import org.apache.unomi.persistence.elasticsearch.conditions.ConditionESQueryBuilderDispatcher;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;

import java.util.List;
import java.util.Map;

/**
 * ES query builder for boolean conditions.
 */
public class BooleanConditionESQueryBuilder implements ConditionESQueryBuilder {

    @Override
    public FilterBuilder buildFilter(Condition condition, Map<String, Object> context,
            ConditionESQueryBuilderDispatcher dispatcher) {
        boolean isAndOperator = "and".equalsIgnoreCase((String) condition.getParameter("operator"));
        @SuppressWarnings("unchecked")
        List<Condition> conditions = (List<Condition>) condition.getParameter("subConditions");

        int conditionCount = conditions.size();

        if (conditionCount == 1) {
            return dispatcher.buildFilter(conditions.get(0), context);
        }

        FilterBuilder[] l = new FilterBuilder[conditionCount];
        for (int i = 0; i < conditionCount; i++) {
            l[i] = dispatcher.buildFilter(conditions.get(i), context);
        }

        return isAndOperator ? FilterBuilders.andFilter(l) : FilterBuilders.orFilter(l);
    }
}
