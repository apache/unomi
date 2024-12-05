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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.unomi.plugins.request.useragent.UserAgent;
import org.apache.unomi.plugins.request.useragent.UserAgentDetectorServiceImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    }

    @Test
    public void testUserAgentDetectionPerformance() throws InterruptedException {
        int workerCount = 5000000;
        ExecutorService executorService = Executors.newFixedThreadPool(3000);

        for (int cpt = 1; cpt < 6; cpt++) {
            LOGGER.info("Execution {}/5", cpt);
            executeWorker(executorService, workerCount);
        }
    }

    private void executeWorker(ExecutorService executorService, int workerCount) throws InterruptedException {
        List<Callable<Object>> callables = new ArrayList<>(workerCount);
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < workerCount; i++) {
            callables.add(new AgentWorker(this.userAgentDetectorService));
        }
        executorService.invokeAll(callables);
        long totalTime = System.currentTimeMillis() - startTime;
        LOGGER.info("AgentWorker workers completed execution in {}ms", totalTime);
    }

    private class AgentWorker implements Callable<Object> {

        String header = "Mozilla/5.0 (Linux; Android 7.0; Nexus 6 Build/NBD90Z) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/53.0.2785.124 Mobile Safari/537.36";
        UserAgentDetectorServiceImpl service;

        public AgentWorker(UserAgentDetectorServiceImpl userAgentDetectorService) {
            this.service = userAgentDetectorService;
        }

        @Override
        public Object call() {
            this.service.parseUserAgent(header);
            return null;
        }
    }

}
