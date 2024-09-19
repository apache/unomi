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

import ognl.MethodFailedException;
import org.apache.unomi.api.*;
import org.apache.unomi.api.rules.Rule;
import org.apache.unomi.plugins.baseplugin.conditions.accessors.HardcodedPropertyAccessor;
import org.apache.unomi.scripting.ExpressionFilter;
import org.apache.unomi.scripting.ExpressionFilterFactory;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static org.junit.Assert.assertFalse;

public class PropertyConditionEvaluatorTest {

    public static final String MOCK_ITEM_ID = "mockItemId";
    public static final String DIGITALL_SCOPE = "digitall";
    public static final String SOURCE_PAGE_PATH_VALUE = "/site/en/home.html";
    public static final String SOURCE_PAGE_URL_VALUE = "http://localhost:8080/site/en/home.html";
    public static final String TARGET_PAGE_PATH_VALUE = "/site/en/home/aboutus.html";
    public static final String TARGET_PAGE_URL_VALUE = "http://localhost:8080/site/en/home/aboutus.html";
    public static final Date SESSION_LAST_EVENT_DATE = new Date();
    public static final int THREAD_POOL_SIZE = 300;
    public static final int WORKER_COUNT = 500000;
    public static final int SESSION_SIZE = 10;
    public static final Date PROFILE_PREVIOUS_VISIT = new Date();
    public static final String NEWSLETTER_CONSENT_ID = "newsLetterConsentId";
    public static final String TRACKING_CONSENT_ID = "trackingConsentId";
    public static final String RULE_ITEM_ID = "mockRuleItemId";
    private static PropertyConditionEvaluator propertyConditionEvaluator = new PropertyConditionEvaluator();
    private static Profile mockProfile = generateMockProfile();
    private static Session mockSession = generateMockSession(mockProfile);
    private static Event mockEvent = generateMockEvent(mockProfile, mockSession);

    @Before
    public void setup() {
        propertyConditionEvaluator.setExpressionFilterFactory(new ExpressionFilterFactory() {
            @Override
            public ExpressionFilter getExpressionFilter(String filterCollection) {
                Set<Pattern> allowedExpressions = new HashSet<>();
                allowedExpressions.add(Pattern.compile("target\\.itemId"));
                allowedExpressions.add(Pattern.compile("target\\.scope"));
                allowedExpressions.add(Pattern.compile("target\\.properties\\.pageInfo\\.pagePath"));
                allowedExpressions.add(Pattern.compile("target\\.properties\\.pageInfo\\.pageURL"));
                allowedExpressions.add(Pattern.compile("size"));
                allowedExpressions.add(Pattern.compile("lastEventDate"));
                allowedExpressions.add(Pattern.compile("systemProperties\\.goals\\._csk6r4cgeStartReached"));
                allowedExpressions.add(Pattern.compile("properties\\.email"));
                allowedExpressions.add(Pattern.compile("systemProperties\\.goals\\._csk6r4cgeStartReached"));
                Set<Pattern> forbiddenExpressions = new HashSet<>();
                return new ExpressionFilter(allowedExpressions, forbiddenExpressions);
            }
        });
    }

