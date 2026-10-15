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
package org.apache.unomi.plugins.hoverevent;

import org.apache.unomi.api.CustomItem;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.utils.ParserHelper;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.apache.unomi.persistence.spi.conditions.evaluator.impl.ConditionEvaluatorDispatcherImpl;
import org.apache.unomi.plugins.baseplugin.conditions.BooleanConditionEvaluator;
import org.apache.unomi.plugins.baseplugin.conditions.PropertyConditionEvaluator;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * UNOMI-1004: the shipped hoverEventCondition, evaluated through the real dispatcher, must only
 * match on the target id or path that is set.
 */
public class HoverEventConditionTest {

    private final ConditionEvaluatorDispatcherImpl dispatcher = new ConditionEvaluatorDispatcherImpl();
    private ConditionType hoverType;

    @Before
    public void setUp() throws IOException {
        DefinitionsService definitionsService = mock(DefinitionsService.class);
        when(definitionsService.getConditionType("booleanCondition"))
                .thenReturn(type("booleanCondition", "booleanConditionEvaluator"));
        when(definitionsService.getConditionType("eventPropertyCondition"))
                .thenReturn(type("eventPropertyCondition", "propertyConditionEvaluator"));

        hoverType = CustomObjectMapper.getObjectMapper().readValue(
                getClass().getResource("/META-INF/cxs/conditions/hoverEventCondition.json"), ConditionType.class);
        assertTrue(ParserHelper.resolveConditionType(definitionsService, hoverType.getParentCondition(), "test"));

        dispatcher.setDefinitionsService(definitionsService);
        dispatcher.addEvaluator("booleanConditionEvaluator", new BooleanConditionEvaluator());
        dispatcher.addEvaluator("propertyConditionEvaluator", new PropertyConditionEvaluator());
    }

    @Test
    public void onlyTargetId_matchesMatchingHover_andRejectsOtherTarget() {
        assertTrue(eval(hover("button-a", null), event("hover", "button-a", "/other")));
        assertFalse(eval(hover("button-a", null), event("hover", "button-b", "/other")));
    }

    @Test
    public void onlyTargetPath_matchesMatchingHover_andRejectsOtherPath() {
        assertTrue(eval(hover(null, "/page-a"), event("hover", "x", "/page-a")));
        assertFalse(eval(hover(null, "/page-a"), event("hover", "x", "/page-b")));
    }

    @Test
    public void bothSet_matchesEither() {
        assertTrue(eval(hover("button-a", "/page-a"), event("hover", "button-a", "/other")));
        assertTrue(eval(hover("button-a", "/page-a"), event("hover", "other", "/page-a")));
        assertFalse(eval(hover("button-a", "/page-a"), event("hover", "other", "/other")));
    }

    @Test
    public void neitherSet_matchesNothing() {
        assertFalse(eval(hover(null, null), event("hover", "button-a", "/page-a")));
    }

    @Test
    public void otherEventType_neverMatches() {
        assertFalse(eval(hover("button-a", null), event("view", "button-a", "/other")));
    }

    private boolean eval(Condition condition, Event event) {
        return dispatcher.eval(condition, event, new HashMap<>());
    }

    private Condition hover(String targetId, String targetPath) {
        Condition hover = new Condition(hoverType);
        if (targetId != null) {
            hover.setParameter("targetId", targetId);
        }
        if (targetPath != null) {
            hover.setParameter("targetPath", targetPath);
        }
        return hover;
    }

    private static ConditionType type(String id, String evaluator) {
        ConditionType type = new ConditionType();
        type.setItemId(id);
        type.setConditionEvaluator(evaluator);
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
