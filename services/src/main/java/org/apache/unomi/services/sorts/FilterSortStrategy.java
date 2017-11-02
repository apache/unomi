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

import java.util.ArrayList;
import java.util.List;

public class FilterSortStrategy implements SortStrategy {

    private ProfileService profileService;

    public void setProfileService(ProfileService profileService) {
        this.profileService = profileService;
    }

    @Override
    public List<String> sort(Profile profile, Session session, ContextRequest.SortRequest sortRequest) {
        List<String> sortedContent = new ArrayList<>();
        for (ContextRequest.FilteredContent filteredContent : sortRequest.getContents()) {
            boolean result = true;
            for (ContextRequest.Filter filter : filteredContent.getFilters()) {
                Condition condition = filter.getCondition();
                result &= profileService.matchCondition(condition, profile, session);
            }
            if (result) {
                sortedContent.add(filteredContent.getFilterid());
            }
        }

        String fallback = (String) sortRequest.getStrategyOptions().get("fallback");
        if (fallback != null && !sortedContent.contains(fallback)) {
            sortedContent.add(fallback);
        }

        return sortedContent;
    }
}