    @Test
    public void testHardcodedEvaluator() {
        Event mockEvent = generateMockEvent(mockProfile, mockSession);
        assertEquals("Target itemId value is not correct", MOCK_ITEM_ID, propertyConditionEvaluator.getHardcodedPropertyValue(mockEvent, "target.itemId"));
        assertEquals("Target scope is not correct", DIGITALL_SCOPE, propertyConditionEvaluator.getHardcodedPropertyValue(mockEvent, "target.scope"));
        assertEquals("Target page path value is not correct", TARGET_PAGE_PATH_VALUE, propertyConditionEvaluator.getHardcodedPropertyValue(mockEvent, "target.properties.pageInfo.pagePath"));
        assertEquals("Target page url value is not correct", TARGET_PAGE_URL_VALUE, propertyConditionEvaluator.getHardcodedPropertyValue(mockEvent, "target.properties.pageInfo.pageURL"));
        assertEquals("Session size should be 10", SESSION_SIZE, propertyConditionEvaluator.getHardcodedPropertyValue(mockSession, "size"));
        assertEquals("Session profile previous visit is not valid", PROFILE_PREVIOUS_VISIT, propertyConditionEvaluator.getHardcodedPropertyValue(mockSession,"profile.properties.previousVisit"));
        assertEquals("Page page couldn't be resolved on Event property", SOURCE_PAGE_PATH_VALUE, propertyConditionEvaluator.getHardcodedPropertyValue(mockEvent, "source.properties.pageInfo.pagePath"));
        assertEquals("Tracking consent should be granted", ConsentStatus.GRANTED, propertyConditionEvaluator.getHardcodedPropertyValue(mockEvent, "profile.consents.digitall/trackingConsentId.status"));
        assertEquals("Tracking consent should be granted", ConsentStatus.GRANTED, propertyConditionEvaluator.getHardcodedPropertyValue(mockEvent, "profile.consents[\"digitall/trackingConsentId\"].status"));

        assertEquals("Unexisting property should be null", null, propertyConditionEvaluator.getHardcodedPropertyValue(mockSession, "systemProperties.goals._csk6r4cgeStartReached"));
        assertEquals("Unexisting property should be null", null, propertyConditionEvaluator.getHardcodedPropertyValue(mockProfile, "properties.email"));

        // here let's make sure our reporting of non optimized expressions works.
        assertEquals("Should have received the non-optimized marker string", HardcodedPropertyAccessor.PROPERTY_NOT_FOUND_MARKER, propertyConditionEvaluator.getHardcodedPropertyValue(mockSession, "profile.non-existing-field"));

        Event mockRuleEvent = generateMockRuleFiredEvent(mockProfile, mockSession);
        assertEquals("Target itemId value is not correct", RULE_ITEM_ID, propertyConditionEvaluator.getHardcodedPropertyValue(mockRuleEvent, "target.itemId"));

    }

    @Test
    public void testFlattenedProperties() {
        Event mockEvent = generateMockEvent(mockProfile, mockSession);
        mockEvent.getFlattenedProperties().put("test", "test");
        assertEquals("FlattenedProperties should be readable form accessor", "test", propertyConditionEvaluator.getHardcodedPropertyValue(mockEvent, "flattenedProperties.test"));
    }

    @Test
    public void testOGNLEvaluator() throws Exception {
        Event mockEvent = generateMockEvent(mockProfile, mockSession);
        assertEquals("Target itemId value is not correct", MOCK_ITEM_ID, propertyConditionEvaluator.getOGNLPropertyValue(mockEvent, "target.itemId"));
        assertEquals("Target scope is not correct", DIGITALL_SCOPE, propertyConditionEvaluator.getOGNLPropertyValue(mockEvent, "target.scope"));
        assertEquals("Target page path value is not correct", TARGET_PAGE_PATH_VALUE, propertyConditionEvaluator.getOGNLPropertyValue(mockEvent, "target.properties.pageInfo.pagePath"));
        assertEquals("Target page url value is not correct", TARGET_PAGE_URL_VALUE, propertyConditionEvaluator.getOGNLPropertyValue(mockEvent, "target.properties.pageInfo.pageURL"));
        assertEquals("Session size should be 10", SESSION_SIZE, propertyConditionEvaluator.getOGNLPropertyValue(mockSession, "size"));
        assertEquals("Should have received the proper last even date", SESSION_LAST_EVENT_DATE, propertyConditionEvaluator.getOGNLPropertyValue(mockSession, "lastEventDate"));

        assertNull("Unexisting property should be null", propertyConditionEvaluator.getOGNLPropertyValue(mockSession, "systemProperties.goals._csk6r4cgeStartReached"));
        assertNull("Unexisting property should be null", propertyConditionEvaluator.getOGNLPropertyValue(mockProfile, "properties.email"));
    }

