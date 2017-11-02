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

package org.apache.unomi.services.services;

import org.apache.unomi.api.Profile;
import org.apache.unomi.api.Session;
import org.apache.unomi.api.PersonalizationStrategy;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.services.PersonalizationService;
import org.apache.unomi.api.services.ProfileService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PersonalizationServiceImpl implements PersonalizationService {

    private BundleContext bundleContext;
    private ProfileService profileService;

    private Map<String, PersonalizationStrategy> personalizationStrategies = new ConcurrentHashMap<>();

    public void setProfileService(ProfileService profileService) {
        this.profileService = profileService;
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void addPersonalizationStrategy(ServiceReference<PersonalizationStrategy> personalizationStrategyRef) {
        PersonalizationStrategy personalizationStrategy = bundleContext.getService(personalizationStrategyRef);
        personalizationStrategies.put(personalizationStrategyRef.getProperty("personalizationStrategyId").toString(), personalizationStrategy);
    }

    public void removePersonalizationStrategy(ServiceReference<PersonalizationStrategy> personalizationStrategyRef) {
        if (personalizationStrategyRef == null) {
            return;
        }
        personalizationStrategies.remove(personalizationStrategyRef.getProperty("personalizationStrategyId").toString());
    }

    @Override
    public boolean filter(Profile profile, Session session, PersonalizedContent personalizedContent) {
        boolean result = true;
        for (Filter filter : personalizedContent.getFilters()) {
            Condition condition = filter.getCondition();
            if (condition.getConditionType() != null) {
                result &= profileService.matchCondition(condition, profile, session);
            }
        }
        return result;
    }

    @Override
    public String bestMatch(Profile profile, Session session, PersonalizationRequest personalizationRequest) {
        List<String> sorted = personalizeList(profile,session,personalizationRequest);
        if (sorted.size() > 0) {
            return sorted.get(0);
        }
        return null;
    }

    @Override
    public List<String> personalizeList(Profile profile, Session session, PersonalizationRequest personalizationRequest) {
        PersonalizationStrategy strategy = personalizationStrategies.get(personalizationRequest.getStrategy());

        if (strategy != null) {
            return strategy.personalizeList(profile, session, personalizationRequest);
        }

        throw new IllegalArgumentException("Unknown strategy : "+ personalizationRequest.getStrategy());
    }
}
