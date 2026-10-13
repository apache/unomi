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
package org.apache.unomi.lifecycle;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

import java.io.IOException;
import java.util.Dictionary;
import java.util.Hashtable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * How the lifecycle bundle decides, remembers and publishes the identity of the node at startup.
 */
class BundleWatcherNodeIdentityTest {

    private static final String FILE_NAME_KEY = "felix.fileinstall.filename";
    private static final String FILE_NAME = "file:/opt/unomi/etc/org.apache.unomi.node.cfg";
    private static final String[] PROPERTIES = {
            BundleWatcherImpl.CLUSTER_NODE_ID_PROPERTY, BundleWatcherImpl.CLUSTER_NODE_NAME_PROPERTY,
            BundleWatcherImpl.SCHEDULER_NODE_ID_OVERRIDE_PROPERTY, BundleWatcherImpl.SCHEDULER_NODE_ID_PROPERTY};

    private ConfigurationAdmin configurationAdmin;
    private Configuration configuration;

    @BeforeEach
    void setUp() throws IOException {
        clearProperties();
        // Fixed name, so the tests do not depend on the host they run on
        System.setProperty(BundleWatcherImpl.CLUSTER_NODE_NAME_PROPERTY, "web-1");
        configuration = mock(Configuration.class);
        configurationAdmin = mock(ConfigurationAdmin.class);
        when(configurationAdmin.getConfiguration(NodeIdentity.PID, "?")).thenReturn(configuration);
    }

    @AfterEach
    void clearProperties() {
        for (String property : PROPERTIES) {
            System.clearProperty(property);
        }
    }

    /** What Karaf holds for the configuration once it has loaded etc/org.apache.unomi.node.cfg. */
    private void stored(String nodeId, String nodeName) {
        Dictionary<String, Object> properties = new Hashtable<>();
        properties.put(NodeIdentity.NODE_ID, nodeId);
        properties.put(NodeIdentity.NODE_NAME, nodeName);
        properties.put(FILE_NAME_KEY, FILE_NAME);
        when(configuration.getProperties()).thenReturn(properties);
    }

    /** One JVM start: Karaf sets the properties from the environment, then the lifecycle bundle starts. */
    private BundleWatcherImpl start() {
        BundleWatcherImpl bundleWatcher = new BundleWatcherImpl();
        bundleWatcher.setConfigurationAdmin(configurationAdmin);
        bundleWatcher.resolveNodeIdentity();
        return bundleWatcher;
    }

    @SuppressWarnings("unchecked")
    private Dictionary<String, Object> storedByUpdate() throws IOException {
        ArgumentCaptor<Dictionary<String, Object>> captor = ArgumentCaptor.forClass(Dictionary.class);
        verify(configuration).update(captor.capture());
        return captor.getValue();
    }

    @Test
    void firstStartGeneratesStoresAndPublishesTheId() throws IOException {
        stored("", "");

        BundleWatcherImpl bundleWatcher = start();

        String nodeId = bundleWatcher.getNodeId();
        assertTrue(nodeId.matches("web-1-[0-9a-f]{8}"), nodeId);
        assertEquals("web-1", bundleWatcher.getNodeName());

        Dictionary<String, Object> update = storedByUpdate();
        assertEquals(nodeId, update.get(NodeIdentity.NODE_ID));
        assertEquals("web-1", update.get(NodeIdentity.NODE_NAME));
        // Without it Karaf would no longer write the configuration back to its file in etc/
        assertEquals(FILE_NAME, update.get(FILE_NAME_KEY));

        // What the cluster and scheduler configurations are resolved with
        assertEquals(nodeId, System.getProperty(BundleWatcherImpl.CLUSTER_NODE_ID_PROPERTY));
        assertEquals(nodeId, System.getProperty(BundleWatcherImpl.SCHEDULER_NODE_ID_PROPERTY));
    }

