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

package org.apache.unomi.graphql.fetchers.list;

import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.api.ExecutionContext;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.services.ExecutionContextManager;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.api.utils.DiagnosticLog;
import org.apache.unomi.graphql.condition.factories.ProfileConditionFactory;
import org.apache.unomi.graphql.fetchers.ConnectionParams;
import org.apache.unomi.graphql.fetchers.ProfileConnectionDataFetcher;
import org.apache.unomi.graphql.services.ServiceManager;
import org.apache.unomi.graphql.types.output.CDPList;
import org.apache.unomi.graphql.types.output.CDPProfileConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListProfileConnectionDataFetcher extends ProfileConnectionDataFetcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(ListProfileConnectionDataFetcher.class);

    @Override
    public CDPProfileConnection get(final DataFetchingEnvironment environment) throws Exception {
        final CDPList cdpList = environment.getSource();
        final ServiceManager serviceManager = environment.getContext();
        final ConnectionParams params = parseConnectionParams(environment);

        Condition listIdCondition = ProfileConditionFactory.get(environment)
                .propertyCondition("systemProperties.lists", "contains", cdpList.id());

        final Query query = buildQuery(listIdCondition, null, params);

        PartialList<Profile> profiles = serviceManager.getService(ProfileService.class).search(query, Profile.class);

        // An empty active-members result is the exact symptom of GraphQLListIT.testCRUD flakiness. Log the
        // tenant, the resolved condition type id (null => match-none), and the result size so we can
        // definitively separate "broken query" (null condition type) from "membership not yet persisted".
        if (profiles == null || profiles.getList() == null || profiles.getList().isEmpty()) {
            logEmptyMembersDiagnostics(serviceManager, cdpList, listIdCondition);
        }

        return createProfileConnection(profiles);
    }

    private void logEmptyMembersDiagnostics(final ServiceManager serviceManager, final CDPList cdpList, final Condition listIdCondition) {
        String currentTenant = "<unavailable>";
        try {
            final ExecutionContextManager contextManager = serviceManager.getService(ExecutionContextManager.class);
            if (contextManager != null) {
                final ExecutionContext context = contextManager.getCurrentContext();
                currentTenant = context == null ? "<null-context>" : String.valueOf(context.getTenantId());
            }
        } catch (Exception e) {
            DiagnosticLog.warn(LOGGER, "list-active-members-empty-error", "error", e.getMessage());
        }
        DiagnosticLog.warn(LOGGER, "list-active-members-empty",
                "listId", cdpList == null ? "<null>" : cdpList.id(),
                "currentTenant", currentTenant,
                "conditionTypeId", listIdCondition == null ? "<null-condition>" : listIdCondition.getConditionTypeId(),
                "note", "null-conditionTypeId-means-match-none");
    }

}
