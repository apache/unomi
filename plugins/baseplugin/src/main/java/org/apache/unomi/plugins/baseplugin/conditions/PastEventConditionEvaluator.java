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

import org.apache.unomi.api.Event;
import org.apache.unomi.api.Item;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.persistence.elasticsearch.conditions.ConditionEvaluator;
import org.apache.unomi.persistence.elasticsearch.conditions.ConditionEvaluatorDispatcher;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.scripting.ScriptExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PastEventConditionEvaluator implements ConditionEvaluator {

    private PersistenceService persistenceService;
    private DefinitionsService definitionsService;
    private ScriptExecutor scriptExecutor;

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    public void setScriptExecutor(ScriptExecutor scriptExecutor) {
        this.scriptExecutor = scriptExecutor;
    }

    @Override
    public boolean eval(Condition condition, Item item, Map<String, Object> context, ConditionEvaluatorDispatcher dispatcher) {
        final Map<String, Object> parameters = condition.getParameterValues();
        long count;

        if (parameters.containsKey("generatedPropertyKey")) {
            String key = (String) parameters.get("generatedPropertyKey");
            Profile profile = (Profile) item;
            List<Map<String, Object>> pastEvents =  (ArrayList<Map<String, Object>>) profile.getSystemProperties().get("pastEvents");
            if (pastEvents != null) {
                Number l = (Number) pastEvents
                        .stream()
                        .filter(pastEvent -> pastEvent.get("key").equals(key))
                        .findFirst()
                        .map(pastEvent -> pastEvent.get("count")).orElse(0L);
                count = l.longValue();
            } else {
                count = 0;
            }
        } else {
            // TODO see for deprecation, this should not happen anymore each past event condition should have a generatedPropertyKey
            count = persistenceService.queryCount(PastEventConditionESQueryBuilder.getEventCondition(condition, context, item.getItemId(), definitionsService, scriptExecutor), Event.ITEM_TYPE);
        }

        boolean eventsOccurred = PastEventConditionESQueryBuilder.getStrategyFromOperator((String) condition.getParameter("operator"));
        if (eventsOccurred) {
            int minimumEventCount = parameters.get("minimumEventCount") == null  ? 0 : (Integer) parameters.get("minimumEventCount");
            int maximumEventCount = parameters.get("maximumEventCount") == null  ? Integer.MAX_VALUE : (Integer) parameters.get("maximumEventCount");
            return count > 0 && (count >= minimumEventCount && count <= maximumEventCount);
        } else {
            return count == 0;
        }
    }
}
