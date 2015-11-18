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

import org.apache.unomi.api.Item;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.rules.Rule;

import java.util.Set;

/**
 * A service to access and operate on {@link Rule}s.
 */
public interface RulesService {

    /**
     * Retrieves the metadata for all known rules.
     *
     * @return the Set of known metadata
     */
    Set<Metadata> getRuleMetadatas();

    /**
     * Retrieves rule metadatas for rules matching the specified {@link Query}.
     *
     * @param query the query the rules which metadata we want to retrieve must match
     * @return a {@link PartialList} of rules metadata for the rules matching the specified query
     */
    PartialList<Metadata> getRuleMetadatas(Query query);

    /**
     * Retrieves the rule identified by the specified identifier.
     *
     * @param ruleId the identifier of the rule we want to retrieve
     * @return the rule identified by the specified identifier or {@code null} if no such rule exists.
     */
    Rule getRule(String ruleId);

    /**
     * Persists the specified rule to the context server.
     *
     * @param rule the rule to be persisted
     */
    void setRule(Rule rule);

    /**
     * Deletes the rule identified by the specified identifier.
     *
     * @param ruleId the identifier of the rule we want to delete
     */
    void removeRule(String ruleId);

    /**
     * Retrieves tracked conditions (rules with a condition marked with the {@code trackedCondition} tag and which {@code sourceEventCondition} matches the specified item) for the
     * specified item.
     *
     * @param item the item which tracked conditions we want to retrieve
     * @return the Set of tracked conditions for the specified item
     */
    Set<Condition> getTrackedConditions(Item item);
}
