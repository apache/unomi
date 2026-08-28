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
import java.util.Objects;
import java.util.stream.Collectors;

public class ProfileInterestsDataFetcher implements DataFetcher<List<CDPInterest>> {

    private final Profile profile;

    private final List<String> viewIds;

    public ProfileInterestsDataFetcher(Profile profile, List<String> viewIds) {
        this.profile = profile;
        this.viewIds = viewIds;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<CDPInterest> get(DataFetchingEnvironment environment) throws Exception {
        final List<Map<String, Object>> interests = (List<Map<String, Object>>) profile.getProperties().get("interests");
        if (interests == null) {
            return Collections.emptyList();
        }

        return interests.stream()
                .map(interest -> {
                    final String topic = interest.get("key").toString();
                    final Double score = Double.parseDouble(interest.get("value").toString());
                    if (viewIds != null && !viewIds.isEmpty()) {
                        return viewIds.contains(topic) ? new CDPInterest(topic, score) : null;
                    }
                    return new CDPInterest(topic, score);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
