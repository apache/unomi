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
package org.apache.unomi.itests;

import org.apache.unomi.itests.persistence.PersistenceITBackendResolver;
import org.junit.After;
import org.junit.Assert;
import org.junit.AssumptionViolatedException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Exercises {@link ProgressListener}'s actual JUnit {@code RunListener} callback wiring — as opposed to
 * {@link TestTimingCacheTest}, which only exercises {@link TestTimingCache}'s pure helpers directly with
 * hand-built inputs. In particular this covers the {@code currentTestFailed}/{@code
 * currentTestAssumptionFailed} flag lifecycle across {@code testStarted}/{@code testFailure}/{@code
 * testAssumptionFailure}/{@code testFinished}, which decides whether a completed test's duration reaches
 * {@link TestTimingCache}.
 */
public class ProgressListenerTest {

    /** Comfortably above {@link TestTimingCache#SUBSTANTIVE_OBSERVED_MS} so save() doesn't filter it out. */
    private static final long SUBSTANTIVE_SLEEP_MS = 150L;

    private String previousUserDir;
    private Path tempDir;

    @Before
    public void setUp() throws Exception {
        previousUserDir = System.getProperty("user.dir");
        tempDir = Files.createTempDirectory("unomi-progress-listener-test");
        System.setProperty("user.dir", tempDir.toAbsolutePath().toString());
    }

    @After
    public void tearDown() {
        if (previousUserDir != null) {
            System.setProperty("user.dir", previousUserDir);
        }
    }

    private static Description descriptionFor(String methodName) {
        return Description.createTestDescription(ProgressListenerTest.class, methodName);
    }

    private static ProgressListener newListener(String... testKeys) {
        return new ProgressListener(testKeys.length, new AtomicInteger(0), Arrays.asList(testKeys));
    }

    private static String provider() {
        return PersistenceITBackendResolver.resolveProviderId();
    }

    @Test
    public void successfulTestPersistsDurationToTimingCache() throws Exception {
        ProgressListener listener = newListener("ProgressListenerTest#ok");
        Description description = descriptionFor("ok");

        listener.testStarted(description);
        Thread.sleep(SUBSTANTIVE_SLEEP_MS);
        listener.testFinished(description);

        Long persisted = TestTimingCache.load(provider()).get("ProgressListenerTest#ok");
        Assert.assertNotNull("a successful test's duration should be persisted to the timing cache", persisted);
        Assert.assertTrue(persisted > 0L);
    }

    @Test
    public void failedTestDurationIsNotPersistedToTimingCache() throws Exception {
        ProgressListener listener = newListener("ProgressListenerTest#failing");
        Description description = descriptionFor("failing");

        listener.testStarted(description);
        Thread.sleep(SUBSTANTIVE_SLEEP_MS);
        listener.testFailure(new Failure(description, new AssertionError("boom")));
        listener.testFinished(description);

        Assert.assertNull("a failed test's duration must not pollute the timing cache",
                TestTimingCache.load(provider()).get("ProgressListenerTest#failing"));
    }

    @Test
    public void assumptionFailureDurationIsNotPersistedToTimingCache() throws Exception {
        // Regression coverage: ProgressListener must override testAssumptionFailure (JUnit's callback
        // for Assume.assumeTrue/assumeFalse-based skips, e.g. RolloverIT's backend-capability gating) —
        // without it, an assume-skipped test flows through testFinished exactly like a success and its
        // duration would be persisted.
        ProgressListener listener = newListener("ProgressListenerTest#skipped");
        Description description = descriptionFor("skipped");

        listener.testStarted(description);
        Thread.sleep(SUBSTANTIVE_SLEEP_MS);
        listener.testAssumptionFailure(new Failure(description,
                new AssumptionViolatedException("backend does not support this")));
        listener.testFinished(description);

        Assert.assertNull("an assume-skipped test's duration must not pollute the timing cache",
                TestTimingCache.load(provider()).get("ProgressListenerTest#skipped"));
    }

    @Test
    public void currentTestFlagsResetBetweenTests() throws Exception {
        // A failure on test #1 must not suppress the timing-cache write for test #2.
        ProgressListener listener = newListener("ProgressListenerTest#first", "ProgressListenerTest#second");
        Description first = descriptionFor("first");
        Description second = descriptionFor("second");

        listener.testStarted(first);
        listener.testFailure(new Failure(first, new AssertionError("boom")));
        listener.testFinished(first);

        listener.testStarted(second);
        Thread.sleep(SUBSTANTIVE_SLEEP_MS);
        listener.testFinished(second);

        Assert.assertNull(TestTimingCache.load(provider()).get("ProgressListenerTest#first"));
        Assert.assertNotNull("the failed flag must reset so the next test persists normally",
                TestTimingCache.load(provider()).get("ProgressListenerTest#second"));
    }

    @Test
    public void currentTestAssumptionFlagResetsBetweenTests() throws Exception {
        // Same as currentTestFlagsResetBetweenTests, but for the assumption-failure flag specifically.
        ProgressListener listener = newListener("ProgressListenerTest#skippedFirst", "ProgressListenerTest#second");
        Description first = descriptionFor("skippedFirst");
        Description second = descriptionFor("second");

        listener.testStarted(first);
        listener.testAssumptionFailure(new Failure(first, new AssumptionViolatedException("skip")));
        listener.testFinished(first);

        listener.testStarted(second);
        Thread.sleep(SUBSTANTIVE_SLEEP_MS);
        listener.testFinished(second);

        Assert.assertNull(TestTimingCache.load(provider()).get("ProgressListenerTest#skippedFirst"));
        Assert.assertNotNull("the assumption-failed flag must reset so the next test persists normally",
                TestTimingCache.load(provider()).get("ProgressListenerTest#second"));
    }

    @Test
    public void ignoredTestIsRemovedFromRemainingKeys() {
        ProgressListener listener = newListener("ProgressListenerTest#ignoredOne", "ProgressListenerTest#other");
        listener.testIgnored(descriptionFor("ignoredOne"));

        Assert.assertFalse(listener.remainingTestKeysSnapshot().contains("ProgressListenerTest#ignoredOne"));
        Assert.assertTrue(listener.remainingTestKeysSnapshot().contains("ProgressListenerTest#other"));
    }

    @Test
    public void finishedTestIsRemovedFromRemainingKeys() throws Exception {
        ProgressListener listener = newListener("ProgressListenerTest#done", "ProgressListenerTest#other");
        Description description = descriptionFor("done");

        listener.testStarted(description);
        Thread.sleep(SUBSTANTIVE_SLEEP_MS);
        listener.testFinished(description);

        Assert.assertFalse(listener.remainingTestKeysSnapshot().contains("ProgressListenerTest#done"));
        Assert.assertTrue(listener.remainingTestKeysSnapshot().contains("ProgressListenerTest#other"));
    }
}
