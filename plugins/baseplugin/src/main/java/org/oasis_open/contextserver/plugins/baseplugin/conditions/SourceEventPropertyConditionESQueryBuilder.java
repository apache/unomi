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

package org.oasis_open.contextserver.plugins.baseplugin.conditions;

import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.oasis_open.contextserver.api.conditions.Condition;
import org.oasis_open.contextserver.persistence.elasticsearch.conditions.ConditionESQueryBuilder;
import org.oasis_open.contextserver.persistence.elasticsearch.conditions.ConditionESQueryBuilderDispatcher;

import java.lang.Object;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SourceEventPropertyConditionESQueryBuilder implements ConditionESQueryBuilder {

    public SourceEventPropertyConditionESQueryBuilder() {
    }

    private void appendFilderIfPropExist(List<FilterBuilder> filterBuilders, Condition condition, String prop){
        final Object parameter = condition.getParameter(prop);
        if (parameter != null && !"".equals(parameter)) {
            filterBuilders.add(FilterBuilders.termFilter("source." + prop, (String) parameter));
        }
    }

    public FilterBuilder buildFilter(Condition condition, Map<String, Object> context, ConditionESQueryBuilderDispatcher dispatcher) {
        List<FilterBuilder> l = new ArrayList<FilterBuilder>();
        for (String prop : new String[]{"id", "path", "scope", "type"}){
            appendFilderIfPropExist(l, condition, prop);
        }

        if (l.size() >= 1) {
            return l.size() == 1 ? l.get(0) : FilterBuilders.andFilter(l.toArray(new FilterBuilder[l.size()]));
        } else {
            return null;
        }
    }
}