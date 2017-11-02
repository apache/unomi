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

package org.apache.unomi.services.sorts;

import org.apache.unomi.api.ContextRequest;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.Session;
import org.apache.unomi.api.SortStrategy;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.services.ProfileService;

import java.util.*;

public class ScoreSortStrategy implements SortStrategy {

    private ProfileService profileService;

    public void setProfileService(ProfileService profileService) {
        this.profileService = profileService;
    }

    @Override
    public List<String> sort(Profile profile, Session session, ContextRequest.SortRequest sortRequest) {
        List<String> sortedContent = new ArrayList<>();
        final Map<String,Integer> t = new HashMap<>();

        Integer threshold = (Integer) sortRequest.getStrategyOptions().get("threshold");
        if (threshold == null) {
            threshold = 0;
        }

        for (ContextRequest.FilteredContent filteredContent : sortRequest.getContents()) {
            int score = 0;

            String interestList = (String) (filteredContent.getProperties() != null ? filteredContent.getProperties().get("interests") : null);
            if (interestList != null) {
                Map<String,Integer> interestValues = (Map<String, Integer>) profile.getProperties().get("interests");
                for (String interest : interestList.split(" ")) {
                    if (interestValues.get(interest) != null) {
                        score += interestValues.get(interest);
                    }
                }
            }

            String scoringPlanList = (String) (filteredContent.getProperties() != null ? filteredContent.getProperties().get("scoringPlans") : null);
            if (scoringPlanList != null) {
                Map<String,Integer> scoreValues = (Map<String, Integer>) profile.getScores();
                for (String scoringPlan : scoringPlanList.split(" ")) {
                    if (scoreValues.get(scoringPlan) != null) {
                        score += scoreValues.get(scoringPlan);
                    }
                }
            }

            for (ContextRequest.Filter filter : filteredContent.getFilters()) {
                Condition condition = filter.getCondition();
                if (condition.getConditionType() != null) {
                    if (profileService.matchCondition(condition, profile, session)) {
                        if (filter.getProperties().get("score") != null) {
                            score += (int) filter.getProperties().get("score");
                        } else {
                            score += 1;
                        }
                    }
                }
            }
            if (score >= threshold) {
                t.put(filteredContent.getFilterid(), score);
                sortedContent.add(filteredContent.getFilterid());
            }
        }
        Collections.sort(sortedContent, new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return t.get(o2) - t.get(o1);
            }
        });

        String fallback = (String) sortRequest.getStrategyOptions().get("fallback");
        if (fallback != null && !sortedContent.contains(fallback)) {
            sortedContent.add(fallback);
        }

        return sortedContent;
    }
}