    @Test
    public void testCompareOGNLvsHardcodedPerformance() throws InterruptedException {
        int workerCount = WORKER_COUNT;
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

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
        Event mockEvent = generateMockEvent(mockProfile, mockSession);
        assertEquals("Target itemId value is not correct", MOCK_ITEM_ID, propertyConditionEvaluator.getPropertyValue(mockEvent, "target.itemId"));
        assertEquals("Target scope is not correct", DIGITALL_SCOPE, propertyConditionEvaluator.getPropertyValue(mockEvent, "target.scope"));
        assertEquals("Target page path value is not correct", TARGET_PAGE_PATH_VALUE, propertyConditionEvaluator.getPropertyValue(mockEvent, "target.properties.pageInfo.pagePath"));

        assertNull("Unexisting property should be null", propertyConditionEvaluator.getPropertyValue(mockSession, "systemProperties.goals._csk6r4cgeStartReached"));
        assertNull("Unexisting property should be null", propertyConditionEvaluator.getPropertyValue(mockProfile, "properties.email"));

        assertEquals("Session size should be 10", SESSION_SIZE, propertyConditionEvaluator.getPropertyValue(mockSession, "size"));
        assertEquals("Session last event date is not right", SESSION_LAST_EVENT_DATE, propertyConditionEvaluator.getPropertyValue(mockSession, "lastEventDate"));
    }

    @Test
    public void testOGNLSecurity() throws Exception {
        Event mockEvent = generateMockEvent(mockProfile, mockSession);
        File vulnFile = new File("target/vuln-file.txt");
        if (vulnFile.exists()) {
            vulnFile.delete();
        }
        String vulnFileCanonicalPath = vulnFile.getCanonicalPath();
        vulnFileCanonicalPath = vulnFileCanonicalPath.replace("\\", "\\\\"); // this is required for Windows support
        try {
            propertyConditionEvaluator.getOGNLPropertyValue(mockEvent, "@java.lang.Runtime@getRuntime().exec('touch " + vulnFileCanonicalPath + "')");
        } catch (RuntimeException | MethodFailedException re) {
            // we ignore these exceptions as they are expected.
        }
        assertFalse("Vulnerability successfully executed ! File created at " + vulnFileCanonicalPath, vulnFile.exists());
        try {
            propertyConditionEvaluator.getOGNLPropertyValue(mockEvent, "(#cmd='touch " + vulnFileCanonicalPath + "').(#cmds={'bash','-c',#cmd}).(#p=new java.lang.ProcessBuilder(#cmds)).(#p.redirectErrorStream(true)).(#process=#p.start())");
        } catch (RuntimeException | MethodFailedException re) {
            // we ignore these exceptions as they are expected.
        }
        vulnFile = new File("target/vuln-file.txt");
        assertFalse("Vulnerability successfully executed ! File created at " + vulnFileCanonicalPath, vulnFile.exists());
        try {
            propertyConditionEvaluator.getOGNLPropertyValue(mockEvent, "(#cmd='touch " + vulnFileCanonicalPath + "').(#iswin=(@java.lang.System@getProperty('os.name').toLowerCase().contains('win'))).(#cmds=(#iswin?{'cmd.exe','/c',#cmd}:{'bash','-c',#cmd})).(#p=new java.lang.ProcessBuilder(#cmds)).(#p.redirectErrorStream(true)).(#process=#p.start())");
        } catch (RuntimeException | MethodFailedException re) {
            // we ignore these exceptions as they are expected.
        }
        vulnFile = new File("target/vuln-file.txt");
        assertFalse("Vulnerability successfully executed ! File created at " + vulnFileCanonicalPath, vulnFile.exists());
        try {
            propertyConditionEvaluator.getOGNLPropertyValue(mockEvent, "(#runtimeclass = #this.getClass().forName(\"java.lang.Runtime\")).(#getruntimemethod = #runtimeclass.getDeclaredMethods().{^ #this.name.equals(\"getRuntime\")}[0]).(#rtobj = #getruntimemethod.invoke(null,null)).(#execmethod = #runtimeclass.getDeclaredMethods().{? #this.name.equals(\"exec\")}.{? #this.getParameters()[0].getType().getName().equals(\"java.lang.String\")}.{? #this.getParameters().length < 2}[0]).(#execmethod.invoke(#rtobj,\" touch "+vulnFileCanonicalPath+"\"))");
        } catch (RuntimeException | MethodFailedException re) {
            // we ignore these exceptions as they are expected.
        }
        vulnFile = new File("target/vuln-file.txt");
        assertFalse("Vulnerability successfully executed ! File created at " + vulnFileCanonicalPath, vulnFile.exists());
    }

