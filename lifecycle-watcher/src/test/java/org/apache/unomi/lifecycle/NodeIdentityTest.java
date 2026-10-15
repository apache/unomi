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

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeIdentityTest {

    private static NodeIdentity resolve(String configuredId, String storedId, String configuredName, String hostName) {
        return NodeIdentity.resolve(configuredId, storedId, configuredName, hostName, () -> "0a1b2c3d");
    }

    @Test
    void generatesIdFromNameWhenNothingIsConfiguredOrStored() {
        NodeIdentity identity = resolve(null, " ", null, "web-1");
        assertEquals("web-1-0a1b2c3d", identity.nodeId);
        assertEquals("web-1", identity.nodeName);
        assertEquals("generated", identity.source);
    }

    @Test
    void storedIdIsReusedAndDoesNotFollowNameChanges() {
        NodeIdentity identity = resolve("", "web-1-99999999", "frontend", "web-2");
        assertEquals("web-1-99999999", identity.nodeId);
        assertEquals("frontend", identity.nodeName);
        assertEquals("stored", identity.source);
    }

    @Test
    void configuredIdWinsOverStoredId() {
        NodeIdentity identity = resolve(" unomi-node-7 ", "web-1-99999999", null, "web-1");
        assertEquals("unomi-node-7", identity.nodeId);
        assertEquals("configured", identity.source);
    }

    @Test
    void configuredNameWinsOverHostName() {
        NodeIdentity identity = resolve(null, null, "Frontend A", "web-1");
        assertEquals("Frontend A", identity.nodeName);
        assertEquals("frontend-a-0a1b2c3d", identity.nodeId);
    }

    @Test
    void fallsBackToDefaultNameWithoutHostName() {
        NodeIdentity identity = resolve(null, null, " ", null);
        assertEquals("unomi", identity.nodeName);
        assertEquals("unomi-0a1b2c3d", identity.nodeId);
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
        Set<String> suffixes = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String suffix = NodeIdentity.randomSuffix();
            assertTrue(suffix.matches("[0-9a-f]{8}"), suffix);
            suffixes.add(suffix);
        }
        assertTrue(suffixes.size() > 190, "suffixes should differ from one another");
    }
}
