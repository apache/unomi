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
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.persistence.spi.conditions.evaluator.impl.ConditionEvaluatorDispatcherImpl;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * UNOMI-1004 boolean semantics for unset optional parameters, through the real dispatcher, which
 * expands condition types into their parent condition. The shipped hover condition is covered in
 * the hover-event plugin.
 */
public class BooleanConditionOptionalParameterTest {

    private final ConditionEvaluatorDispatcherImpl dispatcher = new ConditionEvaluatorDispatcherImpl();
    private final ConditionType booleanType = type("booleanCondition", "booleanConditionEvaluator", null);
    private final ConditionType eventPropertyType = type("eventPropertyCondition", "propertyConditionEvaluator", null);

    @Before
    public void setUp() {
        dispatcher.setDefinitionsService(mock(DefinitionsService.class));
        dispatcher.addEvaluator("booleanConditionEvaluator", new BooleanConditionEvaluator());
        dispatcher.addEvaluator("propertyConditionEvaluator", new PropertyConditionEvaluator());
    }

    @Test
    public void typedSubConditionWithUnsetParameter_isOmittedUnderOr() {
        // A type whose parent pins the event type and compares a target id. The dispatcher merges a
        // typed condition's own parameters into its expanded parent, so one unset parameter voids
        // the whole typed condition; under OR it must be omitted, not count as a match.
        Condition hoverTargetParent = booleanCondition("and",
                eventProperty("eventType", "hover"),
                eventProperty("target.itemId", "parameter::targetId"));
        Condition sub = new Condition(type("hoverTargetCondition", null, hoverTargetParent));
        sub.setParameter("targetId", "parameter::unsetTargetId");
        Condition or = booleanCondition("or", sub, eventProperty("eventType", "view"));

        assertFalse(dispatcher.eval(or, event("hover", "x", "/p"), new HashMap<>()));
        assertTrue(dispatcher.eval(or, event("view", "x", "/p"), new HashMap<>()));
    }

    @Test
    public void nestedAndWithAllOptionalsUnset_matchesUnderOr() {
        // An AND with nothing left to test states no constraint (UNOMI-982), so an OR holding it
        // matches. This is the 2.x and 3.x behaviour, kept on purpose.
        Condition unsetAnd = booleanCondition("and",
                eventProperty("target.itemId", "parameter::unsetTargetId"),
                eventProperty("target.properties.pageInfo.pagePath", "parameter::unsetTargetPath"));
        Condition or = booleanCondition("or", unsetAnd, eventProperty("eventType", "never"));

        assertTrue(dispatcher.eval(or, event("view", "x", "/p"), new HashMap<>()));
    }

    private Condition booleanCondition(String operator, Condition... subConditions) {
        Condition condition = new Condition(booleanType);
        condition.setParameter("operator", operator);
        condition.setParameter("subConditions", Arrays.asList(subConditions));
        return condition;
    }

    private Condition eventProperty(String propertyName, String propertyValue) {
        Condition condition = new Condition(eventPropertyType);
        condition.setParameter("propertyName", propertyName);
        condition.setParameter("propertyValue", propertyValue);
        condition.setParameter("comparisonOperator", "equals");
        return condition;
    }

    private static ConditionType type(String id, String evaluator, Condition parentCondition) {
        ConditionType type = new ConditionType();
        type.setItemId(id);
        type.setConditionEvaluator(evaluator);
        type.setParentCondition(parentCondition);
        return type;
    }

    private static Event event(String eventType, String targetId, String pagePath) {
        CustomItem target = new CustomItem();
        target.setItemId(targetId);
        target.setItemType("page");
        Map<String, Object> pageInfo = new HashMap<>();
        pageInfo.put("pagePath", pagePath);
        Map<String, Object> properties = new HashMap<>();
        properties.put("pageInfo", pageInfo);
        target.setProperties(properties);

        Event event = new Event(eventType, null, new Profile("profile-1"), "systemscope", null, target, new Date());
        event.setItemId("event-1");
        return event;
    }
}
