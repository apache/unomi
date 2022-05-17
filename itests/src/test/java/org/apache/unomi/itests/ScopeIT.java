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

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.entity.ContentType;
import org.apache.unomi.api.Scope;
import org.apache.unomi.api.services.ScopeService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.ops4j.pax.exam.util.Filter;

import javax.inject.Inject;
import java.util.List;
import java.util.Objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Class to tests the Scope features
 */

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class ScopeIT extends BaseIT {
    private final static String SCOPE_URL = "/cxs/scopes";
    private static final int DEFAULT_TRYING_TIMEOUT = 2000;
    private static final int DEFAULT_TRYING_TRIES = 30;

    @Inject
    @Filter(timeout = 600000)
    protected ScopeService scopeService;

    @Inject
    @Filter(timeout = 600000)
    protected PersistenceService persistenceService;

    @Before
    public void setUp() throws InterruptedException {
        keepTrying("Couldn't find scope endpoint", () -> get(SCOPE_URL, List.class), Objects::nonNull, DEFAULT_TRYING_TIMEOUT,
                DEFAULT_TRYING_TRIES);
    }

    @After
    public void tearDown() {
        scopeService.delete("scopeTest");
    }

    @Test
    public void testGetScopesMetadatas() throws InterruptedException {
        List scopes = get(SCOPE_URL, List.class);
        assertTrue("Scope list should be empty", scopes.isEmpty());

        post(SCOPE_URL, "scope/scope-test1.json", ContentType.APPLICATION_JSON);

        scopes = keepTrying("Couldn't find scopes", () -> get(SCOPE_URL, List.class), (list) -> !list.isEmpty(), DEFAULT_TRYING_TIMEOUT,
                DEFAULT_TRYING_TRIES);
        assertFalse("Scope list should not be empty", scopes.isEmpty());
        assertEquals("Scope list should not be empty", 1, scopes.size());
    }

    @Test
    public void testGetScope() throws InterruptedException {
        Scope storedScope = get(SCOPE_URL + "/scopeTest", Scope.class);
        assertTrue("Scope should be null", Objects.isNull(storedScope));

        post(SCOPE_URL, "scope/scope-test1.json", ContentType.APPLICATION_JSON);

        storedScope = keepTrying("Couldn't find scopes", () -> get(SCOPE_URL + "/scopeTest", Scope.class), Objects::nonNull,
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

        assertEquals("storedScope.getValue() shoould be equal to scopeToTest", "scopeToTest", storedScope.getValue());
    }

    @Test
    public void testSaveScope() throws InterruptedException {

        assertTrue("Scope list should be empty", persistenceService.getAllItems(Scope.class).isEmpty());

        CloseableHttpResponse response = post(SCOPE_URL, "scope/scope-test1.json", ContentType.APPLICATION_JSON);

        assertEquals("Invalid response code", 200, response.getStatusLine().getStatusCode());
        List scopes = keepTrying("Couldn't find scopes", () -> get(SCOPE_URL, List.class), (list) -> !list.isEmpty(),
                DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
        assertFalse("Scope list should not be empty", scopes.isEmpty());
    }

    @Test
    public void testDeleteScope() throws InterruptedException {
        assertTrue("Scope list should be empty", persistenceService.getAllItems(Scope.class).isEmpty());

        post(SCOPE_URL, "scope/scope-test1.json", ContentType.APPLICATION_JSON);

        keepTrying("Couldn't find scopes", () -> get(SCOPE_URL, List.class), (list) -> !list.isEmpty(), DEFAULT_TRYING_TIMEOUT,
                DEFAULT_TRYING_TRIES);

        CloseableHttpResponse response = delete(SCOPE_URL + "/scopeTest");
        assertEquals("Invalid response code", 204, response.getStatusLine().getStatusCode());

        List scopes = keepTrying("wait for empty list of scope", () -> get(SCOPE_URL, List.class), List::isEmpty, DEFAULT_TRYING_TIMEOUT,
                DEFAULT_TRYING_TRIES);

        assertTrue("Scope list should be empty", scopes.isEmpty());
    }
}
