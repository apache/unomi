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

import org.slf4j.Logger;

/**
 * Small, reusable helper for emitting structured, greppable diagnostic log lines when investigating
 * intermittent/flaky behaviour (e.g. match-none queries, index/rollover lifecycle gaps, eventual-consistency
 * timeouts). It is intentionally generic so the same convention can be reused across modules instead of
 * bespoke one-off logging.
 * <p>
 * All lines share a common {@link #PREFIX} and a {@code category}, followed by {@code key=value} pairs, e.g.:
 * <pre>[unomi-diag] category=es-match-none conditionTypeId=null visibleConditionTypes=42</pre>
 * Grep for {@code [unomi-diag]} to collect every diagnostic, or {@code category=<name>} for a specific one.
 * <p>
 * Callers pass their own {@link Logger} so the originating class still shows up in the log output. Formatting
 * only happens when the relevant level is enabled and, because these lines are meant for the (rare) failure
 * paths, they add no cost to the happy path.
 */
public final class DiagnosticLog {

    /** Common prefix on every diagnostic line; grep this to collect all diagnostics. */
    public static final String PREFIX = "[unomi-diag]";

    private DiagnosticLog() {
    }

    /**
     * Emits a WARN-level diagnostic line.
     *
     * @param logger    the caller's logger (so the source class is preserved); ignored if {@code null}
     * @param category  short, stable category name (e.g. {@code es-match-none}, {@code rollover-index})
     * @param keyValues alternating key/value pairs; a trailing key without a value renders as empty
     */
    public static void warn(final Logger logger, final String category, final Object... keyValues) {
        if (logger != null && logger.isWarnEnabled()) {
            logger.warn("{} category={} {}", PREFIX, category, format(keyValues));
        }
    }

    /**
     * Emits an INFO-level diagnostic line. Use for lifecycle checkpoints that are useful even on healthy runs.
     *
     * @param logger    the caller's logger (so the source class is preserved); ignored if {@code null}
     * @param category  short, stable category name
     * @param keyValues alternating key/value pairs; a trailing key without a value renders as empty
     */
    public static void info(final Logger logger, final String category, final Object... keyValues) {
        if (logger != null && logger.isInfoEnabled()) {
            logger.info("{} category={} {}", PREFIX, category, format(keyValues));
        }
    }

    /**
     * Formats alternating key/value pairs into a single {@code key=value key=value} string.
     *
     * @param keyValues alternating key/value pairs
     * @return the formatted string (empty when no pairs are provided)
     */
    public static String format(final Object... keyValues) {
        if (keyValues == null || keyValues.length == 0) {
            return "";
        }
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keyValues.length; i += 2) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(keyValues[i]).append('=');
            sb.append(i + 1 < keyValues.length ? String.valueOf(keyValues[i + 1]) : "");
        }
        return sb.toString();
    }
}
