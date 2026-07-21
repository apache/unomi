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
     * Used by {@link #computeScale}; {@link #estimateRemainingMs} prefers wall-clock pace instead.
     */
    static final double MIN_SCALE = 0.25;
    static final double MAX_SCALE = 4.0;

    /**
     * Observed durations below this are treated as non-representative for scale (assumes / empty tests).
     */
    static final long SUBSTANTIVE_OBSERVED_MS = 100L;

    /**
     * Ignore observed/cached pairs more extreme than this when computing {@link #computeScale}
     * (e.g. historically-slow test that skipped in milliseconds).
     */
    static final double MAX_PAIR_SKEW = 20.0;

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
     * <strong>Real suite pace is primary:</strong> after every completion the estimate is rebuilt from
     * {@code elapsed / completed}. Historical per-test durations are only <em>hints</em> that reweight
     * remaining work when the leftover tests are historically heavier or lighter than the suite average
     * (so a block of slow tests still ahead raises ETA above a flat per-test rate).
     * <p>
     * A global “this run is 4× faster than cache” scale is <em>not</em> applied to shrink remaining
     * historical time — that is what made ETAs chronically too low after early assumes/skips.
     * If the run is <em>slower</em> than cache on substantive tests, remaining historical time is still
     * raised accordingly.
     * <p>
     * Cold start (nothing completed yet) falls back to the sum of historical hints (or a placeholder).
     *
     * @param remainingKeys keys still expected to run
     * @param cachedTimings historical durations for this persistence provider
     * @param observedVsCachedCompleted pairs of (observedMs, cachedMs) for completed tests that had history
     * @param completedDurations all completed durations this run (for fallback average)
     * @param elapsedTimeMs wall time since suite start
     * @return estimated remaining milliseconds (never negative)
     */
    static long estimateRemainingMs(Collection<String> remainingKeys,
                                    Map<String, Long> cachedTimings,
                                    Collection<long[]> observedVsCachedCompleted,
                                    Collection<Long> completedDurations,
                                    long elapsedTimeMs) {
        int remainingCount = remainingKeys == null ? 0 : remainingKeys.size();
        if (remainingCount == 0) {
            return 0L;
        }

        int completedCount = completedDurations == null ? 0 : completedDurations.size();
        double fallbackAvg = fallbackAverageMs(completedDurations, cachedTimings, elapsedTimeMs);
        double globalHintAvg = averagePositive(cachedTimings != null ? cachedTimings.values() : null);
        if (globalHintAvg <= 0.0) {
            globalHintAvg = fallbackAvg;
        }

        long hintRemainingMs = 0L;
        for (String key : remainingKeys) {
            Long cached = cachedTimings != null ? cachedTimings.get(key) : null;
            if (cached != null && cached > 0L) {
                hintRemainingMs += cached;
            } else {
                hintRemainingMs += Math.round(fallbackAvg);
            }
        }

        // Cold start: only historical hints (or placeholder average) are available.
        if (completedCount <= 0 || elapsedTimeMs <= 0L) {
            return Math.max(0L, hintRemainingMs);
        }

        double avgActualMs = elapsedTimeMs / (double) completedCount;
        // Flat live pace: every remaining test takes as long as the average so far.
        long rateEtaMs = Math.round(remainingCount * avgActualMs);

        // Hint-shaped live pace: same real average, but weight remaining tests by historical
        // duration relative to the suite's average historical duration.
        //   predict(r) = avgActual * (hint(r) / globalHintAvg)
        //   sum        = hintRemaining * avgActual / globalHintAvg
        long hintShapedEtaMs = rateEtaMs;
        if (globalHintAvg > 0.0) {
            hintShapedEtaMs = Math.round(hintRemainingMs * (avgActualMs / globalHintAvg));
        }

        // Always re-evaluate from live pace; hints may raise ETA when heavier work remains.
        long eta = Math.max(rateEtaMs, hintShapedEtaMs);

        // If substantive tests are slower than history, raise remaining toward scaled hints.
        double robustScale = computeScale(observedVsCachedCompleted);
        if (robustScale > 1.0) {
            double shrink = completedCount / (double) (completedCount + 15);
            double softenedScale = 1.0 + shrink * (robustScale - 1.0);
            eta = Math.max(eta, Math.round(hintRemainingMs * softenedScale));
        }

        return Math.max(0L, eta);
    }

    /**
     * How fast/slow this run is vs the historical cache for the same provider.
     * {@code 1.0} = on pace; {@code >1} = slower than history; {@code <1} = faster.
     * <p>
     * Pairs that look like assumes/skips (tiny observed, huge cached) are ignored so they do not
     * drag the scale to {@link #MIN_SCALE}.
     */
    static double computeScale(Collection<long[]> observedVsCachedCompleted) {
        if (observedVsCachedCompleted == null || observedVsCachedCompleted.isEmpty()) {
            return 1.0;
        }
        long observedSum = 0L;
        long cachedSum = 0L;
        for (long[] pair : observedVsCachedCompleted) {
            if (!isSubstantivePair(pair)) {
                continue;
            }
            observedSum += pair[0];
            cachedSum += pair[1];
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

    /**
     * {@code true} when the pair is usable for pace scaling (not an assume/skip vs huge cache).
     */
    static boolean isSubstantivePair(long[] pair) {
        if (pair == null || pair.length < 2) {
            return false;
        }
        long observed = pair[0];
        long cached = pair[1];
        if (observed < SUBSTANTIVE_OBSERVED_MS || cached <= 0L) {
            return false;
        }
        double skew = cached / (double) observed;
        return skew <= MAX_PAIR_SKEW && (observed / (double) cached) <= MAX_PAIR_SKEW;
    }

    private static double averagePositive(Collection<Long> values) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        long sum = 0L;
        int count = 0;
        for (Long value : values) {
            if (value != null && value > 0L) {
                sum += value;
                count++;
            }
        }
        return count == 0 ? 0.0 : sum / (double) count;
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
