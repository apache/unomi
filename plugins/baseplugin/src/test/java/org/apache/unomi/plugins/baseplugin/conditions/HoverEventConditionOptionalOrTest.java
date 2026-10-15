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

import org.apache.unomi.api.CustomItem;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.Item;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.persistence.spi.conditions.ConditionContextHelper;
import org.apache.unomi.persistence.spi.conditions.evaluator.ConditionEvaluatorDispatcher;
import org.junit.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * End-to-end coverage for UNOMI-1004: hover-like OR of optional target id / path
 * must only match on parameters that are set.
 */
public class HoverEventConditionOptionalOrTest {

    private final BooleanConditionEvaluator booleanEvaluator = new BooleanConditionEvaluator();
    private final PropertyConditionEvaluator propertyEvaluator = new PropertyConditionEvaluator();

    private final ConditionEvaluatorDispatcher dispatcher = new ConditionEvaluatorDispatcher() {
        @Override
        public void addEvaluator(String name, org.apache.unomi.persistence.spi.conditions.evaluator.ConditionEvaluator evaluator) {
        }

        @Override
        public void removeEvaluator(String name) {
        }

        @Override
        public boolean eval(Condition condition, Item item) {
            return eval(condition, item, new HashMap<>());
        }

        @Override
        public boolean eval(Condition condition, Item item, Map<String, Object> context) {
            Condition contextual = ConditionContextHelper.getContextualCondition(condition, context, null);
            if (contextual == null) {
                // Leaf with unset optional — boolean evaluator prunes before calling us;
                // if reached directly, treat as no constraint (AND-compatible).
                return true;
            }
            if (contextual == ConditionContextHelper.UNRESOLVABLE) {
                return false;
            }
            Object operator = contextual.getParameter("operator");
            if (operator != null) {
                return booleanEvaluator.eval(contextual, item, context, this);
            }
            return propertyEvaluator.eval(contextual, item, context, this);
        }
    };

    @Test
    public void onlyTargetId_matchesMatchingHover_andRejectsOtherTarget() {
        Condition hover = hoverOr();

        assertTrue(dispatcher.eval(hover, hoverEvent("button-a", "/other"), params("button-a", null)));
        assertFalse(dispatcher.eval(hover, hoverEvent("button-b", "/other"), params("button-a", null)));
    }

    @Test
    public void onlyTargetPath_matchesMatchingHover_andRejectsOtherPath() {
        Condition hover = hoverOr();

        assertTrue(dispatcher.eval(hover, hoverEvent("x", "/page-a"), params(null, "/page-a")));
        assertFalse(dispatcher.eval(hover, hoverEvent("x", "/page-b"), params(null, "/page-a")));
    }

    @Test
    public void bothSet_matchesEither() {
        Condition hover = hoverOr();

        assertTrue(dispatcher.eval(hover, hoverEvent("button-a", "/other"), params("button-a", "/page-a")));
        assertTrue(dispatcher.eval(hover, hoverEvent("other", "/page-a"), params("button-a", "/page-a")));
        assertFalse(dispatcher.eval(hover, hoverEvent("other", "/other"), params("button-a", "/page-a")));
    }

    @Test
    public void neitherSet_matchesNothing() {
        Condition hover = hoverOr();

        assertFalse(dispatcher.eval(hover, hoverEvent("button-a", "/page-a"), params(null, null)));
    }

    private static Condition hoverOr() {
        Condition or = new Condition();
        or.setParameter("operator", "or");
        or.setParameter("subConditions", Arrays.asList(
                optionalEquals("target.itemId", "targetId"),
                optionalEquals("target.properties.pageInfo.pagePath", "targetPath")));
        return or;
    }

    private static Condition optionalEquals(String propertyName, String parameterName) {
        Condition c = new Condition();
        c.setParameter("propertyName", propertyName);
        c.setParameter("propertyValue", "parameter::" + parameterName);
        c.setParameter("comparisonOperator", "equals");
        return c;
    }

    private static Map<String, Object> params(String targetId, String targetPath) {
        Map<String, Object> context = new HashMap<>();
        if (targetId != null) {
            context.put("targetId", targetId);
        }
        if (targetPath != null) {
            context.put("targetPath", targetPath);
        }
        return context;
    }

    private static Event hoverEvent(String targetId, String pagePath) {
        Profile profile = new Profile("profile-1");
        CustomItem target = new CustomItem();
        target.setItemId(targetId);
        target.setItemType("page");
        Map<String, Object> pageInfo = new HashMap<>();
        pageInfo.put("pagePath", pagePath);
        Map<String, Object> properties = new HashMap<>();
        properties.put("pageInfo", pageInfo);
        target.setProperties(properties);

        Event event = new Event("hover", null, profile, "systemscope", null, target, new Date());
        event.setItemId("event-1");
        return event;
    }
}
