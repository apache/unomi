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
package org.apache.unomi.plugins.baseplugin.conditions;

import org.apache.unomi.api.CustomItem;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.Session;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static org.apache.unomi.plugins.baseplugin.conditions.PropertyConditionEvaluator.NOT_OPTIMIZED_MARKER;

public class PropertyConditionEvaluatorTest {

    public static final String MOCK_ITEM_ID = "mockItemId";
    public static final String DIGITALL_SCOPE = "digitall";
    public static final String PAGE_PATH_VALUE = "/site/en/home/aboutus.html";
    public static final String PAGE_URL_VALUE = "http://localhost:8080/site/en/home/aboutus.html";
    public static final Date SESSION_LAST_EVENT_DATE = new Date();
    private static PropertyConditionEvaluator propertyConditionEvaluator = new PropertyConditionEvaluator();
    private static Event mockEvent = generateMockEvent();
    private static Profile mockProfile = generateMockProfile();
    private static Session mockSession = generateMockSession();

    @Test
    public void testHardcodedEvaluator() {
        Event mockEvent = generateMockEvent();
        assertEquals("Target itemId value is not correct", MOCK_ITEM_ID, propertyConditionEvaluator.getHardcodedPropertyValue(mockEvent, "target.itemId"));
        assertEquals("Target scope is not correct", DIGITALL_SCOPE, propertyConditionEvaluator.getHardcodedPropertyValue(mockEvent, "target.scope"));
        assertEquals("Target page path value is not correct", PAGE_PATH_VALUE, propertyConditionEvaluator.getHardcodedPropertyValue(mockEvent, "target.properties.pageInfo.pagePath"));
        assertEquals("Target page url value is not correct", PAGE_URL_VALUE,                 propertyConditionEvaluator.getHardcodedPropertyValue(mockEvent, "target.properties.pageInfo.pageURL"));
        assertEquals("Session size should be 10", 10, propertyConditionEvaluator.getHardcodedPropertyValue(mockSession, "size"));

        assertNull("Unexisting property should be null", propertyConditionEvaluator.getHardcodedPropertyValue(mockSession, "systemProperties.goals._csk6r4cgeStartReached"));
        assertNull("Unexisting property should be null", propertyConditionEvaluator.getHardcodedPropertyValue(mockProfile, "properties.email"));

        // here let's make sure our reporting of non optimized expressions works.
        assertEquals("Should have received the non-optimized marker string", NOT_OPTIMIZED_MARKER, propertyConditionEvaluator.getHardcodedPropertyValue(mockSession, "lastEventDate") );

    }

    @Test
    public void testOGNLEvaluator() throws Exception {
        Event mockEvent = generateMockEvent();
        assertEquals("Target itemId value is not correct", MOCK_ITEM_ID, propertyConditionEvaluator.getOGNLPropertyValue(mockEvent, "target.itemId"));
        assertEquals("Target scope is not correct", DIGITALL_SCOPE,propertyConditionEvaluator.getOGNLPropertyValue(mockEvent, "target.scope"));
        assertEquals("Target page path value is not correct", PAGE_PATH_VALUE, propertyConditionEvaluator.getOGNLPropertyValue(mockEvent, "target.properties.pageInfo.pagePath"));
        assertEquals("Target page url value is not correct", PAGE_URL_VALUE, propertyConditionEvaluator.getOGNLPropertyValue(mockEvent, "target.properties.pageInfo.pageURL"));
        assertEquals("Session size should be 10", 10, propertyConditionEvaluator.getOGNLPropertyValue(mockSession, "size"));
        assertEquals("Should have received the proper last even date", SESSION_LAST_EVENT_DATE, propertyConditionEvaluator.getOGNLPropertyValue(mockSession, "lastEventDate") );

        assertNull("Unexisting property should be null", propertyConditionEvaluator.getHardcodedPropertyValue(mockSession, "systemProperties.goals._csk6r4cgeStartReached"));
        assertNull("Unexisting property should be null", propertyConditionEvaluator.getHardcodedPropertyValue(mockProfile, "properties.email"));
    }

    @Test
    public void testCompareOGNLvsHardcodedPerformance() throws InterruptedException {
        int workerCount = 5000000;
        ExecutorService executorService = Executors.newFixedThreadPool(3000);

        runHardcodedTest(workerCount, executorService);
        runOGNLTest(workerCount, executorService);
        runHardcodedTest(workerCount, executorService);
        runOGNLTest(workerCount, executorService);
        runHardcodedTest(workerCount, executorService);
        runOGNLTest(workerCount, executorService);
        runHardcodedTest(workerCount, executorService);
        runOGNLTest(workerCount, executorService);

    }

