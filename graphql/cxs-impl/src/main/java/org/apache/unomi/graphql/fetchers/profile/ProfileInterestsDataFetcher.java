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
import org.apache.unomi.graphql.types.output.CDPInterest;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ProfileInterestsDataFetcher implements DataFetcher<List<CDPInterest>> {

    private final Profile profile;

    public ProfileInterestsDataFetcher(Profile profile) {
        this.profile = profile;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<CDPInterest> get(DataFetchingEnvironment environment) throws Exception {
        final Map<String, Double> interests = (Map<String, Double>) profile.getProperties().get("interests");
        if (interests == null) {
            return Collections.emptyList();
        }

        return interests.entrySet().stream()
                .map(entry -> new CDPInterest(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }
}
