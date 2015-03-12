package org.oasis_open.contextserver.plugins.events.hover.querybuilders;

/*
 * #%L
 * Context Server Plugin - Hover event support
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2014 - 2015 Jahia Solutions
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.oasis_open.contextserver.api.conditions.Condition;
import org.oasis_open.contextserver.persistence.elasticsearch.conditions.ConditionESQueryBuilder;
import org.oasis_open.contextserver.persistence.elasticsearch.conditions.ConditionESQueryBuilderDispatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HoverEventConditionESQueryBuilder implements ConditionESQueryBuilder {

    public HoverEventConditionESQueryBuilder() {
    }

    public FilterBuilder buildFilter(Condition condition, Map<String, Object> context, ConditionESQueryBuilderDispatcher dispatcher) {
        List<FilterBuilder> filters = new ArrayList<FilterBuilder>();
        filters.add(FilterBuilders.termFilter("eventType", "hover"));
        String targetId = (String) condition.getParameterValues().get("targetId");
        String targetPath = (String) condition.getParameterValues().get("targetPath");

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
