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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Comprehensive unit tests for polling utility methods with 100% edge case coverage:
 * keepTrying(), waitForNullValue(), and shouldBeTrueUntilEnd().
 */
public class PollingUtilsTest {

    private static final int SHORT_TIMEOUT = 5;
    private static final int ZERO_TIMEOUT = 0;
    private static final int LONG_RETRIES = 10;
    private static final int MED_RETRIES = 5;

    private PollingTestHelper helper;

    @Before
    public void setUp() {
        helper = new PollingTestHelper();
    }

    // ========== HELPER METHODS - DRY Principle ==========

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws InterruptedException;
    }

    /**
     * Asserts that calling a method throws a specific exception with expected message.
     * Eliminates repeated try-catch-Assert.fail pattern.
     */
    private <E extends Throwable> void assertThrowsWithMessage(
            String message, Class<E> exceptionType, CheckedRunnable operation, String expectedMessageFragment) throws InterruptedException {
        try {
            operation.run();
            Assert.fail(message);
        } catch (Throwable e) {
            Assert.assertTrue("Exception type mismatch: expected " + exceptionType.getSimpleName() +
                    " but got " + e.getClass().getSimpleName(), exceptionType.isInstance(e));
            Assert.assertTrue("Expected message to contain '" + expectedMessageFragment +
                    "' but got: " + e.getMessage(), e.getMessage().contains(expectedMessageFragment));
        }
    }

    /**
     * Verifies a supplier succeeds with exact return value and call count.
     * Combines result verification with side-effect verification.
     */
    private <T> void assertSucceedsWithCallCount(String testName, int expectedCallCount,
            T expectedValue, int timeout, int retries, java.util.function.Supplier<T> supplier,
            java.util.function.Predicate<T> predicate) throws InterruptedException {
        AtomicInteger callCount = new AtomicInteger(0);
        T result = helper.keepTrying(testName, () -> {
            callCount.incrementAndGet();
            return supplier.get();
        }, predicate, timeout, retries);
        Assert.assertEquals("Call count mismatch", expectedCallCount, callCount.get());
        Assert.assertEquals("Return value mismatch", expectedValue, result);
    }

    /**
     * Simplified success assertion without call count tracking (when side effects don't matter).
     */
    private <T> T assertSucceeds(String testName, int timeout, int retries,
            java.util.function.Supplier<T> supplier, java.util.function.Predicate<T> predicate)
            throws InterruptedException {
        return helper.keepTrying(testName, supplier, predicate, timeout, retries);
    }

    /**
     * Calculates expected call count: 1 initial call + retries.
     */
    private int expectedCallCount(int retries) {
        return retries + 1;
    }

    /**
     * Verifies supplier succeeds with expected call count (eliminates 11 repeated test patterns).
     */
    private <T> void verifySuccessWithExactCallCount(String testName, int retries,
            java.util.function.Function<Integer, T> supplierByCallNumber,
            java.util.function.Predicate<T> predicate, T expectedResult)
            throws InterruptedException {
        AtomicInteger callCount = new AtomicInteger(0);
        T result = helper.keepTrying(testName, () -> {
            int callNum = callCount.incrementAndGet();
            return supplierByCallNumber.apply(callNum);
        }, predicate, ZERO_TIMEOUT, retries);
        Assert.assertEquals("Expected exactly " + expectedCallCount(retries) + " calls for retries=" + retries,
                expectedCallCount(retries), callCount.get());
        Assert.assertEquals("Return value mismatch", expectedResult, result);
    }

    // ========== keepTrying() SUCCESS CASES ==========

    @Test
    public void testKeepTryingSucceedsImmediately() throws InterruptedException {
        Integer result = assertSucceeds("Find value", SHORT_TIMEOUT, MED_RETRIES, () -> 42, v -> v == 42);
        Assert.assertEquals(Integer.valueOf(42), result);
    }

    @Test
    public void testKeepTryingSucceedsAfterExactlyOneRetry() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger(0);
        Integer result = helper.keepTrying("After 1 retry", () -> counter.getAndIncrement() == 0 ? null : 99,
                v -> v != null, 5, 10);
        Assert.assertEquals(Integer.valueOf(99), result);
        Assert.assertEquals(2, counter.get());
    }

    @Test
    public void testKeepTryingSucceedsAfterMaxRetries() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger(0);
        Integer result = helper.keepTrying("At max retries", () -> counter.getAndIncrement() < 3 ? null : 100,
                v -> v != null, 5, 3);
        Assert.assertEquals(Integer.valueOf(100), result);
        Assert.assertEquals(4, counter.get());
    }

    @Test
    public void testKeepTryingWaitingForNull() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger(2);
        Integer result = helper.keepTrying("Become null", () -> counter.decrementAndGet() > 0 ? 1 : null,
                v -> v == null, 5, 10);
        Assert.assertNull(result);
    }

    @Test
    public void testKeepTryingNullImmediately() throws InterruptedException {
        Integer result = helper.keepTrying("Already null", () -> null, v -> v == null, 5, 5);
        Assert.assertNull(result);
    }

    @Test
    public void testKeepTryingWithZeroTimeout() throws InterruptedException {
        Integer result = helper.keepTrying("Zero timeout", () -> 1, v -> v == 1, 0, 5);
        Assert.assertEquals(Integer.valueOf(1), result);
    }

    @Test
    public void testKeepTryingWithZeroRetries() throws InterruptedException {
        Integer result = helper.keepTrying("Zero retries ok", () -> 5, v -> v == 5, 10, 0);
        Assert.assertEquals(Integer.valueOf(5), result);
    }

    @Test
    public void testKeepTryingWithNegativeTimeout() throws InterruptedException {
        assertThrowsWithMessage("Negative timeout", IllegalArgumentException.class,
                () -> helper.keepTrying("Negative timeout", () -> 1, v -> v == 1, -1, 5),
                "timeout must be non-negative");
    }

    @Test
    public void testKeepTryingWithNegativeRetries() throws InterruptedException {
        assertThrowsWithMessage("Negative retries", IllegalArgumentException.class,
                () -> helper.keepTrying("Negative retries", () -> 1, v -> v == 1, 10, -1),
                "retries must be non-negative");
    }

    @Test
    public void testKeepTryingReturnsDifferentTypes() throws InterruptedException {
        String result = helper.keepTrying("String type", () -> "test", v -> v.length() == 4, 5, 5);
        Assert.assertEquals("test", result);
    }

    @Test
    public void testKeepTryingWithComplexPredicate() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger(0);
        Integer result = helper.keepTrying("Complex predicate",
                () -> counter.getAndIncrement(),
                v -> v >= 2 && v % 2 == 0,
                5, 10);
        Assert.assertEquals(Integer.valueOf(2), result);
    }

    // ========== keepTrying() FAILURE CASES ==========

    @Test
    public void testKeepTryingFailsWhenPredicateNeverSatisfied() throws InterruptedException {
        assertThrowsWithMessage("Predicate never satisfied", AssertionError.class,
                () -> helper.keepTrying("Never satisfied", () -> "test", v -> false, 5, 2),
                "Never satisfied");
    }

    @Test
    public void testKeepTryingFailsRespectsRetryLimit() throws InterruptedException {
        AtomicInteger callCount = new AtomicInteger(0);
        try {
            helper.keepTrying("Exact limit", () -> {
                callCount.incrementAndGet();
                return "fail";
            }, v -> false, ZERO_TIMEOUT, 2);
            Assert.fail("Should throw AssertionError");
        } catch (AssertionError e) {
            Assert.assertEquals("With retries=2, expects 1 initial + 2 retries", 3, callCount.get());
        }
    }

    @Test
    public void testKeepTryingFailureMessageIncludesLastValue() throws InterruptedException {
        assertThrowsWithMessage("Failure message includes value", AssertionError.class,
                () -> helper.keepTrying("Custom message", () -> "actual_value", v -> false, 5, 0),
                "Custom message");
    }

    @Test
    public void testKeepTryingFailureWhenNullAndPredicateRequiresNonNull() throws InterruptedException {
        assertThrowsWithMessage("Null fails when non-null required", AssertionError.class,
                () -> helper.keepTrying("Expects non-null", () -> null, v -> v != null, 5, 1),
                "Expects non-null");
    }

    @Test
    public void testKeepTryingExceptionInSupplierPropagates() throws InterruptedException {
        assertThrowsWithMessage("Supplier exception propagates", RuntimeException.class,
                () -> helper.keepTrying("Exception test", () -> {
                    throw new RuntimeException("supplier error");
                }, v -> true, 5, 2),
                "supplier error");
    }

    @Test
    public void testKeepTryingExceptionInPredicatePropagates() throws InterruptedException {
        assertThrowsWithMessage("Predicate exception propagates", IllegalArgumentException.class,
                () -> helper.keepTrying("Predicate error", () -> "test", v -> {
                    throw new IllegalArgumentException("predicate error");
                }, 5, 2),
                "predicate error");
    }

    // ========== waitForNullValue() SUCCESS CASES ==========

    @Test
    public void testWaitForNullValueAlreadyNull() throws InterruptedException {
        helper.waitForNullValue("Already null", () -> null, 5, 5);
    }

    @Test
    public void testWaitForNullValueBecomesNullAfterOne() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger(1);
        helper.waitForNullValue("One retry", () -> counter.decrementAndGet() > 0 ? 1 : null, 5, 10);
    }

    @Test
    public void testWaitForNullValueBecomesNullAtMaxRetries() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger(3);
        helper.waitForNullValue("At max", () -> counter.decrementAndGet() > 0 ? counter.get() : null, 5, 3);
    }

    @Test
    public void testWaitForNullValueWithZeroTimeout() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger(1);
        helper.waitForNullValue("Zero timeout", () -> counter.decrementAndGet() > 0 ? 1 : null, 0, 10);
    }

    // ========== waitForNullValue() FAILURE CASES ==========

    @Test
    public void testWaitForNullValueFailsWhenNeverNull() throws InterruptedException {
        assertThrowsWithMessage("Never becomes null", AssertionError.class,
                () -> helper.waitForNullValue("Never null", () -> "always", 5, 1),
                "Never null");
    }

    @Test
    public void testWaitForNullValueExceptionInSupplierPropagates() throws InterruptedException {
        assertThrowsWithMessage("Exception in waitForNullValue", RuntimeException.class,
                () -> helper.waitForNullValue("Supplier error", () -> {
                    throw new RuntimeException("null supplier failed");
                }, 5, 1),
                "null supplier failed");
    }

    // ========== shouldBeTrueUntilEnd() SUCCESS CASES ==========

    @Test
    public void testShouldBeTrueUntilEndAlwaysTrue() throws InterruptedException {
        String result = helper.shouldBeTrueUntilEnd("Always true", () -> "stable",
                v -> v != null, 5, 3);
        Assert.assertEquals("stable", result);
    }

    @Test
    public void testShouldBeTrueUntilEndZeroRetries() throws InterruptedException {
        AtomicInteger callCount = new AtomicInteger(0);
        String result = helper.shouldBeTrueUntilEnd("Zero retries",
                () -> {
                    callCount.incrementAndGet();
                    return "ok";
                },
                v -> v != null, 5, 0);
        Assert.assertEquals(1, callCount.get());
        Assert.assertEquals("ok", result);
    }

    @Test
    public void testShouldBeTrueUntilEndExactlyMaxRetries() throws InterruptedException {
        AtomicInteger callCount = new AtomicInteger(0);
        String result = helper.shouldBeTrueUntilEnd("Max retries",
                () -> {
                    callCount.incrementAndGet();
                    return "pass";
                },
                v -> v != null, 5, 3);
        Assert.assertEquals(4, callCount.get());
        Assert.assertEquals("pass", result);
    }

    @Test
    public void testShouldBeTrueUntilEndVerifyReturnValue() throws InterruptedException {
        String result = helper.shouldBeTrueUntilEnd("Return value", () -> "final_value",
                v -> "final_value".equals(v), 5, 2);
        Assert.assertEquals("final_value", result);
    }

    @Test
    public void testShouldBeTrueUntilEndWithZeroTimeout() throws InterruptedException {
        String result = helper.shouldBeTrueUntilEnd("Zero timeout", () -> "ok",
                v -> v != null, 0, 2);
        Assert.assertEquals("ok", result);
    }

    @Test
    public void testShouldBeTrueUntilEndWithNegativeTimeout() throws InterruptedException {
        assertThrowsWithMessage("Negative timeout until end", IllegalArgumentException.class,
                () -> helper.shouldBeTrueUntilEnd("Negative timeout", () -> "ok", v -> v != null, -1, 2),
                "timeout must be non-negative");
    }

    @Test
    public void testShouldBeTrueUntilEndWithNegativeRetries() throws InterruptedException {
        assertThrowsWithMessage("Negative retries until end", IllegalArgumentException.class,
                () -> helper.shouldBeTrueUntilEnd("Negative retries", () -> "ok", v -> v != null, 10, -1),
                "retries must be non-negative");
    }

    @Test
    public void testShouldBeTrueUntilEndComplexPredicate() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger(0);
        String result = helper.shouldBeTrueUntilEnd("Complex",
                () -> Integer.toString(counter.incrementAndGet()),
                v -> Integer.parseInt(v) >= 1,  // Always true for positive numbers
                ZERO_TIMEOUT, 3);
        Assert.assertEquals("4", result);  // Returns final value after 3 retries (initial + 3 = 4 calls)
    }

    // ========== shouldBeTrueUntilEnd() FAILURE CASES ==========

    @Test
    public void testShouldBeTrueUntilEndFailsOnInitialCheck() throws InterruptedException {
        assertThrowsWithMessage("Initial check fails", AssertionError.class,
                () -> helper.shouldBeTrueUntilEnd("Initial fails", () -> null, v -> v != null, 5, 3),
                "Initial fails");
    }

    @Test
    public void testShouldBeTrueUntilEndFailsAfterFirstRetry() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger(0);
        assertThrowsWithMessage("Fails after first retry", AssertionError.class,
                () -> helper.shouldBeTrueUntilEnd("Fails after retry",
                        () -> counter.getAndIncrement() == 0 ? "ok" : null,
                        v -> v != null, 5, 3),
                "Fails after retry");
    }

    @Test
    public void testShouldBeTrueUntilEndFailsAtMaxRetries() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger(0);
        assertThrowsWithMessage("Fails at max retries", AssertionError.class,
                () -> helper.shouldBeTrueUntilEnd("Fails at max",
                        () -> counter.getAndIncrement() < 3 ? "ok" : null,
                        v -> v != null, 5, 3),
                "Fails at max");
    }

    @Test
    public void testShouldBeTrueUntilEndExceptionInSupplierPropagates() throws InterruptedException {
        assertThrowsWithMessage("Supplier exception in until end", RuntimeException.class,
                () -> helper.shouldBeTrueUntilEnd("Supplier error",
                        () -> {
                            throw new RuntimeException("until end supplier error");
                        },
                        v -> true, 5, 1),
                "until end supplier error");
    }

    @Test
    public void testShouldBeTrueUntilEndExceptionInPredicatePropagates() throws InterruptedException {
        assertThrowsWithMessage("Predicate exception in until end", IllegalStateException.class,
                () -> helper.shouldBeTrueUntilEnd("Predicate error",
                        () -> "test",
                        v -> {
                            throw new IllegalStateException("until end predicate error");
                        },
                        5, 1),
                "until end predicate error");
    }

    // ========== EXACT CALL COUNT VERIFICATION ==========
    // These tests ensure the exact number of calls matches expected retry logic

    @Test
    public void testKeepTryingCallCountWithZeroRetries() throws InterruptedException {
        verifySuccessWithExactCallCount("Zero retries", 0, callNum -> 42, v -> v == 42, 42);
    }

    @Test
    public void testKeepTryingCallCountWithOneRetry() throws InterruptedException {
        verifySuccessWithExactCallCount("One retry", 1, callNum -> callNum == 2 ? 99 : null,
                v -> v != null, 99);
    }

    @Test
    public void testKeepTryingCallCountWithTwoRetries() throws InterruptedException {
        verifySuccessWithExactCallCount("Two retries", 2, callNum -> callNum == 3 ? 77 : null,
                v -> v != null, 77);
    }

    @Test
    public void testKeepTryingCallCountExactlyAtRetryLimit() throws InterruptedException {
        AtomicInteger callCount = new AtomicInteger(0);
        try {
            helper.keepTrying("At limit", () -> {
                callCount.incrementAndGet();
                return "fail";
            }, v -> false, 5, 3);
            Assert.fail("Should throw");
        } catch (AssertionError e) {
            Assert.assertEquals("With retries=3 and no success, should attempt 4 times before failing",
                    4, callCount.get());
        }
    }

    @Test
    public void testKeepTryingCallCountSucceedsOnLastRetry() throws InterruptedException {
        verifySuccessWithExactCallCount("Last retry", 3, callNum -> callNum == 4 ? 55 : null,
                v -> v != null, 55);
    }

    @Test
    public void testWaitForNullValueCallCountWithZeroRetries() throws InterruptedException {
        AtomicInteger callCount = new AtomicInteger(0);
        helper.waitForNullValue("Already null", () -> {
            callCount.incrementAndGet();
            return null;
        }, ZERO_TIMEOUT, 0);
        Assert.assertEquals("With retries=0, expects exactly 1 call", 1, callCount.get());
    }

    @Test
    public void testWaitForNullValueCallCountBecomesNullAfterOne() throws InterruptedException {
        AtomicInteger callCount = new AtomicInteger(0);
        helper.waitForNullValue("After one", () -> {
            int call = callCount.incrementAndGet();
            return call == 1 ? "value" : null;
        }, ZERO_TIMEOUT, 1);
        Assert.assertEquals("Becomes null on 2nd call, expects 2 total calls", 2, callCount.get());
    }

    @Test
    public void testShouldBeTrueUntilEndCallCountWithZeroRetries() throws InterruptedException {
        AtomicInteger callCount = new AtomicInteger(0);
        String result = helper.shouldBeTrueUntilEnd("Zero retries", () -> {
            callCount.incrementAndGet();
            return "ok";
        }, v -> v != null, ZERO_TIMEOUT, 0);
        Assert.assertEquals("With retries=0, expects 1 call", 1, callCount.get());
        Assert.assertEquals("ok", result);
    }

    @Test
    public void testShouldBeTrueUntilEndCallCountWithOneRetry() throws InterruptedException {
        AtomicInteger callCount = new AtomicInteger(0);
        String result = helper.shouldBeTrueUntilEnd("One retry", () -> {
            callCount.incrementAndGet();
            return "stable";
        }, v -> v != null, ZERO_TIMEOUT, 1);
        Assert.assertEquals("With retries=1, expects 2 calls", 2, callCount.get());
        Assert.assertEquals("stable", result);
    }

    @Test
    public void testShouldBeTrueUntilEndCallCountWithThreeRetries() throws InterruptedException {
        AtomicInteger callCount = new AtomicInteger(0);
        String result = helper.shouldBeTrueUntilEnd("Three retries", () -> {
            callCount.incrementAndGet();
            return "ok";
        }, v -> v != null, ZERO_TIMEOUT, 3);
        Assert.assertEquals("With retries=3, expects 4 calls", 4, callCount.get());
        Assert.assertEquals("ok", result);
    }

    @Test
    public void testShouldBeTrueUntilEndCallCountFailsOnSecondCheck() throws InterruptedException {
        AtomicInteger callCount = new AtomicInteger(0);
        try {
            helper.shouldBeTrueUntilEnd("Fails on 2nd", () -> {
                int call = callCount.incrementAndGet();
                return call == 1 ? "ok" : null;
            }, v -> v != null, ZERO_TIMEOUT, 3);
            Assert.fail("Should throw AssertionError");
        } catch (AssertionError e) {
            Assert.assertEquals("Fails on 2nd check, expects 2 calls before failure", 2, callCount.get());
            Assert.assertTrue(e.getMessage().contains("after 1 retries"));
        }
    }

    // ========== HELPER CLASS ==========

    public static class PollingTestHelper extends BaseIT {
        @Override
        public <T> T keepTrying(String failMessage, java.util.function.Supplier<T> call,
                                java.util.function.Predicate<T> predicate, int timeout, int retries)
                throws InterruptedException {
            return super.keepTrying(failMessage, call, predicate, timeout, retries);
        }

        @Override
        public <T> void waitForNullValue(String failMessage, java.util.function.Supplier<T> call,
                                        int timeout, int retries) throws InterruptedException {
            super.waitForNullValue(failMessage, call, timeout, retries);
        }

        @Override
        public <T> T shouldBeTrueUntilEnd(String failMessage, java.util.function.Supplier<T> call,
                                         java.util.function.Predicate<T> predicate, int timeout, int retries)
                throws InterruptedException {
            return super.shouldBeTrueUntilEnd(failMessage, call, predicate, timeout, retries);
        }

        @Override
        public org.ops4j.pax.exam.Option[] config() {
            return new org.ops4j.pax.exam.Option[0];
        }
    }
}
