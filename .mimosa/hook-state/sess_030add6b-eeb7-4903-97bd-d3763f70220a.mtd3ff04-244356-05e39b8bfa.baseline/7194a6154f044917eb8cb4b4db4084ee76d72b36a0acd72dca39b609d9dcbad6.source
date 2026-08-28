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

import org.apache.unomi.api.Item;
import org.apache.unomi.api.tasks.ScheduledTask;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Persistence-format coverage for {@link ScheduledTask#getLockLeaseMillis()}.
 * <p>
 * The lock lease is a cross-node security decision (it decides who may declare a peer dead), so
 * its survival through the production serializer is not an implementation detail: a field that
 * silently fails to round-trip would degrade every observer to the legacy observer-timeout
 * fallback and quietly reintroduce the divergent-timeout double-execution bug. Both store read
 * paths are exercised: direct class binding, and the {@code Item}-dispatched path the persistence
 * services actually use ({@code readValue(json, Item.class)} via {@code ItemDeserializer}).
 */
public class ScheduledTaskLeaseSerializationTest {

    private CustomObjectMapper mapper;

    @BeforeEach
    public void setUp() {
        mapper = CustomObjectMapper.getCustomInstance();
        mapper.registerBuiltInItemTypeClass(ScheduledTask.ITEM_TYPE, ScheduledTask.class);
    }

    private ScheduledTask lockedTask() {
        ScheduledTask task = new ScheduledTask();
        task.setItemId("lease-serialization-test");
        task.setTaskType("lease-serialization-test");
        task.setStatus(ScheduledTask.TaskStatus.RUNNING);
        task.setLockOwner("node-a");
        task.setLockDate(new Date());
        task.setLockLeaseMillis(12345);
        return task;
    }

    @Test
    public void leaseSurvivesRoundTripViaDirectClassBinding() throws Exception {
        String json = mapper.writeValueAsString(lockedTask());
        assertTrue(json.contains("\"lockLeaseMillis\":12345"), "lease must be serialized: " + json);

        ScheduledTask back = mapper.readValue(json, ScheduledTask.class);
        assertEquals(12345, back.getLockLeaseMillis());
        assertEquals("node-a", back.getLockOwner());
    }

    @Test
    public void leaseSurvivesRoundTripViaItemDispatchedPath() throws Exception {
        // This is the path the persistence services use when loading store documents.
        String json = mapper.writeValueAsString(lockedTask());
        Item item = mapper.readValue(json, Item.class);
        assertTrue(item instanceof ScheduledTask, "itemType dispatch should yield a ScheduledTask");
        assertEquals(12345, ((ScheduledTask) item).getLockLeaseMillis());
    }

    /**
     * A document written BEFORE lease recording (no {@code lockLeaseMillis} field) must load with
     * lease 0, which {@code TaskLockManager#isLockExpired} treats as "fall back to the observer's
     * own timeout" — i.e. exactly the pre-lease behaviour, so a rolling upgrade cannot make old
     * locks unexpirable or instantly expired.
     */
    @Test
    public void legacyDocumentWithoutLeaseLoadsAsZero() throws Exception {
        String legacyJson = "{" +
            "\"itemId\":\"legacy-task\"," +
            "\"itemType\":\"scheduledTask\"," +
            "\"taskType\":\"legacy-task\"," +
            "\"status\":\"RUNNING\"," +
            "\"lockOwner\":\"old-node\"," +
            "\"lockDate\":\"2026-01-01T00:00:00Z\"" +
            "}";
        Item item = mapper.readValue(legacyJson, Item.class);
        ScheduledTask task = (ScheduledTask) item;
        assertEquals(0, task.getLockLeaseMillis(), "missing lease must read as 0 (legacy fallback)");
        assertEquals("old-node", task.getLockOwner());
    }

    /**
     * A document written by a NEWER version carrying a field this version does not know must
     * still deserialize (rolling upgrade window: older binaries keep reading scheduler state
     * written by upgraded peers). Pinned by {@code @JsonIgnoreProperties(ignoreUnknown = true)}
     * on ScheduledTask — without it, Jackson's default rejects the first unknown field and the
     * older node loses access to every task document the newer node has touched.
     */
    @Test
    public void documentFromNewerVersionWithUnknownFieldStillLoads() throws Exception {
        String futureJson = "{" +
            "\"itemId\":\"future-task\"," +
            "\"itemType\":\"scheduledTask\"," +
            "\"taskType\":\"future-task\"," +
            "\"status\":\"SCHEDULED\"," +
            "\"lockLeaseMillis\":5000," +
            "\"someFieldAddedInAFutureVersion\":\"whatever\"" +
            "}";
        Item item = mapper.readValue(futureJson, Item.class);
        assertNotNull(item);
        assertEquals(5000, ((ScheduledTask) item).getLockLeaseMillis());
    }
}
