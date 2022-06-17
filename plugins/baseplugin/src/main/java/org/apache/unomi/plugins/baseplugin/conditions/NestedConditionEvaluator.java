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

package org.apache.unomi.plugins.baseplugin.conditions;

import org.apache.commons.lang3.StringUtils;
import org.apache.unomi.api.Item;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.Session;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.persistence.elasticsearch.conditions.ConditionEvaluator;
import org.apache.unomi.persistence.elasticsearch.conditions.ConditionEvaluatorDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class NestedConditionEvaluator implements ConditionEvaluator {

    private static final Logger logger = LoggerFactory.getLogger(NestedConditionEvaluator.class.getName());

    PropertyConditionEvaluator propertyConditionEvaluator;

    public void setPropertyConditionEvaluator(PropertyConditionEvaluator propertyConditionEvaluator) {
        this.propertyConditionEvaluator = propertyConditionEvaluator;
    }

    @Override
    public boolean eval(Condition condition, Item item, Map<String, Object> context, ConditionEvaluatorDispatcher dispatcher) {
        String path = (String) condition.getParameter("path");
        Condition subCondition = (Condition) condition.getParameter("subCondition");

        if (subCondition == null || path == null) {
            throw new IllegalArgumentException("Impossible to build Nested evaluator, subCondition and path properties should be provided");
        }


        try {
            // Get list of nested items to be evaluated
            Object nestedItems = propertyConditionEvaluator.getPropertyValue(item, path);
            if (nestedItems instanceof List) {

                // Evaluated each nested items until one match the nested condition
                for (Object nestedItem : (List<Object>) nestedItems) {
                    if (nestedItem instanceof Map) {
                        Map<String, Object> flattenedNestedItem = flattenNestedItem(path, (Map<String, Object>) nestedItem);
                        Item finalNestedItem = createFinalNestedItemForEvaluation(item, path, flattenedNestedItem);
                        if (finalNestedItem != null && dispatcher.eval(subCondition, finalNestedItem, context)) {
                            // We found at least one nested item matching
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to evaluated nested condition", e);
            return false;
        }
        return false;
    }

    protected Map<String, Object> flattenNestedItem(String path, Map<String, Object> nestedItem) {
        Map<String, Object> flattenNestedItem = new HashMap<>();

        // Merge the nested Item in flat properties
        if (StringUtils.isNotEmpty(path)) {
            // substring to keep only the part after "properties".
            // For example in case of "properties.interests", we only want to keep "interests"
            String propertyPath = StringUtils.substringAfter(path, ".");
            if (StringUtils.isNotEmpty(propertyPath)) {
                String[] propertyKeys = propertyPath.split("\\.");
                Iterator<String> propertyKeysIterator = Arrays.stream(propertyKeys).iterator();

                Map<String, Object> currentPropertiesLevel = flattenNestedItem;
                while (propertyKeysIterator.hasNext()) {
                    String propertyKey = propertyKeysIterator.next();
                    if (!propertyKeysIterator.hasNext()) {
                        // last property, we set the nested Item
                        currentPropertiesLevel.put(propertyKey, nestedItem);
                    } else {
                        // new level of prop
                        Map<String, Object> subLevel = new HashMap<>();
                        currentPropertiesLevel.put(propertyKey, subLevel);
                        currentPropertiesLevel = subLevel;
                    }
                }
            }
        }

        return flattenNestedItem;
    }

    protected Item createFinalNestedItemForEvaluation(Item parentItem, String path, Map<String, Object> flattenedNestedItem) {
        // Build a basic Item with merged properties
        if (parentItem instanceof Profile) {
            Profile profile = new Profile(parentItem.getItemId());
            if (path.startsWith("properties.")) {
                profile.setProperties(flattenedNestedItem);
            } else if (path.startsWith("systemProperties.")) {
                profile.setSystemProperties(flattenedNestedItem);
            }
            return profile;
        } else if (parentItem instanceof Session) {
            Session session = new Session();
            if (path.startsWith("properties.")) {
                session.setProperties(flattenedNestedItem);
            } else if (path.startsWith("systemProperties.")) {
                session.setSystemProperties(flattenedNestedItem);
            }
            return session;
        }

        return null;
    }
}
