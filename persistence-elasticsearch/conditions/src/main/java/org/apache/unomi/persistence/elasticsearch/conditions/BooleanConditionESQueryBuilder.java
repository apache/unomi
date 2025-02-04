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

package org.apache.unomi.persistence.elasticsearch.conditions;

import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.persistence.elasticsearch.ConditionESQueryBuilder;
import org.apache.unomi.persistence.elasticsearch.ConditionESQueryBuilderDispatcher;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * ES query builder for boolean conditions.
 */
public class BooleanConditionESQueryBuilder implements ConditionESQueryBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(BooleanConditionESQueryBuilder.class.getName());

    @Override
    public QueryBuilder buildQuery(Condition condition, Map<String, Object> context,
                                   ConditionESQueryBuilderDispatcher dispatcher) {
        boolean isAndOperator = "and".equalsIgnoreCase((String) condition.getParameter("operator"));
        @SuppressWarnings("unchecked")
        List<Condition> conditions = (List<Condition>) condition.getParameter("subConditions");

        int conditionCount = conditions.size();

        if (conditionCount == 1) {
            return dispatcher.buildFilter(conditions.get(0), context);
        }

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        for (int i = 0; i < conditionCount; i++) {
            if (isAndOperator) {
                QueryBuilder andFilter = dispatcher.buildFilter(conditions.get(i), context);
                if (andFilter != null) {
                    if (andFilter.getName().equals("range")) {
                        boolQueryBuilder.filter(andFilter);
                    } else {
                        boolQueryBuilder.must(andFilter);
                    }
                } else {
                    LOGGER.warn("Null filter for boolean AND sub condition. See debug log level for more information");
                    if (LOGGER.isDebugEnabled()) LOGGER.debug("Null filter for boolean AND sub condition {}", conditions.get(i));
                }
            } else {
                QueryBuilder orFilter = dispatcher.buildFilter(conditions.get(i), context);
                if (orFilter != null) {
                    boolQueryBuilder.should(orFilter);
                } else {
                    LOGGER.warn("Null filter for boolean OR sub condition. See debug log level for more information");
                    if (LOGGER.isDebugEnabled()) LOGGER.debug("Null filter for boolean OR sub condition {}", conditions.get(i));
                }
            }
        }

        return boolQueryBuilder;
    }
}
