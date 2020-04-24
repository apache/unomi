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

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.Session;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.graphql.condition.ProfileConditionFactory;
import org.apache.unomi.graphql.services.ServiceManager;
import org.apache.unomi.graphql.types.input.CDPNamedFilterInput;
import org.apache.unomi.graphql.types.output.CDPFilterMatch;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;


public class ProfileMatchesDataFetcher implements DataFetcher<List<CDPFilterMatch>> {

    private final Profile profile;
    private final List<CDPNamedFilterInput> namedFilters;

    public ProfileMatchesDataFetcher(Profile profile, List<CDPNamedFilterInput> namedFilters) {
        this.profile = profile;
        this.namedFilters = namedFilters;
    }

    @Override
    public List<CDPFilterMatch> get(DataFetchingEnvironment environment) throws Exception {
        if (namedFilters == null || namedFilters.isEmpty()) {
            return Collections.emptyList();
        }

        final ServiceManager serviceManager = environment.getContext();
        final ProfileService profileService = serviceManager.getProfileService();
        final Date now = new Date();
        final Session session = new Session("profile-matcher-" + now.getTime(), profile, now, "digitall");

        final List<Map<String, Object>> namedFiltersAsMap = environment.getArgument("namedFilters");

        final ProfileConditionFactory factory = ProfileConditionFactory.get(environment);
        AtomicInteger i = new AtomicInteger();
        return namedFilters.stream().map(filterInput -> {
            Map<String, Object> namedFilterAsMap = null;
            if (namedFiltersAsMap.size() > i.get()) {
                namedFilterAsMap = namedFiltersAsMap.get(i.get());
            }
            final long startTime = System.currentTimeMillis();
            final Condition condition = factory.profileFilterInputCondition(filterInput.getFilter(), namedFilterAsMap);
            final boolean matches = profileService.matchCondition(condition, profile, session);
            i.getAndIncrement();
            return new CDPFilterMatch(filterInput.getName(), matches, System.currentTimeMillis() - startTime);
        }).collect(Collectors.toList());
    }

}
