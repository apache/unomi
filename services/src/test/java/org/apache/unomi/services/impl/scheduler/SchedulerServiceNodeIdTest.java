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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SchedulerServiceNodeIdTest {

    @Test
    void validateNodeIdKeepsConfiguredValue() {
        SchedulerServiceImpl scheduler = new SchedulerServiceImpl();
        scheduler.setNodeId("unomi-node-2");
        scheduler.validateNodeId();
        assertEquals("unomi-node-2", scheduler.getNodeId());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void validateNodeIdRejectsBlank(String blankNodeId) {
        SchedulerServiceImpl scheduler = new SchedulerServiceImpl();
        scheduler.setNodeId(blankNodeId);
        assertThrows(IllegalStateException.class, scheduler::validateNodeId);
    }

    @Test
    void postConstructFailsOnBlankNodeIdLikeTheClusterService() {
        SchedulerServiceImpl scheduler = new SchedulerServiceImpl();
        assertThrows(IllegalStateException.class, scheduler::postConstruct);
    }

    @Test
    void validateNodeIdAcceptsLegacyDefault() {
        SchedulerServiceImpl scheduler = new SchedulerServiceImpl();
        scheduler.setNodeId(SchedulerServiceImpl.LEGACY_DEFAULT_NODE_ID);
        scheduler.validateNodeId();
        assertEquals(SchedulerServiceImpl.LEGACY_DEFAULT_NODE_ID, scheduler.getNodeId());
    }
}