    @Test
    void restartReusesTheStoredIdWithoutRewritingIt() throws IOException {
        stored("web-1-99999999", "web-1");

        BundleWatcherImpl bundleWatcher = start();

        assertEquals("web-1-99999999", bundleWatcher.getNodeId());
        assertEquals("web-1-99999999", System.getProperty(BundleWatcherImpl.CLUSTER_NODE_ID_PROPERTY));
        assertEquals("web-1-99999999", System.getProperty(BundleWatcherImpl.SCHEDULER_NODE_ID_PROPERTY));
        verify(configuration, never()).update(any());
    }

    @Test
    void renamingTheNodeKeepsItsId() throws IOException {
        stored("web-1-99999999", "web-1");
        System.setProperty(BundleWatcherImpl.CLUSTER_NODE_NAME_PROPERTY, "frontend");

        BundleWatcherImpl bundleWatcher = start();

        assertEquals("web-1-99999999", bundleWatcher.getNodeId());
        assertEquals("frontend", bundleWatcher.getNodeName());
        assertEquals("web-1-99999999", storedByUpdate().get(NodeIdentity.NODE_ID));
    }

    @Test
    void configuredIdWinsAndIsRememberedOnceTheVariableIsRemoved() throws IOException {
        stored("web-1-99999999", "web-1");
        System.setProperty(BundleWatcherImpl.CLUSTER_NODE_ID_PROPERTY, "unomi-node-7");

        assertEquals("unomi-node-7", start().getNodeId());
        Dictionary<String, Object> update = storedByUpdate();
        assertEquals("unomi-node-7", update.get(NodeIdentity.NODE_ID));

        // Next start without UNOMI_CLUSTER_NODEID: the id given back to the node stays
        clearProperties();
        System.setProperty(BundleWatcherImpl.CLUSTER_NODE_NAME_PROPERTY, "web-1");
        when(configuration.getProperties()).thenReturn(update);
        assertEquals("unomi-node-7", start().getNodeId());
    }

    @Test
    void blankingTheStoredIdGivesANewOne() throws IOException {
        stored("web-1-99999999", "web-1");
        String before = start().getNodeId();

        stored(" ", "web-1");
        clearProperties();
        System.setProperty(BundleWatcherImpl.CLUSTER_NODE_NAME_PROPERTY, "web-1");
        String after = start().getNodeId();

        assertEquals("web-1-99999999", before);
        assertTrue(after.matches("web-1-[0-9a-f]{8}") && !after.equals(before), after);
    }

    @Test
    void schedulerOverrideIsLeftAlone() {
        stored("web-1-99999999", "web-1");
        System.setProperty(BundleWatcherImpl.SCHEDULER_NODE_ID_OVERRIDE_PROPERTY, "scheduler-a");

        start();

        assertEquals("web-1-99999999", System.getProperty(BundleWatcherImpl.CLUSTER_NODE_ID_PROPERTY));
        assertNull(System.getProperty(BundleWatcherImpl.SCHEDULER_NODE_ID_PROPERTY));
    }

    @Test
    void worksWhenTheConfigurationDoesNotExistYet() throws IOException {
        when(configuration.getProperties()).thenReturn(null);

        String nodeId = start().getNodeId();

        assertTrue(nodeId.matches("web-1-[0-9a-f]{8}"), nodeId);
        assertEquals(nodeId, storedByUpdate().get(NodeIdentity.NODE_ID));
    }

    @Test
    void nodeStillGetsAnIdWhenItCannotBeStored() throws IOException {
        stored("", "");
        doThrow(new IOException("etc is read-only")).when(configuration).update(any());

        String nodeId = start().getNodeId();

        assertTrue(nodeId.matches("web-1-[0-9a-f]{8}"), nodeId);
        assertEquals(nodeId, System.getProperty(BundleWatcherImpl.CLUSTER_NODE_ID_PROPERTY));
    }

    @Test
    void nodeStillGetsAnIdWhenTheStoredIdentityCannotBeRead() throws IOException {
        when(configurationAdmin.getConfiguration(NodeIdentity.PID, "?")).thenThrow(new IOException("unavailable"));

        String nodeId = start().getNodeId();

        assertTrue(nodeId.matches("web-1-[0-9a-f]{8}"), nodeId);
        assertEquals(nodeId, System.getProperty(BundleWatcherImpl.SCHEDULER_NODE_ID_PROPERTY));
    }
}
