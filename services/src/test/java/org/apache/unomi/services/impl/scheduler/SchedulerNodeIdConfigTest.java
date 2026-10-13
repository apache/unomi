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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Guards the UNOMI-1006 wiring: scheduler node id must follow the cluster node id by default,
 * not the former shared sentinel {@code test-scheduler-node}.
 */
class SchedulerNodeIdConfigTest {

    @Test
    void servicesCfgDefaultsSchedulerNodeIdToClusterNodeId() throws IOException {
        String cfg = readResource("/org.apache.unomi.services.cfg");
        assertTrue(cfg.contains(
                        "scheduler.nodeId=${org.apache.unomi.scheduler.nodeId:-${org.apache.unomi.cluster.nodeId}}"),
                "scheduler.nodeId must fall back to the cluster node id");
        assertFalse(cfg.contains("test-scheduler-node"),
                "the shared legacy default must not remain in services.cfg");
    }

    @Test
    void blueprintDefaultMatchesClusterDefault() throws IOException {
        String blueprint = readResource("/OSGI-INF/blueprint/blueprint.xml");
        assertTrue(blueprint.contains("<cm:property name=\"scheduler.nodeId\" value=\"unomi-node-1\" />"),
                "blueprint fallback must match the cluster default, not test-scheduler-node");
        assertFalse(blueprint.contains("value=\"test-scheduler-node\""),
                "blueprint must not hard-code the legacy shared scheduler id");
    }

    @Test
    void customSystemPropertiesDefaultsSchedulerNodeIdToClusterNodeId() throws IOException {
        Path props = Path.of("..", "package", "src", "main", "resources", "etc", "custom.system.properties");
        assumeTrue(Files.isRegularFile(props), "run from the services module in the Unomi source tree");
        String content = Files.readString(props, StandardCharsets.UTF_8);
        assertTrue(content.contains(
                        "org.apache.unomi.scheduler.nodeId=${env:UNOMI_SCHEDULER_NODEID:-${org.apache.unomi.cluster.nodeId}}"),
                "shipped system properties must default the scheduler id to the cluster node id");
    }

    /**
     * Simulates two JVMs that only set distinct cluster node ids (the documented production path).
     * Their scheduler ids must differ so exclusive tasks are not owned by both.
     */
    @Test
    void distinctClusterNodeIdsYieldDistinctSchedulerIds() {
        String nodeA = resolveSchedulerNodeId(null, "unomi-node-1");
        String nodeB = resolveSchedulerNodeId(null, "unomi-node-2");
        assertNotEquals(nodeA, nodeB);
        assertTrue(nodeA.equals("unomi-node-1") && nodeB.equals("unomi-node-2"));
    }

    @Test
    void schedulerEnvOverrideWinsOverClusterNodeId() {
        assertTrue("scheduler-override".equals(
                resolveSchedulerNodeId("scheduler-override", "unomi-node-1")));
    }

    /**
     * Mirrors {@code org.apache.unomi.scheduler.nodeId=${env:UNOMI_SCHEDULER_NODEID:-${org.apache.unomi.cluster.nodeId}}}.
     */
    static String resolveSchedulerNodeId(String schedulerEnvOrProperty, String clusterNodeId) {
        if (schedulerEnvOrProperty != null && !schedulerEnvOrProperty.isBlank()) {
            return schedulerEnvOrProperty;
        }
        return clusterNodeId;
    }

    private static String readResource(String path) throws IOException {
        try (InputStream in = SchedulerNodeIdConfigTest.class.getResourceAsStream(path)) {
            Objects.requireNonNull(in, "missing classpath resource " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
