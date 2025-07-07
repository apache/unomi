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
package org.apache.unomi.persistence.opensearch.querybuilders.advanced;

import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.services.ExecutionContextManager;
import org.apache.unomi.persistence.opensearch.ConditionOSQueryBuilder;
import org.apache.unomi.persistence.opensearch.ConditionOSQueryBuilderDispatcher;
import org.opensearch.client.opensearch._types.query_dsl.Query;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class IdsConditionOSQueryBuilder implements ConditionOSQueryBuilder {

    private int maximumIdsQueryCount = 5000;
    private ExecutionContextManager executionContextManager;

    public void setMaximumIdsQueryCount(int maximumIdsQueryCount) {
        this.maximumIdsQueryCount = maximumIdsQueryCount;
    }

    public void setExecutionContextManager(ExecutionContextManager executionContextManager) {
        this.executionContextManager = executionContextManager;
    }

    @Override
    public Query buildQuery(Condition condition, Map<String, Object> context, ConditionOSQueryBuilderDispatcher dispatcher) {
        Collection<String> ids = (Collection<String>) condition.getParameter("ids");
        Boolean match = (Boolean) condition.getParameter("match");

        if (ids.size() > maximumIdsQueryCount) {
            // Avoid building too big ids query - throw exception instead
            throw new UnsupportedOperationException("Too many profiles");
        }

        // Get the current tenant ID from the execution context
        String tenantId = executionContextManager.getCurrentContext().getTenantId();

        // Prefix each ID with the tenant ID
        List<String> prefixedIds = new ArrayList<>();
        for (String id : ids) {
            prefixedIds.add(tenantId + "_" + id);
        }

        Query idsQuery = Query.of(q->q.ids(i->i.values(prefixedIds)));
        if (match) {
            return idsQuery;
        } else {
            return Query.of(q->q.bool(b->b.mustNot(idsQuery)));
        }
    }
}
