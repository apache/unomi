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

import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Local, best-effort cache of individual IT execution times, used by {@link ProgressListener} to make its
 * estimated-time-remaining calculation more accurate than a single flat running average.
 * <p>
 * The cache is a plain properties file kept under {@code user.dir} (typically the {@code itests} module
 * directory, not {@code target/}) so it survives {@code mvn clean}, with <strong>one file per
 * persistence provider</strong> ({@code elasticsearch}, {@code opensearch}, {@code postgresql}, …)
 * since backends have different timing profiles and must not be averaged together.
 * <p>
 * This is a local developer convenience, not build state: all I/O failures are swallowed so a missing or
 * unwritable cache (e.g. a read-only or ephemeral CI workspace) never fails the IT run — it just falls back
 * to the in-run average for every test.
 */
final class TestTimingCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestTimingCache.class);

    /** Weight given to a freshly observed duration when blending it into the persisted average. */
    private static final double SMOOTHING = 0.3;

    /**
     * Clamp for the live-run vs historical scale factor so a few outliers cannot make ETA absurd.
     */
    static final double MIN_SCALE = 0.25;
    static final double MAX_SCALE = 4.0;

    private TestTimingCache() {
    }

    /**
     * Builds the cache key correlating a completed JUnit test with a persisted timing entry.
     *
     * @param description the description of the test that just ran
     * @return a "SimpleClassName#methodName" key
     */
    static String keyFor(Description description) {
        String displayName = description.getDisplayName();
        if (displayName.contains("(") && displayName.contains(")")) {
            int methodEnd = displayName.indexOf('(');
            int classStart = methodEnd + 1;
            int classEnd = displayName.indexOf(')');
            if (methodEnd > 0 && classEnd > classStart) {
                String methodName = displayName.substring(0, methodEnd);
                String className = displayName.substring(classStart, classEnd);
                int lastDot = className.lastIndexOf('.');
                String simpleClassName = (lastDot >= 0) ? className.substring(lastDot + 1) : className;
                return simpleClassName + "#" + methodName;
            }
        }
        return displayName;
    }

    /**
     * Builds the cache key for a test method discovered via reflection, before it has ever run.
     *
     * @param testClass the concrete test class the method will run on
     * @param methodName the {@code @Test} method name
     * @return a "SimpleClassName#methodName" key, matching {@link #keyFor(Description)}
     */
    static String keyFor(Class<?> testClass, String methodName) {
        return testClass.getSimpleName() + "#" + methodName;
    }

    /**
     * Loads the previously persisted timings for the given persistence provider.
     *
     * @param persistenceProvider provider id (e.g. {@code elasticsearch}, {@code opensearch}, {@code postgresql})
     * @return a mutable map of cache key to last known duration in milliseconds; empty if no cache
     *         exists yet or it could not be read
     */
    static Map<String, Long> load(String persistenceProvider) {
        Map<String, Long> timings = new HashMap<>();
        Path cacheFile = cacheFile(persistenceProvider);
        if (!Files.isReadable(cacheFile)) {
            return timings;
        }
        Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(cacheFile, StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (IOException e) {
            LOGGER.debug("Unable to read test timing cache at {} (ETAs will use the in-run average instead): {}",
                    cacheFile, e.getMessage());
            return timings;
        }
        for (String key : props.stringPropertyNames()) {
            try {
                timings.put(key, Long.parseLong(props.getProperty(key)));
            } catch (NumberFormatException e) {
                // Ignore a malformed entry rather than failing the whole cache load
            }
        }
        return timings;
    }

    /**
     * Merges freshly observed durations into the persisted cache for the given persistence provider,
     * smoothing each updated entry with an exponential moving average so a single unusually slow/fast
     * run doesn't swing future ETAs too far.
     *
     * @param persistenceProvider provider id the run just executed against
     * @param observedTimings durations (in milliseconds) observed during the run that just finished
     */
    static void save(String persistenceProvider, Map<String, Long> observedTimings) {
        if (observedTimings.isEmpty()) {
            return;
        }
        Path cacheFile = cacheFile(persistenceProvider);
        try {
            Map<String, Long> merged = load(persistenceProvider);
            for (Map.Entry<String, Long> entry : observedTimings.entrySet()) {
                Long previous = merged.get(entry.getKey());
                long updated = previous == null
                        ? entry.getValue()
                        : Math.round(previous * (1 - SMOOTHING) + entry.getValue() * SMOOTHING);
                merged.put(entry.getKey(), updated);
            }
            Properties props = new Properties();
            merged.forEach((key, value) -> props.setProperty(key, String.valueOf(value)));

            Path parent = cacheFile.toAbsolutePath().getParent();
            Path tempFile = Files.createTempFile(parent, "test-timing-cache", ".tmp");
            try (Writer writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
                props.store(writer, "Apache Unomi IT test timing cache per persistence provider "
                        + "(local dev aid, safe to delete)");
            }
            Files.move(tempFile, cacheFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            LOGGER.debug("Unable to persist test timing cache at {} (ETAs will just use the in-run average next time): {}",
                    cacheFile, e.getMessage());
        }
    }

    /**
     * Estimates remaining wall time for unfinished tests.
     * <p>
     * For each remaining test with a historical entry, uses that duration scaled by how fast/slow
     * <em>this</em> run has been relative to history (ratio of observed vs cached for completed
     * tests that had a cache hit). Uncached remaining tests use the in-run average of completed
     * durations (or the median of historical values when nothing has completed yet).
     *
     * @param remainingKeys keys still expected to run
     * @param cachedTimings historical durations for this persistence provider
     * @param observedVsCachedCompleted pairs of (observedMs, cachedMs) for completed tests that had history
     * @param completedDurations all completed durations this run (for fallback average)
     * @param elapsedTimeMs wall time since suite start (unused for sum; kept for API clarity)
     * @return estimated remaining milliseconds (never negative)
     */
    static long estimateRemainingMs(Collection<String> remainingKeys,
                                    Map<String, Long> cachedTimings,
                                    Collection<long[]> observedVsCachedCompleted,
                                    Collection<Long> completedDurations,
                                    long elapsedTimeMs) {
        double scale = computeScale(observedVsCachedCompleted);
        double fallbackAvg = fallbackAverageMs(completedDurations, cachedTimings, elapsedTimeMs);

        long estimate = 0L;
        for (String key : remainingKeys) {
            Long cached = cachedTimings.get(key);
            if (cached != null && cached > 0L) {
                estimate += Math.round(cached * scale);
            } else {
                estimate += Math.round(fallbackAvg);
            }
        }
        return Math.max(0L, estimate);
    }

    /**
     * How fast/slow this run is vs the historical cache for the same provider.
     * {@code 1.0} = on pace; {@code >1} = slower than history; {@code <1} = faster.
     */
    static double computeScale(Collection<long[]> observedVsCachedCompleted) {
        if (observedVsCachedCompleted == null || observedVsCachedCompleted.isEmpty()) {
            return 1.0;
        }
        long observedSum = 0L;
        long cachedSum = 0L;
        for (long[] pair : observedVsCachedCompleted) {
            if (pair == null || pair.length < 2) {
                continue;
            }
            if (pair[0] > 0L && pair[1] > 0L) {
                observedSum += pair[0];
                cachedSum += pair[1];
            }
        }
        if (cachedSum <= 0L || observedSum <= 0L) {
            return 1.0;
        }
        double scale = (double) observedSum / (double) cachedSum;
        if (scale < MIN_SCALE) {
            return MIN_SCALE;
        }
        if (scale > MAX_SCALE) {
            return MAX_SCALE;
        }
        return scale;
    }

    private static double fallbackAverageMs(Collection<Long> completedDurations,
                                            Map<String, Long> cachedTimings,
                                            long elapsedTimeMs) {
        if (completedDurations != null && !completedDurations.isEmpty()) {
            long sum = 0L;
            for (Long d : completedDurations) {
                if (d != null && d > 0L) {
                    sum += d;
                }
            }
            return sum / (double) completedDurations.size();
        }
        if (cachedTimings != null && !cachedTimings.isEmpty()) {
            long sum = 0L;
            for (Long d : cachedTimings.values()) {
                if (d != null && d > 0L) {
                    sum += d;
                }
            }
            return sum / (double) cachedTimings.size();
        }
        // Cold start: tiny placeholder so ETA is non-zero until the first test finishes
        return elapsedTimeMs > 0L ? elapsedTimeMs : 30_000L;
    }

    static Path cacheFile(String persistenceProvider) {
        String normalized = (persistenceProvider == null || persistenceProvider.isEmpty())
                ? "unknown"
                : persistenceProvider.toLowerCase(Locale.ROOT);
        return Paths.get(System.getProperty("user.dir", "."), ".test-timing-cache-" + normalized + ".properties");
    }
}
