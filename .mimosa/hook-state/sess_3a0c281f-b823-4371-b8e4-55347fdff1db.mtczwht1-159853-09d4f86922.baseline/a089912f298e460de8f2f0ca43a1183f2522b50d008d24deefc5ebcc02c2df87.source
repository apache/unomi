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

import org.apache.unomi.api.Event;
import org.apache.unomi.api.Item;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.rules.Rule;
import org.apache.unomi.api.rules.RuleStatistics;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads, saves, and searches {@link org.apache.unomi.api.rules.Rule} definitions.
 * Rules tie conditions to actions and drive most automated behavior when
 * events arrive.
 */
public interface RulesService {

    /**
     * Returns metadata for all in-memory rules.
     * Does not query persistence directly.
     *
     * @return rule metadata from the in-memory cache
     */
    Set<Metadata> getRuleMetadatas();

    /**
     * Returns metadata for rules matching the given query.
     *
     * @param query filter for rules whose metadata should be returned
     * @return matching rule metadata
     */
    PartialList<Metadata> getRuleMetadatas(Query query);

    /**
     * Returns full rule definitions matching the given query.
     *
     * @param query filter for rules to return
     * @return matching rules
     */
    PartialList<Rule> getRuleDetails(Query query);

    /**
     * Returns all rules from the in-memory cache.
     * The cache refreshes on a configurable interval (default one second).
     *
     * @return all cached rules
     */
    List<Rule> getAllRules();

    /**
     * Loads a rule by id.
     *
     * @param ruleId rule identifier
     * @return matching rule, or {@code null} if none exists
     */
    Rule getRule(String ruleId);

    /**
     * Returns execution statistics for a rule.
     *
     * @param ruleId rule identifier
     * @return rule match and execution counts
     */
    RuleStatistics getRuleStatistics(String ruleId);

    /**
     * Returns execution statistics for all rules.
     *
     * @return map of rule id to statistics
     */
    Map<String,RuleStatistics> getAllRuleStatistics();

    /**
     * Resets match and execution counters for every rule.
     */
    void resetAllRuleStatistics();

    /**
     * Persists a rule definition.
     *
     * @param rule rule to save
     */
    void setRule(Rule rule);

    /**
     * Deletes a rule by id.
     *
     * @param ruleId rule identifier
     */
    void removeRule(String ruleId);

    /**
     * Returns tracked conditions whose source-event condition matches the given item.
     * Tracked conditions are rules tagged with {@code trackedCondition}.
     *
     * @param item item to match against source-event conditions
     * @return matching tracked conditions
     */
    Set<Condition> getTrackedConditions(Item item);

    /**
     * Returns rules whose conditions match the given event.
     *
     * @param event event to evaluate
     * @return matching rules
     */
    public Set<Rule> getMatchingRules(Event event);

    /**
     * Reloads rules from persistence into the in-memory cache.
     */
    public void refreshRules();

}
