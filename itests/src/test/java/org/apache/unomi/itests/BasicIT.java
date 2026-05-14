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
 * limitations under the License
 */

package org.apache.unomi.itests;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.apache.unomi.api.ContextRequest;
import org.apache.unomi.api.CustomItem;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.rules.Rule;
import org.apache.unomi.itests.tools.httpclient.HttpClientThatWaitsForUnomi;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;


@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class BasicIT extends BaseIT {
    private final static Logger LOGGER = LoggerFactory.getLogger(BasicIT.class);


    private static final String SESSION_ID_0 = "aa3b04bd-8f4d-4a07-8e96-d33ffa04d3d0";
    private static final String SESSION_ID_1 = "aa3b04bd-8f4d-4a07-8e96-d33ffa04d3d1";
    private static final String SESSION_ID_2 = "aa3b04bd-8f4d-4a07-8e96-d33ffa04d3d2";
    private static final String SESSION_ID_3 = "aa3b04bd-8f4d-4a07-8e96-d33ffa04d3d3";
    private static final String SESSION_ID_4 = "aa3b04bd-8f4d-4a07-8e96-d33ffa04d3d4";

    private static final String EVENT_TYPE_LOGIN = "login";
    private static final String EVENT_TYPE_VIEW = "view";
    private static final String TEST_SCOPE = "testScope";

    private static final String ITEM_TYPE_SITE = "site";
    private static final String ITEM_ID_SITE = "site-8f4d-4a07-8e96-d33ffa04d3d4";
    private static final String ITEM_TYPE_VISITOR = "VISITOR";
    protected static final String ITEM_ID_PAGE_1 = "page-8f4d-4a07-8e96-d33ffa04d3d4";
    protected static final String ITEM_TYPE_PAGE = "page";

    private static final String FIRST_NAME = "firstName";
    private static final String LAST_NAME = "lastName";
    private static final String EMAIL = "email";
    private static final String FIRST_VISIT = "firstVisit";
    private static final String LAST_VISIT = "lastVisit";
    private static final String PREVIOUS_VISIT = "previousVisit";

    private static final String FIRST_NAME_VISITOR_1 = "firstNameVisitor1";
    private static final String FIRST_NAME_VISITOR_2 = "firstNameVisitor2";
    private static final String LAST_NAME_VISITOR_1 = "lastNameVisitor1";
    private static final String LAST_NAME_VISITOR_2 = "lastNameVisitor2";
    private static final String EMAIL_VISITOR_1 = "visitor1@apache.unomi.org";
    private static final String EMAIL_VISITOR_2 = "visitor2@apache.unomi.org";

    @Before
    public void setUp() throws InterruptedException {
        TestUtils.createScope(TEST_SCOPE, "Test scope", scopeService);
        keepTrying("Scope "+ TEST_SCOPE +" not found in the required time", () -> scopeService.getScope(TEST_SCOPE),
                Objects::nonNull, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
    }

    @After
    public void tearDown() {
        scopeService.delete(TEST_SCOPE);
    }

    @Test
    public void simpleTest() throws Exception {
        System.out.println("==== System Property in probe bundle: " + System.getProperty("my.system.property"));
        assertContains("foo", System.getProperty("my.system.property"));
    }

    @Test
    public void testContextJS() throws Exception {
        LOGGER.info("Start test testContextJS");
        HttpUriRequest request = new HttpGet(getFullUrl("/cxs/context.js?sessionId=" + SESSION_ID_0));
        request.setHeader("Content-Type", "application/json");
        // The underlying HTTP connection is still held by the response object
        // to allow the response content to be streamed directly from the network socket.
        // In order to ensure correct deallocation of system resources
        // the profile MUST call CloseableHttpResponse#close() from a finally clause.
        // Please note that if response content is not fully consumed the underlying
        // connection cannot be safely re-used and will be shut down and discarded
        // by the connection manager.
        String responseContent;
        try (CloseableHttpResponse response = HttpClientThatWaitsForUnomi.doRequest(request)) {
            HttpEntity entity = response.getEntity();
            // do something useful with the response body
            // and ensure it is fully consumed
            responseContent = EntityUtils.toString(entity);
        }
        Assert.assertTrue("Response should contain context object", responseContent.contains("window.digitalData = window.digitalData || {};\n"));
        // @todo we should check the validity of the context object, but this is rather complex since it would potentially require parsing the Javascript !
        LOGGER.info("End test testContextJS");
    }

    @Test
    public void testContextJSONWithUrlParameter() throws Exception {
        LOGGER.info("Start test testContextJSONWithUrlParameter");
        ContextRequest contextRequest = new ContextRequest();
        HttpPost request = new HttpPost(getFullUrl("/cxs/context.json?sessionId=" + SESSION_ID_1));
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.create("application/json")));

        executeContextJSONRequest(request, SESSION_ID_1);
        LOGGER.info("End test testContextJSONWithUrlParameter");
    }

    @Test
    public void testContextJSON() throws Exception {
        LOGGER.info("Start test testContextJSON");
        ContextRequest contextRequest = new ContextRequest();
        contextRequest.setSessionId(SESSION_ID_2);
        HttpPost request = new HttpPost(getFullUrl("/cxs/context.json"));
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequest), ContentType.create("application/json")));

        executeContextJSONRequest(request, SESSION_ID_2);
        LOGGER.info("End test testContextJSON");
    }

    @Test
    public void testMultipleLoginOnSameBrowser() throws Exception {
        LOGGER.info("Start test testMultipleLoginOnSameBrowser");

        // Add login event condition
        ConditionType conditionType = CustomObjectMapper.getObjectMapper().readValue(
                new File("data/tmp/testLoginEventCondition.json").toURI().toURL(), ConditionType.class);
        definitionsService.setConditionType(conditionType);

        refreshPersistence(ConditionType.class);
        Thread.sleep(2000);
        // Ensure the dynamically registered condition type is visible before creating the rule
        keepTrying(
                "loginEventCondition not registered in the required time",
                () -> definitionsService.getConditionType("loginEventCondition"),
                Objects::nonNull,
                DEFAULT_TRYING_TIMEOUT,
                DEFAULT_TRYING_TRIES
        );

        // Add login rule
        Rule rule = CustomObjectMapper.getObjectMapper().readValue(new File("data/tmp/testLogin.json").toURI().toURL(),
                Rule.class);
        createAndWaitForRule(rule);

        CustomItem sourceSite = new CustomItem(ITEM_ID_SITE, ITEM_TYPE_SITE);
        sourceSite.setScope(TEST_SCOPE);

        // First page view with the first visitor aka VISITOR_1 and SESSION_ID_3
        ContextRequest contextRequestPageViewSession1 = getContextRequestWithPageViewEvent(sourceSite, SESSION_ID_3);
        HttpPost requestPageView1 = new HttpPost(getFullUrl("/cxs/context.json"));
        requestPageView1.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequestPageViewSession1),
                ContentType.create("application/json")));
        TestUtils.RequestResponse requestResponsePageView1 = executeContextJSONRequest(requestPageView1, SESSION_ID_3);
        String profileIdVisitor1 = requestResponsePageView1.getContextResponse().getProfileId();
        String lastVisit = (String) requestResponsePageView1.getContextResponse().getProfileProperties().get("lastVisit");
        Assert.assertNotNull("Context profile properties should contains a lastVisit property", lastVisit);
        Thread.sleep(1000);

        // Initialize VISITOR_1 properties
        Map<String, Object> loginEventPropertiesVisitor1 = new HashMap<>();
        loginEventPropertiesVisitor1.put(FIRST_NAME, FIRST_NAME_VISITOR_1);
        loginEventPropertiesVisitor1.put(LAST_NAME, LAST_NAME_VISITOR_1);
        loginEventPropertiesVisitor1.put(EMAIL, EMAIL_VISITOR_1);

        // Create login event with VISITOR_1
        ContextRequest contextRequestLoginVisitor1 = getContextRequestWithLoginEvent(sourceSite, loginEventPropertiesVisitor1,
                EMAIL_VISITOR_1, SESSION_ID_3);
        HttpPost requestLoginVisitor1 = new HttpPost(getFullUrl("/cxs/context.json"));
        requestLoginVisitor1.addHeader("Cookie", requestResponsePageView1.getCookieHeaderValue());
        requestLoginVisitor1.addHeader("X-Unomi-Api-Key", testPublicKey.getKey());
        requestLoginVisitor1.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequestLoginVisitor1),
                ContentType.create("application/json")));
        TestUtils.RequestResponse requestResponseLoginVisitor1 = executeContextJSONRequest(requestLoginVisitor1, SESSION_ID_3);
        Assert.assertEquals("Context profile id should be the same", profileIdVisitor1,
                requestResponseLoginVisitor1.getContextResponse().getProfileId());
        checkVisitor1ResponseProperties(requestResponseLoginVisitor1.getContextResponse().getProfileProperties());
        Assert.assertEquals("LastVisit property should not be updated as we are on the same session", lastVisit,
                requestResponseLoginVisitor1.getContextResponse().getProfileProperties().get("lastVisit"));
        Thread.sleep(1000);

        // Lets add a page view with VISITOR_1 to simulate reloading the page after login and be able to check the profile properties
        HttpPost requestPageView2 = new HttpPost(getFullUrl("/cxs/context.json"));
        requestPageView2.addHeader("Cookie", requestResponsePageView1.getCookieHeaderValue());
        requestPageView2.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequestPageViewSession1),
                ContentType.create("application/json")));
        TestUtils.RequestResponse requestResponsePageView2 = executeContextJSONRequest(requestPageView2, SESSION_ID_3);
        Assert.assertEquals("Context profile id should be the same", profileIdVisitor1,
                requestResponsePageView2.getContextResponse().getProfileId());
        checkVisitor1ResponseProperties(requestResponsePageView2.getContextResponse().getProfileProperties());
        Assert.assertEquals("LastVisit property should not be updated as we are on the same session", lastVisit,
                requestResponsePageView2.getContextResponse().getProfileProperties().get("lastVisit"));
        Thread.sleep(1000);

        // Lets simulate a logout by requesting the context with a new page view event and a new session id
        // but we will send the cookie of the profile id from VISITOR_1
        ContextRequest contextRequestPageViewSession2 = getContextRequestWithPageViewEvent(sourceSite, SESSION_ID_4);
        HttpPost requestPageView3 = new HttpPost(getFullUrl("/cxs/context.json"));
        requestPageView3.addHeader("Cookie", requestResponsePageView1.getCookieHeaderValue());
        requestPageView3.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequestPageViewSession2),
                ContentType.create("application/json")));
        TestUtils.RequestResponse requestResponsePageView3 = executeContextJSONRequest(requestPageView3, SESSION_ID_4);
        Assert.assertEquals("Context profile id should be the same", profileIdVisitor1,
                requestResponsePageView3.getContextResponse().getProfileId());
        checkVisitor1ResponseProperties(requestResponsePageView3.getContextResponse().getProfileProperties());
        Assert.assertEquals("previousVisit property should be updated as we are on a new session", lastVisit,
                requestResponsePageView3.getContextResponse().getProfileProperties().get("previousVisit"));
        Assert.assertNotEquals("lastVisit property should be updated as we are on a new session", lastVisit,
                requestResponsePageView3.getContextResponse().getProfileProperties().get("lastVisit"));
        lastVisit = (String) requestResponsePageView3.getContextResponse().getProfileProperties().get("lastVisit");
        Assert.assertNotNull("Context profile properties should contains a lastVisit property", lastVisit);
        Thread.sleep(1000);

        // Initialize VISITOR_2 properties
        Map<String, Object> loginEventPropertiesVisitor2 = new HashMap<>();
        loginEventPropertiesVisitor2.put(FIRST_NAME, FIRST_NAME_VISITOR_2);
        loginEventPropertiesVisitor2.put(LAST_NAME, LAST_NAME_VISITOR_2);
        loginEventPropertiesVisitor2.put(EMAIL, EMAIL_VISITOR_2);

        // Create login event with VISITOR_2
        ContextRequest contextRequestLoginVisitor2 = getContextRequestWithLoginEvent(sourceSite, loginEventPropertiesVisitor2,
                EMAIL_VISITOR_2, SESSION_ID_4);
        HttpPost requestLoginVisitor2 = new HttpPost(getFullUrl("/cxs/context.json"));
        requestLoginVisitor2.addHeader("Cookie", requestResponsePageView1.getCookieHeaderValue());
        requestLoginVisitor2.addHeader("X-Unomi-Api-Key", testPublicKey.getKey());
        requestLoginVisitor2.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequestLoginVisitor2),
                ContentType.create("application/json")));
        TestUtils.RequestResponse requestResponseLoginVisitor2 = executeContextJSONRequest(requestLoginVisitor2, SESSION_ID_4);
        // We should have a new profile id so the session should have been moved from VISITOR_1 to VISITOR_2
        String profileIdVisitor2 = requestResponseLoginVisitor2.getContextResponse().getProfileId();
        Assert.assertNotEquals("Context profile id should not be the same", profileIdVisitor1,
                profileIdVisitor2);
        checkVisitor2ResponseProperties(requestResponseLoginVisitor2.getContextResponse().getProfileProperties());
        Thread.sleep(1000);

        // Lets add a page view with VISITOR_2 to simulate reloading the page after login
        HttpPost requestPageView4 = new HttpPost(getFullUrl("/cxs/context.json"));
        requestPageView4.addHeader("Cookie", requestResponseLoginVisitor2.getCookieHeaderValue());
        requestPageView4.setEntity(new StringEntity(objectMapper.writeValueAsString(contextRequestPageViewSession2),
                ContentType.create("application/json")));
        TestUtils.RequestResponse requestResponsePageView4 = executeContextJSONRequest(requestPageView4, SESSION_ID_4);
        Assert.assertEquals("Context profile id should be the same", profileIdVisitor2,
                requestResponsePageView4.getContextResponse().getProfileId());
        checkVisitor2ResponseProperties(requestResponsePageView4.getContextResponse().getProfileProperties());
        Thread.sleep(1000);

        refreshPersistence(Profile.class);

        // Check both visitor profile at the end by loading them directly
        Profile profileVisitor1 = profileService.load(profileIdVisitor1);
        checkVisitor1ResponseProperties(profileVisitor1.getProperties());
        Profile profileVisitor2 = profileService.load(profileIdVisitor2);
        checkVisitor2ResponseProperties(profileVisitor2.getProperties());

        rulesService.removeRule("testLogin");

        LOGGER.info("End test testMultipleLoginOnSameBrowser");
    }

    private ContextRequest getContextRequestWithLoginEvent(CustomItem sourceSite, Map<String, Object> loginEventProperties,
            String visitorId, String sessionId) {

        CustomItem loginEventTarget = new CustomItem(visitorId, ITEM_TYPE_VISITOR);
        loginEventTarget.setProperties(loginEventProperties);

        // We use setters to avoid having auto-populated fields by the other event constructor methods.
        Event loginEvent = new Event();
        loginEvent.setEventType(EVENT_TYPE_LOGIN);
        loginEvent.setScope(TEST_SCOPE);
        loginEvent.setTarget(loginEventTarget);
        loginEvent.setTimeStamp(new Date());

        ContextRequest contextRequest = new ContextRequest();
        contextRequest.setSource(sourceSite);
        contextRequest.setRequireSegments(false);
        contextRequest.setEvents(Collections.singletonList(loginEvent));
        contextRequest.setRequiredProfileProperties(Arrays.asList(FIRST_NAME, LAST_NAME, EMAIL, FIRST_VISIT, LAST_VISIT, PREVIOUS_VISIT));
        contextRequest.setSessionId(sessionId);
        return contextRequest;
    }

    private ContextRequest getContextRequestWithPageViewEvent(CustomItem sourceSite, String sessionId) {
        CustomItem customPageItem = new CustomItem(ITEM_ID_PAGE_1, ITEM_TYPE_PAGE);
        customPageItem.setScope(TEST_SCOPE);
        Map<String, Object> pageInfo = new HashMap<>();
        pageInfo.put("referringURL", "http://localhost:8080");

        Map<String, Object> properties = new HashMap<>();
        properties.put("pageInfo", pageInfo);

        customPageItem.setProperties(properties);

        // Create page view event to mock a connection to a site. We use setters to avoid having auto-populated fields
        Event pageViewEvent = new Event();
        pageViewEvent.setEventType(EVENT_TYPE_VIEW);
        pageViewEvent.setSessionId(sessionId);
        pageViewEvent.setScope(TEST_SCOPE);
        pageViewEvent.setSource(sourceSite);
        pageViewEvent.setTarget(customPageItem);
        pageViewEvent.setTimeStamp(new Date());

        // Initialize context like if you display the first page on the website
        ContextRequest contextRequest = new ContextRequest();
        contextRequest.setSessionId(sessionId);
        contextRequest.setSource(customPageItem);
        contextRequest.setRequireSegments(false);
        contextRequest.setEvents(Collections.singletonList(pageViewEvent));
        contextRequest.setRequiredProfileProperties(Arrays.asList(FIRST_NAME, LAST_NAME, EMAIL, FIRST_VISIT, LAST_VISIT, PREVIOUS_VISIT));
        return contextRequest;
    }

    private TestUtils.RequestResponse executeContextJSONRequest(HttpPost request, String sessionId) throws IOException {
        return TestUtils.executeContextJSONRequest(request, sessionId);
    }

    private void checkVisitor1ResponseProperties(Map<String, Object> profileProperties) {
        checkVisitorResponseProperties(profileProperties, FIRST_NAME_VISITOR_1, LAST_NAME_VISITOR_1, EMAIL_VISITOR_1);
    }

    private void checkVisitor2ResponseProperties(Map<String, Object> profileProperties) {
        checkVisitorResponseProperties(profileProperties, FIRST_NAME_VISITOR_2, LAST_NAME_VISITOR_2, EMAIL_VISITOR_2);
    }

    private void checkVisitorResponseProperties(Map<String, Object> profileProperties, String firstNameVisitor,
            String lastNameVisitor, String emailVisitor) {
        Assert.assertNotNull("Context profile properties should not be null", profileProperties);

        Assert.assertTrue("Context profile properties should contains the entry " + FIRST_NAME,
                profileProperties.containsKey(FIRST_NAME));
        Assert.assertTrue("Context profile properties should contains the entry " + LAST_NAME,
                profileProperties.containsKey(LAST_NAME));
        Assert.assertTrue("Context profile properties should contains the entry " + EMAIL,
                profileProperties.containsKey(EMAIL));

        Assert.assertEquals("Context profile properties " + FIRST_NAME + " should be equal to " + firstNameVisitor,
                profileProperties.get(FIRST_NAME), firstNameVisitor);
        Assert.assertEquals("Context profile properties " + LAST_NAME + " should be equal to " + lastNameVisitor,
                profileProperties.get(LAST_NAME), lastNameVisitor);
        Assert.assertEquals("Context profile properties " + EMAIL + " should be equal to " + emailVisitor,
                profileProperties.get(EMAIL), emailVisitor);
    }
}
