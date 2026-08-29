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
package org.apache.unomi.plugins.request.actions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.unomi.plugins.request.useragent.UserAgent;
import org.apache.unomi.plugins.request.useragent.UserAgentDetectorServiceImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class UserAgentDetectorTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserAgentDetectorTest.class.getName());

    private UserAgentDetectorServiceImpl userAgentDetectorService;

    @Before
    public void init() {
        long start = System.currentTimeMillis();
        this.userAgentDetectorService = new UserAgentDetectorServiceImpl();
        this.userAgentDetectorService.postConstruct();
        long end = System.currentTimeMillis();
        LOGGER.info("Duration starting user agent (in msec) > {}", end - start);
    }

    @After
    public void end() {
        this.userAgentDetectorService.preDestroy();
    }

    @Test
    public void testFirstUserAgentDetection() {
        String header = "Mozilla/5.0 (Linux; Android 7.0; Nexus 6 Build/NBD90Z) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/53.0.2785.124 Mobile Safari/537.36";

        long start = System.currentTimeMillis();
        UserAgent agent = this.userAgentDetectorService.parseUserAgent(header);
        long end = System.currentTimeMillis();
        LOGGER.info("Duration user agent parsing (in msec) > {}", end - start);
        LOGGER.info(agent.toString());

        assertEquals("Mobile", agent.getOperatingSystemFamily());
        assertEquals("Android", agent.getOperatingSystemName());
        assertEquals("Chrome", agent.getUserAgentName());
        assertEquals("53.0.2785.124", agent.getUserAgentVersion());
        assertEquals("Phone", agent.getDeviceCategory());
        assertEquals("Google", agent.getDeviceBrand());
        assertEquals("Google Nexus 6", agent.getDeviceName());
    }

    @Test
    public void testDesktopUserAgentDetection() {
        String header = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36";

        UserAgent agent = this.userAgentDetectorService.parseUserAgent(header);

        assertEquals("Desktop", agent.getOperatingSystemFamily());
        assertEquals("Windows NT", agent.getOperatingSystemName());
        assertEquals("Chrome", agent.getUserAgentName());
        assertEquals("91.0.4472.124", agent.getUserAgentVersion());
        assertEquals("Desktop", agent.getDeviceCategory());
    }

    /**
     * Concurrency smoke test: the analyzer is shared across threads in production (a single
     * OSGi service instance handling concurrent requests), so this exercises parseUserAgent()
     * under concurrent access and verifies every call returns a correctly parsed result -
     * not just "didn't throw". Iteration/thread counts are intentionally small (this is a
     * correctness-under-concurrency check, not a throughput benchmark); the original version
     * ran 25,000,000 parses across a 3000-thread pool with no assertions at all, which made
     * this test one of the slowest in the module while verifying nothing.
     */
    @Test
    public void testUserAgentDetectionUnderConcurrency() throws InterruptedException {
        int workerCount = 200;
        ExecutorService executorService = Executors.newFixedThreadPool(20);
        try {
            List<Callable<UserAgent>> callables = new ArrayList<>(workerCount);
            for (int i = 0; i < workerCount; i++) {
                callables.add(new AgentWorker(this.userAgentDetectorService));
            }
            long startTime = System.currentTimeMillis();
            List<Future<UserAgent>> results = executorService.invokeAll(callables);
            long totalTime = System.currentTimeMillis() - startTime;
            LOGGER.info("{} concurrent parses completed in {}ms", workerCount, totalTime);

            for (Future<UserAgent> result : results) {
                UserAgent agent = result.get();
                assertNotNull("Every concurrent parse must return a result", agent);
                assertEquals("Chrome", agent.getUserAgentName());
                assertEquals("Android", agent.getOperatingSystemName());
                assertEquals("Google Nexus 6", agent.getDeviceName());
            }
        } catch (ExecutionException e) {
            throw new AssertionError("A concurrent parseUserAgent() call failed", e);
        } finally {
            executorService.shutdown();
        }
    }

    private static class AgentWorker implements Callable<UserAgent> {

        String header = "Mozilla/5.0 (Linux; Android 7.0; Nexus 6 Build/NBD90Z) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/53.0.2785.124 Mobile Safari/537.36";
        UserAgentDetectorServiceImpl service;

        public AgentWorker(UserAgentDetectorServiceImpl userAgentDetectorService) {
            this.service = userAgentDetectorService;
        }

        @Override
        public UserAgent call() {
            return this.service.parseUserAgent(header);
        }
    }

}
