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

import org.apache.unomi.api.PersonalizationResult;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.Session;
import org.apache.unomi.api.conditions.Condition;

import java.util.List;
import java.util.Map;

/**
 * Resolves which content variants to show a visitor.
 * Evaluates personalization requests against profiles, sessions, and
 * segments, returning {@link PersonalizationResult} with matching content ids.
 */
public interface PersonalizationService {

    /**
     * Evaluates whether personalized content is visible for the given profile and session.
     *
     * @param profile visitor profile
     * @param session visitor session
     * @param personalizedContent content definition with filters to evaluate
     * @return {@code true} if the content should be shown
     */
    boolean filter(Profile profile, Session session, PersonalizedContent personalizedContent);

    /**
     * Selects the best-matching content variant for the given profile and session.
     *
     * @param profile visitor profile
     * @param session visitor session
     * @param personalizationRequest request with variants and selection strategy
     * @return id of the best-matching variant
     */
    String bestMatch(Profile profile, Session session, PersonalizationRequest personalizationRequest);

    /**
     * Filters and ranks content variants for the given profile and session.
     *
     * @param profile visitor profile
     * @param session visitor session
     * @param personalizationRequest request with variants and selection strategy
     * @return ordered personalization result for the visitor
     */
    PersonalizationResult personalizeList(Profile profile, Session session, PersonalizationRequest personalizationRequest);

    /**
     * Request to resolve an ordered set of content variants for a profile/session
     * (sent in {@code ContextRequest.personalizations}).
     */
    class PersonalizationRequest {
        /**
         * Client-defined personalization id; echoed as the key in {@code ContextResponse.personalizationResults}.
         * @api.example homepage-hero
         */
        private String id;
        /**
         * Strategy plugin name (for example {@code matching-content}, {@code score-sort}, {@code random}).
         * @api.example matching-content
         */
        private String strategy;
        /**
         * Strategy-specific options (control groups, fallbacks, size limits, …). Free-form map.
         * @api.example {"fallback":"default","size":1}
         */
        private Map<String, Object> strategyOptions;
        /**
         * Candidate content variants evaluated by the strategy.
         */
        private List<PersonalizedContent> contents;

        /**
         * Request identifier.
         *
         * @return request id
         */
        public String getId() {
            return id;
        }

        /**
         * Sets the request identifier.
         *
         * @param id request id
         */
        public void setId(String id) {
            this.id = id;
        }

        /**
         * Personalization strategy name (for example {@code alwaysSet}).
         *
         * @return strategy name
         */
        public String getStrategy() {
            return strategy;
        }

        /**
         * Sets the personalization strategy name.
         *
         * @param strategy strategy name (for example {@code alwaysSet})
         */
        public void setStrategy(String strategy) {
            this.strategy = strategy;
        }

        /**
         * Content variants included in this request.
         *
         * @return personalized content items
         */
        public List<PersonalizedContent> getContents() {
            return contents;
        }

        /**
         * Sets the content variants for this request.
         *
         * @param contents personalized content items
         */
        public void setContents(List<PersonalizedContent> contents) {
            this.contents = contents;
        }

        /**
         * Strategy-specific options passed to the personalization engine.
         *
         * @return strategy options, or {@code null} if none
         */
        public Map<String, Object> getStrategyOptions() {
            return strategyOptions;
        }

        /**
         * Sets strategy-specific options for this request.
         *
         * @param strategyOptions strategy options map
         */
        public void setStrategyOptions(Map<String, Object> strategyOptions) {
            this.strategyOptions = strategyOptions;
        }
    }

    /**
     * One content variant (also used as entries in {@code ContextRequest.filters}).
     */
    class PersonalizedContent {
        /**
         * Variant / filter id returned to the client when selected or when evaluating filters.
         * @api.example hero-a
         */
        private String id;
        /**
         * Optional filters; all applicable conditions must match for the variant to be eligible.
         */
        private List<Filter> filters;
        /**
         * Optional free-form metadata for the client (CMS labels, positions, …).
         * @api.example {"position":"top"}
         */
        private Map<String,Object> properties;

        /**
         * Content variant identifier.
         *
         * @return content id
         */
        public String getId() {
            return id;
        }

        /**
         * Sets the content variant identifier.
         *
         * @param id content id
         */
        public void setId(String id) {
            this.id = id;
        }

        /**
         * Sets the filter identifier associated with this content filtering definition.
         * @param filterid the filter identifier associated with this content filtering definition
         * @deprecated As of version 1.3.0-incubating, please use {@link #setId(String)} instead
         */
        @Deprecated
        public void setFilterid(String filterid) {
            this.id = filterid;
        }

        /**
         * Filters applied to this content variant.
         *
         * @return filter definitions
         */
        public List<Filter> getFilters() {
            return filters;
        }

        /**
         * Sets the filters for this content variant.
         *
         * @param filters filter definitions
         */
        public void setFilters(List<Filter> filters) {
            this.filters = filters;
        }

        /**
         * Additional properties attached to this content variant.
         *
         * @return content properties
         */
        public Map<String, Object> getProperties() {
            return properties;
        }

        /**
         * Sets additional properties for this content variant.
         *
         * @param properties content properties
         */
        public void setProperties(Map<String, Object> properties) {
            this.properties = properties;
        }
    }

    /**
     * Filter applied to a content variant: condition plus optional target hints.
     */
    class Filter {
        /**
         * Optional targets describing which profile/session dimensions the filter concerns.
         */
        private List<Target> appliesOn;
        /**
         * Unomi condition that must match the profile/session for this filter to pass.
         */
        private Condition condition;
        /**
         * Optional free-form filter metadata.
         * @api.example {"priority":1}
         */
        private Map<String,Object> properties;

        /**
         * Targets this filter should be evaluated against.
         *
         * @return applicable targets
         */
        public List<Target> getAppliesOn() {
            return appliesOn;
        }

        /**
         * Sets the targets this filter applies to.
         *
         * @param appliesOn applicable targets
         */
        public void setAppliesOn(List<Target> appliesOn) {
            this.appliesOn = appliesOn;
        }

        /**
         * Condition evaluated when applying this filter.
         *
         * @return filter condition
         */
        public Condition getCondition() {
            return condition;
        }

        /**
         * Sets the condition for this filter.
         *
         * @param condition filter condition
         */
        public void setCondition(Condition condition) {
            this.condition = condition;
        }

        /**
         * Additional properties for this filter.
         *
         * @return filter properties map
         */
        public Map<String, Object> getProperties() {
            return properties;
        }

        /**
         * Sets additional properties for this filter.
         *
         * @param properties filter properties map
         */
        public void setProperties(Map<String, Object> properties) {
            this.properties = properties;
        }
    }

    /**
     * Named dimension and allowed values used as filter metadata (client/CMS hinting).
     */
    class Target {
        /**
         * Dimension name (for example a property key or {@code segments}).
         * @api.example interests
         */
        private String target;
        /**
         * Allowed values for that dimension.
         * @api.example ["sports","news"]
         */
        private List<String> values;

        /**
         * Target dimension name (for example profile property or segment).
         *
         * @return target name
         */
        public String getTarget() {
            return target;
        }

        /**
         * Sets the target dimension name.
         *
         * @param target target name
         */
        public void setTarget(String target) {
            this.target = target;
        }

        /**
         * Allowed values for this target dimension.
         *
         * @return target values
         */
        public List<String> getValues() {
            return values;
        }

        /**
         * Sets allowed values for this target dimension.
         *
         * @param values target values
         */
        public void setValues(List<String> values) {
            this.values = values;
        }
    }
}
