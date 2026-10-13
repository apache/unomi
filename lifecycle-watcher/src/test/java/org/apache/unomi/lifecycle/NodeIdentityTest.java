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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeIdentityTest {

    private static NodeIdentity resolve(String configuredId, String storedId, String configuredName, String hostName) {
        return NodeIdentity.resolve(configuredId, storedId, configuredName, hostName, () -> "0a1b2c3d");
    }

    @Test
    void generatesIdFromNameWhenNothingIsConfiguredOrStored() {
        NodeIdentity identity = resolve(null, " ", null, "web-1");
        assertEquals("web-1-0a1b2c3d", identity.getNodeId());
        assertEquals("web-1", identity.getNodeName());
    }

    @Test
    void reusesStoredId() {
        assertEquals("web-1-99999999", resolve("", "web-1-99999999", null, "web-1").getNodeId());
    }

    @Test
    void storedIdDoesNotFollowNameChanges() {
        NodeIdentity identity = resolve(null, "web-1-99999999", "frontend", "web-2");
        assertEquals("web-1-99999999", identity.getNodeId());
        assertEquals("frontend", identity.getNodeName());
    }

    @Test
    void configuredIdWinsOverStoredId() {
        assertEquals("unomi-node-7", resolve(" unomi-node-7 ", "web-1-99999999", null, "web-1").getNodeId());
    }

    @Test
    void configuredNameWinsOverHostName() {
        NodeIdentity identity = resolve(null, null, "Frontend A", "web-1");
        assertEquals("Frontend A", identity.getNodeName());
        assertEquals("frontend-a-0a1b2c3d", identity.getNodeId());
    }

    @Test
    void fallsBackToDefaultNameWithoutHostName() {
        NodeIdentity identity = resolve(null, null, " ", null);
        assertEquals("unomi", identity.getNodeName());
        assertEquals("unomi-0a1b2c3d", identity.getNodeId());
    }

    @ParameterizedTest
    @CsvSource({
            "web-1, web-1",
            "Web_1.Example.COM, web-1-example-com",
            "'  node  ', node",
            "--a--, a",
            "éà, unomi",
            "0123456789012345678901234567890123456789-tail, 0123456789012345678901234567890123456789"
    })
    void idPrefixIsSafeForIdsLogsAndUrls(String nodeName, String expectedPrefix) {
        assertEquals(expectedPrefix, NodeIdentity.toIdPrefix(nodeName));
    }

    @Test
    void randomSuffixIsEightHexCharactersAndVaries() {
        String suffix = NodeIdentity.randomSuffix();
        assertTrue(suffix.matches("[0-9a-f]{8}"), suffix);
        assertNotEquals(suffix + NodeIdentity.randomSuffix(), NodeIdentity.randomSuffix() + NodeIdentity.randomSuffix());
    }
}
