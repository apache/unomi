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

import org.apache.unomi.api.PersonalizationResult;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.Session;
import org.apache.unomi.api.PersonalizationStrategy;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.services.PersonalizationService;
import org.apache.unomi.api.services.ProfileService;

import java.util.ArrayList;
import java.util.List;

/**
 * This strategy will use filters to only keep the contents that match all their associated filters
 */
public class FilterPersonalizationStrategy implements PersonalizationStrategy {

    private ProfileService profileService;

    public void setProfileService(ProfileService profileService) {
        this.profileService = profileService;
    }

    @Override
    public PersonalizationResult personalizeList(Profile profile, Session session, PersonalizationService.PersonalizationRequest personalizationRequest) {
        List<String> sortedContent = new ArrayList<>();
        for (PersonalizationService.PersonalizedContent personalizedContent : personalizationRequest.getContents()) {
            boolean result = true;
            if (personalizedContent.getFilters() != null) {
                for (PersonalizationService.Filter filter : personalizedContent.getFilters()) {
                    Condition condition = filter.getCondition();
                    result &= profileService.matchCondition(condition, profile, session);
                }
            }
            if (result) {
                sortedContent.add(personalizedContent.getId());
            }
        }

        if (personalizationRequest.getStrategyOptions() != null) {
            String fallback = (String) personalizationRequest.getStrategyOptions().get("fallback");
            if (fallback != null && !sortedContent.contains(fallback)) {
                sortedContent.add(fallback);
            }
        }

        return new PersonalizationResult(sortedContent);
    }
}