    private void runHardcodedTest(int workerCount, ExecutorService executorService) throws InterruptedException {
        List<Callable<Object>> todo = new ArrayList<>(workerCount);
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < workerCount; i++) {
            todo.add(new HardcodedWorker());
        }
        List<Future<Object>> answers = executorService.invokeAll(todo);
        long totalTime = System.currentTimeMillis() - startTime;
        System.out.println("Hardcoded workers completed execution in " + totalTime + "ms");
    }

    private void runOGNLTest(int workerCount, ExecutorService executorService) throws InterruptedException {
        List<Callable<Object>> todo = new ArrayList<>(workerCount);
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < workerCount; i++) {
            todo.add(new OGNLWorker());
        }
        List<Future<Object>> answers = executorService.invokeAll(todo);
        long totalTime = System.currentTimeMillis() - startTime;
        System.out.println("OGNL workers completed execution in " + totalTime + "ms");
    }

    private static Event generateMockEvent(Profile mockProfile, Session mockSession) {
        CustomItem sourceItem = new CustomItem();
        sourceItem.setItemId(MOCK_ITEM_ID);
        sourceItem.setScope(DIGITALL_SCOPE);
        Map<String, Object> sourcePageInfoMap = new HashMap<>();
        sourcePageInfoMap.put("pagePath", SOURCE_PAGE_PATH_VALUE);
        sourcePageInfoMap.put("pageURL", SOURCE_PAGE_URL_VALUE);
        sourceItem.getProperties().put("pageInfo", sourcePageInfoMap);
        CustomItem targetItem = new CustomItem();
        targetItem.setItemId(MOCK_ITEM_ID);
        targetItem.setScope(DIGITALL_SCOPE);
        Map<String, Object> targetPageInfoMap = new HashMap<>();
        targetPageInfoMap.put("pagePath", TARGET_PAGE_PATH_VALUE);
        targetPageInfoMap.put("pageURL", TARGET_PAGE_URL_VALUE);
        targetItem.getProperties().put("pageInfo", targetPageInfoMap);
        return new Event("view", mockSession, mockProfile, DIGITALL_SCOPE, sourceItem, targetItem, new HashMap<>(), new Date(), true);
    }

    private static Event generateMockRuleFiredEvent(Profile mockProfile, Session mockSession) {
        CustomItem sourceItem = new CustomItem();
        sourceItem.setItemId(MOCK_ITEM_ID);
        sourceItem.setScope(DIGITALL_SCOPE);
        Map<String, Object> sourcePageInfoMap = new HashMap<>();
        sourcePageInfoMap.put("pagePath", SOURCE_PAGE_PATH_VALUE);
        sourcePageInfoMap.put("pageURL", SOURCE_PAGE_URL_VALUE);
        sourceItem.getProperties().put("pageInfo", sourcePageInfoMap);
        Metadata metadata = new Metadata();
        metadata.setId(RULE_ITEM_ID);
        metadata.setScope(DIGITALL_SCOPE);
        metadata.setEnabled(true);
        Rule rule = new Rule(metadata);
        rule.setScope(DIGITALL_SCOPE);
        return new Event("ruleFired", mockSession, mockProfile, DIGITALL_SCOPE, sourceItem, rule, new HashMap<>(), new Date(), true);
    }

    public static Profile generateMockProfile() {
        Profile mockProfile = new Profile();
        mockProfile.setItemId("mockProfileId");
        mockProfile.getSegments().add("segment1");
        mockProfile.getSegments().add("segment2");
        mockProfile.getSegments().add("segment3");
        mockProfile.getProperties().put("previousVisit", PROFILE_PREVIOUS_VISIT);

        Consent newsLetterConsent = new Consent(DIGITALL_SCOPE, NEWSLETTER_CONSENT_ID, ConsentStatus.DENIED, new Date(), new Date());
        mockProfile.setConsent(newsLetterConsent);
        Consent trackingConsent = new Consent(DIGITALL_SCOPE, TRACKING_CONSENT_ID, ConsentStatus.GRANTED, new Date(), new Date());
        mockProfile.setConsent(trackingConsent);

        return mockProfile;
    }

    public static Session generateMockSession(Profile mockProfile) {
        Session mockSession = new Session("mockSessionId", generateMockProfile(), new Date(), "digitall");
        mockSession.setProfile(mockProfile);
        mockSession.setSize(SESSION_SIZE);
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
