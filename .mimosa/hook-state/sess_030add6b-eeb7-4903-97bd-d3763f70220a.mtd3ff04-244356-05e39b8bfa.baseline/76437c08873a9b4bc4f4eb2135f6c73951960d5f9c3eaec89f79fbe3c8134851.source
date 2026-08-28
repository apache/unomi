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
package org.apache.unomi.services.impl.scheduler;

import org.apache.unomi.api.conditions.ConditionType;

/**
 * Constants used across scheduler implementation classes.
 */
public final class SchedulerConstants {
    private SchedulerConstants() {
        // Prevent instantiation
    }

    public static final ConditionType PROPERTY_CONDITION_TYPE = new ConditionType();
    public static final ConditionType BOOLEAN_CONDITION_TYPE = new ConditionType();

    static {
        PROPERTY_CONDITION_TYPE.setItemId("propertyCondition");
        PROPERTY_CONDITION_TYPE.setItemType(ConditionType.ITEM_TYPE);
        PROPERTY_CONDITION_TYPE.setConditionEvaluator("propertyConditionEvaluator");
        PROPERTY_CONDITION_TYPE.setQueryBuilder("propertyConditionQueryBuilder");

        BOOLEAN_CONDITION_TYPE.setItemId("booleanCondition");
        BOOLEAN_CONDITION_TYPE.setItemType(ConditionType.ITEM_TYPE);
        BOOLEAN_CONDITION_TYPE.setConditionEvaluator("booleanConditionEvaluator");
        BOOLEAN_CONDITION_TYPE.setQueryBuilder("booleanConditionQueryBuilder");
    }

    // Task execution constants
    public static final int MAX_HISTORY_SIZE = 10;
    public static final long DEFAULT_LOCK_TIMEOUT = 5 * 60 * 1000; // 5 minutes
    public static final int MIN_THREAD_POOL_SIZE = 4;
    public static final long TASK_CHECK_INTERVAL = 1000; // 1 second
} 