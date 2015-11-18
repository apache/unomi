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

package org.apache.unomi.plugins.events.hover.querybuilders;

import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.persistence.elasticsearch.conditions.ConditionESQueryBuilder;
import org.apache.unomi.persistence.elasticsearch.conditions.ConditionESQueryBuilderDispatcher;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Condition builder for hover event types
 */
public class HoverEventConditionESQueryBuilder implements ConditionESQueryBuilder {

    public HoverEventConditionESQueryBuilder() {
    }

    public FilterBuilder buildFilter(Condition condition, Map<String, Object> context, ConditionESQueryBuilderDispatcher dispatcher) {
        List<FilterBuilder> filters = new ArrayList<FilterBuilder>();
        filters.add(FilterBuilders.termFilter("eventType", "hover"));
        String targetId = (String) condition.getParameter("targetId");
        String targetPath = (String) condition.getParameter("targetPath");

        if (targetId != null && targetId.trim().length() > 0) {
            filters.add(FilterBuilders.termFilter("target.itemId", targetId));
        } else if (targetPath != null && targetPath.trim().length() > 0) {
            filters.add(FilterBuilders.termFilter("target.properties.pageInfo.pagePath", targetPath));
        } else {
            filters.add(FilterBuilders.termFilter("target.itemId", ""));
        }
        return FilterBuilders.andFilter(filters.toArray(new FilterBuilder[filters.size()]));
    }
}
