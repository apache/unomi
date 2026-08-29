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

import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Utility methods for polling until a condition is met.
 */
public final class PollingUtils {

    private PollingUtils() {}

    /**
     * Polls {@code call} repeatedly until {@code predicate} is satisfied or retries are exhausted.
     * The first attempt is made immediately (no leading sleep).
     *
     * @param <T>          type returned by {@code call}
     * @param failMessage  message for the exception if the predicate is never satisfied
     * @param call         supplier invoked on each attempt
     * @param predicate    condition that must hold on the returned value
     * @param delayMillis  milliseconds to sleep between attempts (must be &gt;= 0)
     * @param maxRetries   maximum number of attempts (must be &gt;= 0); 0 means no calls are made
     *                     and {@code IllegalStateException} is thrown immediately
     * @return the first value satisfying {@code predicate}
     * @throws IllegalArgumentException if {@code delayMillis} or {@code maxRetries} is negative
     * @throws InterruptedException  if the thread is interrupted while sleeping between attempts
     * @throws IllegalStateException if the predicate is not satisfied within {@code maxRetries} attempts
     */
    public static <T> T pollUntil(String failMessage, Supplier<T> call, Predicate<T> predicate,
                                   long delayMillis, int maxRetries) throws InterruptedException {
        if (delayMillis < 0) {
            throw new IllegalArgumentException("delayMillis must be non-negative, got: " + delayMillis);
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be non-negative, got: " + maxRetries);
        }
        T last = null;
        for (int i = 0; i < maxRetries; i++) {
            if (i > 0) {
                Thread.sleep(delayMillis);
            }
            last = call.get();
            if (predicate.test(last)) {
                return last;
            }
        }
        String detail = last != null ? " (last value: " + last + ")" : "";
        throw new IllegalStateException(failMessage + detail);
    }
}
