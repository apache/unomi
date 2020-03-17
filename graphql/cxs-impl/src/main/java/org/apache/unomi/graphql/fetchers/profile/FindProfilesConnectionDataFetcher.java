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

package org.apache.unomi.graphql.fetchers.profile;

import com.google.common.base.Strings;
import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.graphql.fetchers.ConnectionParams;
import org.apache.unomi.graphql.fetchers.ProfileConnectionDataFetcher;
import org.apache.unomi.graphql.services.ServiceManager;
import org.apache.unomi.graphql.types.input.CDPOrderByInput;
import org.apache.unomi.graphql.types.input.CDPProfileFilterInput;
import org.apache.unomi.graphql.types.output.CDPProfileConnection;

import java.util.List;
import java.util.stream.Collectors;

public class FindProfilesConnectionDataFetcher extends ProfileConnectionDataFetcher {

    private final CDPProfileFilterInput filterInput;
    private final List<CDPOrderByInput> orderByInput;

    public FindProfilesConnectionDataFetcher(CDPProfileFilterInput filterInput, List<CDPOrderByInput> orderByInput) {
        this.filterInput = filterInput;
        this.orderByInput = orderByInput;
    }

    @Override
    public CDPProfileConnection get(DataFetchingEnvironment environment) throws Exception {
        final ServiceManager serviceManager = environment.getContext();
        final ConnectionParams params = parseConnectionParams(environment);

        final Condition filterCondition = createProfileFilterInputCondition(
                filterInput, params.getAfter(), params.getBefore(), serviceManager.getDefinitionsService());
        final Query query = new Query();
        if (orderByInput != null) {
            final String sortBy = orderByInput.stream().map(CDPOrderByInput::asString)
                    .collect(Collectors.joining(","));

            if (!Strings.isNullOrEmpty(sortBy)) {
                query.setSortby(sortBy);
            }
        }
        query.setOffset(params.getFirst());
        query.setLimit(params.getSize());
        query.setCondition(filterCondition);

        PartialList<Profile> profiles = serviceManager.getProfileService().search(query, Profile.class);

        return createProfileConnection(profiles);
    }
}
