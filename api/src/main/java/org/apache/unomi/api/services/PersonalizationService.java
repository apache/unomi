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

package org.apache.unomi.api.services;

import org.apache.unomi.api.Profile;
import org.apache.unomi.api.Session;
import org.apache.unomi.api.conditions.Condition;

import java.util.List;
import java.util.Map;

/**
 * A service to fulfill personalization request.
 */
public interface PersonalizationService {

    /**
     * Check if an item is visible for the specified profile and session
     *
     * @param profile The profile
     * @param session The session
     * @param personalizedContent Personalized content, containing a list of filters
     * @return If the content is visible or not
     */
    boolean filter(Profile profile, Session session, PersonalizedContent personalizedContent);

    /**
     * Get the best match among a list of items, for the specified profile and session
     *
     * @param profile The profile
     * @param session The session
     * @param personalizationRequest Personalization request, containing the list of variants and the required strategy
     * @return The id of the best-matching variant
     */
    String bestMatch(Profile profile, Session session, PersonalizationRequest personalizationRequest);

    /**
     * Get a personalized list, filtered and sorted, based on the profile and session
     *
     * @param profile The profile
     * @param session The session
     * @param personalizationRequest Personalization request, containing the list of variants and the required strategy
     * @return List of ids, based on user profile
     */
    List<String> personalizeList(Profile profile, Session session, PersonalizationRequest personalizationRequest);

    /**
     * Personalization request
     */
    class PersonalizationRequest {
        private String id;
        private String strategy;
        private Map<String, Object> strategyOptions;
        private List<PersonalizedContent> contents;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getStrategy() {
            return strategy;
        }

        public void setStrategy(String strategy) {
            this.strategy = strategy;
        }

        public List<PersonalizedContent> getContents() {
            return contents;
        }

        public void setContents(List<PersonalizedContent> contents) {
            this.contents = contents;
        }

        public Map<String, Object> getStrategyOptions() {
            return strategyOptions;
        }

        public void setStrategyOptions(Map<String, Object> strategyOptions) {
            this.strategyOptions = strategyOptions;
        }
    }

    /**
     * A personalizated content definition.
     */
    class PersonalizedContent {
        private String id;
        private List<Filter> filters;
        private Map<String,Object> properties;

        /**
         * Retrieves the filter identifier associated with this content filtering definition.
         *
         * @return the filter identifier associated with this content filtering definition
         */
        public String getId() {
            return id;
        }

        /**
         * Sets the filter identifier associated with this content filtering definition.
         *
         * @param id the filter identifier associated with this content filtering definition
         */
        public void setId(String id) {
            this.id = id;
        }

        /**
         * Sets the filter identifier associated with this content filtering definition.
         *
         * @param filterid the filter identifier associated with this content filtering definition
         * @deprecated this method is deprecated use the setId method instead
         */
        public void setFilterid(String filterid) {
            this.id = filterid;
        }

        /**
         * Retrieves the filters.
         *
         * @return the filters
         */
        public List<Filter> getFilters() {
            return filters;
        }

        /**
         * Sets the filters.
         *
         * @param filters the filters
         */
        public void setFilters(List<Filter> filters) {
            this.filters = filters;
        }

        public Map<String, Object> getProperties() {
            return properties;
        }

        public void setProperties(Map<String, Object> properties) {
            this.properties = properties;
        }
    }

    /**
     * A filter definition for content filtering
     */
    class Filter {
        private List<Target> appliesOn;
        private Condition condition;
        private Map<String,Object> properties;

        /**
         * Retrieves the list of targets this filter applies on.
         *
         * @return the applies on
         */
        public List<Target> getAppliesOn() {
            return appliesOn;
        }

        /**
         * Specifies which targets this filter applies on.
         *
         * @param appliesOn the list of {@link Target} this filter should be applied on
         */
        public void setAppliesOn(List<Target> appliesOn) {
            this.appliesOn = appliesOn;
        }

        /**
         * Retrieves the condition associated with this filter.
         *
         * @return the condition associated with this filter
         */
        public Condition getCondition() {
            return condition;
        }

        /**
         * Sets the condition associated with this filter.
         *
         * @param condition the condition associated with this filter
         */
        public void setCondition(Condition condition) {
            this.condition = condition;
        }

        public Map<String, Object> getProperties() {
            return properties;
        }

        public void setProperties(Map<String, Object> properties) {
            this.properties = properties;
        }
    }

    /**
     * A target for content filtering.
     */
    class Target {
        private String target;
        private List<String> values;

        /**
         * Retrieves the target.
         *
         * @return the target
         */
        public String getTarget() {
            return target;
        }

        /**
         * Sets the target.
         *
         * @param target the target
         */
        public void setTarget(String target) {
            this.target = target;
        }

        /**
         * Retrieves the values.
         *
         * @return the values
         */
        public List<String> getValues() {
            return values;
        }

        /**
         * Sets the values.
         *
         * @param values the values
         */
        public void setValues(List<String> values) {
            this.values = values;
        }
    }
}