    @Test
    public void testPropertyEvaluator() throws Exception {
        Event mockEvent = generateMockEvent();
        assertEquals("Target itemId value is not correct", MOCK_ITEM_ID, propertyConditionEvaluator.getPropertyValue(mockEvent, "target.itemId"));
        assertEquals("Target scope is not correct", DIGITALL_SCOPE,propertyConditionEvaluator.getPropertyValue(mockEvent, "target.scope"));
        assertEquals("Target page path value is not correct", PAGE_PATH_VALUE, propertyConditionEvaluator.getPropertyValue(mockEvent, "target.properties.pageInfo.pagePath"));

        assertNull("Unexisting property should be null", propertyConditionEvaluator.getPropertyValue(mockSession, "systemProperties.goals._csk6r4cgeStartReached"));
        assertNull("Unexisting property should be null", propertyConditionEvaluator.getPropertyValue(mockProfile, "properties.email"));

        assertEquals("Session size should be 10", 10, propertyConditionEvaluator.getPropertyValue(mockSession, "size"));
        assertEquals("Session last event date is not right", SESSION_LAST_EVENT_DATE, propertyConditionEvaluator.getPropertyValue(mockSession, "lastEventDate"));
    }

    private void runHardcodedTest(int workerCount, ExecutorService executorService) throws InterruptedException {
        List<Callable<Object>> todo = new ArrayList<Callable<Object>>(workerCount);
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < workerCount; i++) {
            todo.add(new HardcodedWorker());
        }
        List<Future<Object>> answers = executorService.invokeAll(todo);
        long totalTime = System.currentTimeMillis() - startTime;
        System.out.println("Hardcoded workers completed execution in " + totalTime + "ms");
    }

    private void runOGNLTest(int workerCount, ExecutorService executorService) throws InterruptedException {
        List<Callable<Object>> todo = new ArrayList<Callable<Object>>(workerCount);
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < workerCount; i++) {
            todo.add(new OGNLWorker());
        }
        List<Future<Object>> answers = executorService.invokeAll(todo);
        long totalTime = System.currentTimeMillis() - startTime;
        System.out.println("OGNL workers completed execution in " + totalTime + "ms");
    }

    private static Event generateMockEvent() {
        Event mockEvent = new Event();
        CustomItem targetItem = new CustomItem();
        targetItem.setItemId(MOCK_ITEM_ID);
        targetItem.setScope(DIGITALL_SCOPE);
        mockEvent.setTarget(targetItem);
        Map<String,Object> pageInfoMap = new HashMap<>();
        pageInfoMap.put("pagePath", PAGE_PATH_VALUE);
        pageInfoMap.put("pageURL", PAGE_URL_VALUE);
        targetItem.getProperties().put("pageInfo", pageInfoMap);
        return mockEvent;
    }

    public static Profile generateMockProfile() {
        Profile mockProfile = new Profile();
        mockProfile.setItemId("mockProfileId");
        mockProfile.getSegments().add("segment1");
        mockProfile.getSegments().add("segment2");
        mockProfile.getSegments().add("segment3");
        return mockProfile;
    }

    public static Session generateMockSession() {
        Session mockSession = new Session("mockSessionId", generateMockProfile(), new Date(), "digitall");
        mockSession.setSize(10);
        mockSession.setLastEventDate(SESSION_LAST_EVENT_DATE);
        return mockSession;
    }

    class HardcodedWorker implements Callable<Object> {

        @Override
        public Object call() {
            propertyConditionEvaluator.getHardcodedPropertyValue(mockEvent, "target.itemId");
            propertyConditionEvaluator.getHardcodedPropertyValue(mockEvent, "target.scope");
            propertyConditionEvaluator.getHardcodedPropertyValue(mockEvent, "target.properties.pageInfo.pagePath");
            propertyConditionEvaluator.getHardcodedPropertyValue(mockEvent, "target.properties.pageInfo.pageURL");
            return null;
        }
    }

    class OGNLWorker implements Callable<Object> {

        @Override
        public Object call() {
            try {
                propertyConditionEvaluator.getOGNLPropertyValue(mockEvent, "target.itemId");
                propertyConditionEvaluator.getOGNLPropertyValue(mockEvent, "target.scope");
                propertyConditionEvaluator.getOGNLPropertyValue(mockEvent, "target.properties.pageInfo.pagePath");
                propertyConditionEvaluator.getOGNLPropertyValue(mockEvent, "target.properties.pageInfo.pageURL");
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }
    }

}
