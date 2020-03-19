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

package org.apache.unomi.services.impl;

import org.apache.unomi.api.PropertyType;
import org.apache.unomi.api.ValueType;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionType;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.services.DefinitionsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Helper class to resolve condition, action and values types when loading definitions from JSON files
 */
public class ParserHelper {

    private static final Logger logger = LoggerFactory.getLogger(ParserHelper.class);

    private static final Set<String> unresolvedActionTypes = new HashSet<>();
    private static final Set<String> unresolvedConditionTypes = new HashSet<>();

    public static boolean resolveConditionType(final DefinitionsService definitionsService, Condition rootCondition) {
        if (rootCondition == null) {
            return false;
        }
        final List<String> result = new ArrayList<String>();
        visitConditions(rootCondition, new ConditionVisitor() {
            @Override
            public void visit(Condition condition) {
                if (condition.getConditionType() == null) {
                    ConditionType conditionType = definitionsService.getConditionType(condition.getConditionTypeId());
                    if (conditionType != null) {
                        unresolvedConditionTypes.remove(condition.getConditionTypeId());
                        condition.setConditionType(conditionType);
                    } else {
                        result.add(condition.getConditionTypeId());
                        if (!unresolvedConditionTypes.contains(condition.getConditionTypeId())) {
                            unresolvedConditionTypes.add(condition.getConditionTypeId());
                            logger.warn("Couldn't resolve condition type: " + condition.getConditionTypeId());
                        }
                    }
                }
            }
        });
        return result.isEmpty();
    }

    public static List<String> getConditionTypeIds(Condition rootCondition) {
        final List<String> result = new ArrayList<String>();
        visitConditions(rootCondition, new ConditionVisitor() {
            @Override
            public void visit(Condition condition) {
                result.add(condition.getConditionTypeId());
            }
        });
        return result;
    }

    private static void visitConditions(Condition rootCondition, ConditionVisitor visitor) {
        visitor.visit(rootCondition);
        // recursive call for sub-conditions as parameters
        for (Object parameterValue : rootCondition.getParameterValues().values()) {
            if (parameterValue instanceof Condition) {
                Condition parameterValueCondition = (Condition) parameterValue;
                visitConditions(parameterValueCondition, visitor);
            } else if (parameterValue instanceof Collection) {
                @SuppressWarnings("unchecked")
                Collection<Object> valueList = (Collection<Object>) parameterValue;
                for (Object value : valueList) {
                    if (value instanceof Condition) {
                        Condition valueCondition = (Condition) value;
                        visitConditions(valueCondition, visitor);
                    }
                }
            }
        }
    }

    public static boolean resolveActionTypes(DefinitionsService definitionsService, List<Action> actions) {
        boolean result = true;
        for (Action action : actions) {
            result &= ParserHelper.resolveActionType(definitionsService, action);
        }
        return result;
    }

    public static boolean resolveActionType(DefinitionsService definitionsService, Action action) {
        if (action.getActionType() == null) {
            ActionType actionType = definitionsService.getActionType(action.getActionTypeId());
            if (actionType != null) {
                unresolvedActionTypes.remove(action.getActionTypeId());
                action.setActionType(actionType);
            } else {
                if (!unresolvedActionTypes.contains(action.getActionTypeId())) {
                    logger.warn("Couldn't resolve action type : " + action.getActionTypeId());
                    unresolvedActionTypes.add(action.getActionTypeId());
                }
                return false;
            }
        }
        return true;
    }

    public static void resolveValueType(DefinitionsService definitionsService, PropertyType propertyType) {
        if (propertyType.getValueType() == null) {
            ValueType valueType = definitionsService.getValueType(propertyType.getValueTypeId());
            if (valueType != null) {
                propertyType.setValueType(valueType);
            }
        }
    }

    interface ConditionVisitor {
        void visit(Condition condition);
    }
}
