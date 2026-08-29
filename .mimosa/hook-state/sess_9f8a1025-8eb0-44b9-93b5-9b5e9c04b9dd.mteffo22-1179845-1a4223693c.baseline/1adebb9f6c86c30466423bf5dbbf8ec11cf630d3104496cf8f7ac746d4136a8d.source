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
package org.apache.unomi.api.utils;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class PollingUtilsTest {

    // --- utility class structure ---

    @Test
    public void testPrivateConstructorForCoverage() throws Exception {
        Constructor<PollingUtils> ctor = PollingUtils.class.getDeclaredConstructor();
        assertFalse("Constructor should be private", Modifier.isPublic(ctor.getModifiers()));
        ctor.setAccessible(true);
        ctor.newInstance(); // covers the private constructor body
    }

    // --- input validation ---

    @Test(expected = IllegalArgumentException.class)
    public void testNegativeMaxRetriesThrowsIllegalArgument() throws InterruptedException {
        PollingUtils.pollUntil("fail", () -> "x", v -> true, 0, -1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNegativeDelayThrowsIllegalArgument() throws InterruptedException {
        PollingUtils.pollUntil("fail", () -> "x", v -> true, -1, 3);
    }

    // --- success paths ---

    @Test
    public void testReturnsImmediatelyWhenFirstAttemptSatisfiesPredicate() throws InterruptedException {
        String result = PollingUtils.pollUntil("fail", () -> "hello", "hello"::equals, 0, 3);
        assertEquals("hello", result);
    }

    @Test
    public void testReturnsAfterSeveralAttempts() throws InterruptedException {
        AtomicInteger calls = new AtomicInteger(0);
        // Returns null for the first two calls, "ready" on the third
        String result = PollingUtils.pollUntil(
                "should eventually be ready",
                () -> calls.incrementAndGet() >= 3 ? "ready" : null,
                "ready"::equals,
                1, 5
        );
        assertEquals("ready", result);
        assertEquals(3, calls.get());
    }

    @Test
    public void testSucceedsOnLastPossibleAttempt() throws InterruptedException {
        AtomicInteger calls = new AtomicInteger(0);
        int maxRetries = 4;
        Integer result = PollingUtils.pollUntil(
                "fail",
                calls::incrementAndGet,
                n -> n == maxRetries,
                1, maxRetries
        );
        assertEquals(maxRetries, (int) result);
        assertEquals(maxRetries, calls.get());
    }

    // --- exhaustion: last value non-null ---

    @Test
    public void testExceptionMessageContainsFailMessageAndLastValue() {
        try {
            PollingUtils.pollUntil("not found", () -> "last-result", v -> false, 1, 2);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue("fail message should be in exception", e.getMessage().contains("not found"));
            assertTrue("last value should be in exception", e.getMessage().contains("last-result"));
        } catch (InterruptedException e) {
            fail("Should not be interrupted");
        }
    }

    @Test
    public void testCallCountMatchesMaxRetriesOnExhaustion() {
        AtomicInteger calls = new AtomicInteger(0);
        try {
            PollingUtils.pollUntil("exhausted", calls::incrementAndGet, v -> false, 1, 5);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertEquals("should have tried exactly maxRetries times", 5, calls.get());
        } catch (InterruptedException e) {
            fail("Should not be interrupted");
        }
    }

    // --- exhaustion: last value null ---

    @Test
    public void testExceptionMessageOmitsDetailWhenCallReturnsNull() {
        // Covers the last != null → false branch: call runs but returns null, predicate never satisfied
        try {
            PollingUtils.pollUntil("null result", () -> null, v -> false, 1, 2);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertEquals("message should be exactly the failMessage with no detail appended",
                    "null result", e.getMessage());
        } catch (InterruptedException e) {
            fail("Should not be interrupted");
        }
    }

    @Test
    public void testZeroMaxRetriesMeansNoCallsAndImmediateThrow() {
        // Covers: loop never entered, last stays null, throws with plain failMessage
        AtomicInteger calls = new AtomicInteger(0);
        try {
            PollingUtils.pollUntil("zero retries", () -> { calls.incrementAndGet(); return "x"; }, "x"::equals, 1, 0);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertEquals(0, calls.get());
            assertEquals("zero retries", e.getMessage());
        } catch (InterruptedException e) {
            fail("Should not be interrupted");
        }
    }

    // --- null return value on success ---

    @Test
    public void testCanReturnNullWhenPredicateAcceptsNull() throws InterruptedException {
        // Callers may wait for something to become absent; predicate v -> v == null is valid
        String result = PollingUtils.pollUntil("fail", () -> null, v -> v == null, 0, 3);
        assertNull(result);
    }

    // --- supplier throws ---

    @Test
    public void testRuntimeExceptionFromSupplierPropagatesUnwrapped() {
        RuntimeException cause = new IllegalArgumentException("bad call");
        try {
            PollingUtils.pollUntil("fail", () -> { throw cause; }, v -> true, 0, 3);
            fail("Expected RuntimeException");
        } catch (RuntimeException e) {
            assertSame("exception should propagate without wrapping", cause, e);
        } catch (InterruptedException e) {
            fail("Should not be interrupted");
        }
    }

    // --- interruption ---

    @Test
    public void testPropagatesInterruptionDuringSleep() throws Exception {
        AtomicInteger calls = new AtomicInteger(0);
        Thread thread = new Thread(() -> {
            try {
                PollingUtils.pollUntil("interrupted", () -> {
                    calls.incrementAndGet();
                    return null;
                }, v -> false, 10_000, 100);
                fail("Should not reach here");
            } catch (InterruptedException e) {
                // expected — thread was interrupted during the inter-attempt sleep
            }
        });
        thread.start();
        // Wait for at least one call to complete, then interrupt during the long sleep
        while (calls.get() == 0) {
            Thread.sleep(1);
        }
        thread.interrupt();
        thread.join(2000);
        assertFalse("Thread should have finished after interruption", thread.isAlive());
    }
}
