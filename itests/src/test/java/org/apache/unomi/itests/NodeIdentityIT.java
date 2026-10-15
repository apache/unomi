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
package org.apache.unomi.itests;

import org.apache.unomi.api.ClusterNode;
import org.apache.unomi.api.services.ClusterService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Properties;

import static org.junit.Assert.*;

/**
 * The ITs configure no node id, so the container goes through what a fresh install does: the lifecycle
 * bundle generates an id, Karaf hands it to the cluster and scheduler services through their
 * configurations, and it is written back to etc/ for the next start.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class NodeIdentityIT extends BaseIT {

    private static final String NODE_CONFIG_FILE = "org.apache.unomi.node.cfg";

    @Test
    public void testGeneratedNodeIdIsUsedByClusterAndSchedulerAndStored() throws Exception {
        String nodeId = bundleWatcher.getNodeId();
        String nodeName = bundleWatcher.getNodeName();
        assertNotNull("The lifecycle bundle should have resolved a node id", nodeId);
        assertTrue("Node id " + nodeId + " should be generated as <name>-<8 hex characters>", nodeId.matches(".+-[0-9a-f]{8}"));
        assertFalse("The node should have a name", nodeName == null || nodeName.trim().isEmpty());

        assertEquals("The scheduler should use the node id", nodeId, schedulerService.getNodeId());

        ClusterService clusterService = getOsgiService(ClusterService.class, 600000);
        ClusterNode clusterNode = keepTrying("Node " + nodeId + " should be registered in the cluster",
                () -> clusterService.getClusterNodes().stream().filter(node -> nodeId.equals(node.getItemId())).findFirst().orElse(null),
                Objects::nonNull, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
        assertEquals("The cluster record should carry the node name", nodeName, clusterNode.getNodeName());
        assertEquals("The cluster record should say the node runs tasks", Boolean.TRUE, clusterNode.getExecutorNode());

        // Karaf writes the configuration back to its file asynchronously
        Path nodeConfigFile = Paths.get(System.getProperty("karaf.etc"), NODE_CONFIG_FILE);
        Properties stored = keepTrying("Node id " + nodeId + " should be stored in " + nodeConfigFile,
                () -> load(nodeConfigFile), properties -> nodeId.equals(properties.getProperty("nodeId")),
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
        assertEquals(nodeName, stored.getProperty("nodeName"));
        assertTrue("The explanations in " + NODE_CONFIG_FILE + " should survive the update",
                new String(Files.readAllBytes(nodeConfigFile), StandardCharsets.UTF_8).contains("Identity of this Apache Unomi node"));
    }

    private static Properties load(Path file) {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            // Not there yet, or being written: the caller retries
        }
        return properties;
    }
}
