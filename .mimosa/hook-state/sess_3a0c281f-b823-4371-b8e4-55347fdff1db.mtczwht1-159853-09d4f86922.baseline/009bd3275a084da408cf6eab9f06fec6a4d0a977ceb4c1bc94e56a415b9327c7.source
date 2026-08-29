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
     * Used by {@link #computeScale}; {@link #estimateRemainingMs} uses the live substantive-test
     * average as its primary pace signal and only applies this scale as an additional boost when
     * completed tests are individually slower than their cached history.
     */
    static final double MIN_SCALE = 0.25;
    static final double MAX_SCALE = 4.0;

    /**
     * Observed durations below this are treated as non-representative for pace/scale purposes
     * (assumes / skipped-in-substance tests that return almost instantly).
     */
    static final long SUBSTANTIVE_OBSERVED_MS = 100L;

    /**
     * Ignore a pair in {@link #computeScale} when its cached entry is more than this many times
     * bigger than what was actually observed. This only ever applies to pairs that already passed the
     * {@link #SUBSTANTIVE_OBSERVED_MS} gate (i.e. {@code observed} is not itself assume/skip-like) but
     * are still disproportionately faster than their own cached history — an outlier that would
     * otherwise drag the scale toward {@link #MIN_SCALE}. This is intentionally one-directional: a pair
     * where {@code observed} is much bigger than {@code cached} is a genuine regression signal and is
     * not excluded, so it can still raise the ETA.
     */
    static final double MAX_PAIR_SKEW = 20.0;

    /**
     * Upper bound for the true-cold-start placeholder average in {@link #fallbackAverageMs} (no
     * completed test and no historical cache at all). Growing with elapsed time keeps the ETA display
     * from looking frozen while nothing has finished yet, but must stay capped — otherwise a run that
     * stalls (e.g. several early failures with nothing successful yet) would balloon the placeholder,
     * and therefore the ETA for every remaining test, to an implausibly large number.
     */
    static final long COLD_START_PLACEHOLDER_CAP_MS = 60_000L;

    /**
     * An (observed, cached) duration sample for a single completed test that had a historical cache
     * entry, used by {@link #computeScale} to judge live pace vs history for individual tests.
     */
    record TimingSample(long observedMs, long cachedMs) {
    }

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
                LOGGER.debug("Ignoring malformed test timing cache entry {}={} in {}: {}",
                        key, props.getProperty(key), cacheFile, e.getMessage());
            }
        }
        return timings;
    }

    /**
     * Merges freshly observed durations into the persisted cache for the given persistence provider,
     * smoothing each updated entry with an exponential moving average so a single unusually slow/fast
     * run doesn't swing future ETAs too far.
     * <p>
     * Durations below {@link #SUBSTANTIVE_OBSERVED_MS} (assume/skip-like) are ignored entirely rather
     * than blended in — a test that occasionally short-circuits via an early assume/skip would
     * otherwise gradually drag its historical average down toward that non-representative value.
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
            boolean changed = false;
            for (Map.Entry<String, Long> entry : observedTimings.entrySet()) {
                Long observed = entry.getValue();
                if (observed == null || observed < SUBSTANTIVE_OBSERVED_MS) {
                    continue;
                }
                Long previous = merged.get(entry.getKey());
                long updated = previous == null
                        ? observed
                        : Math.round(previous * (1 - SMOOTHING) + observed * SMOOTHING);
                merged.put(entry.getKey(), updated);
                changed = true;
            }
            if (!changed) {
                return;
            }
            Properties props = new Properties();
            merged.forEach((key, value) -> props.setProperty(key, String.valueOf(value)));

            Path parent = cacheFile.toAbsolutePath().getParent();
            Path tempFile = Files.createTempFile(parent, "test-timing-cache", ".tmp");
            try {
                try (Writer writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
                    props.store(writer, "Apache Unomi IT test timing cache per persistence provider "
                            + "(local dev aid, safe to delete)");
                }
                Files.move(tempFile, cacheFile, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                // Best-effort cleanup only: after a successful move this is already gone, and any
                // exception here must not mask a real failure from the write/move above.
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    // Nothing more we can do; the temp file is harmless local dev-workspace clutter.
                }
            }
        } catch (IOException e) {
            LOGGER.debug("Unable to persist test timing cache at {} (ETAs will just use the in-run average next time): {}",
                    cacheFile, e.getMessage());
        } catch (RuntimeException e) {
            // Distinct from the expected-I/O-failure case above: an unexpected exception here means a
            // real bug in the merge/blend logic, not just a read-only/ephemeral workspace.
            LOGGER.warn("Unexpected error persisting test timing cache at {} (ETAs will just use the in-run average next time)",
                    cacheFile, e);
        }
    }

    /**
     * Estimates remaining wall time for unfinished tests.
     * <p>
     * <strong>Live pace is primary:</strong> the pace is the average duration of <em>substantive</em>
     * completed tests this run (real work, excluding near-instant assume/skip-like completions — see
     * {@link #SUBSTANTIVE_OBSERVED_MS}). Historical per-test durations are only <em>hints</em> that
     * reweight remaining work by how each remaining test's historical duration compares to the suite's
     * average historical duration — a block of historically slow tests still ahead raises the ETA above
     * a flat per-test rate, and a tail of historically light tests lowers it below that rate.
     * <p>
     * Deriving pace from substantive durations only — rather than {@code elapsedTimeMs / completed}
     * over every finished test — avoids two failure modes: (1) the caller never adds a failed or
     * assume-aborted test's duration to {@code completedDurations} (see {@link ProgressListener}), so
     * its wall time cannot inflate the pace the way a naive elapsed/count ratio would; (2) a run of fast
     * assumes/skips before a block of heavy tests cannot drag the pace toward zero, since those
     * near-instant completions are excluded from the average rather than counted as "typical" tests.
     * <p>
     * A global “this run is 4× faster than cache” scale is <em>not</em> applied to shrink remaining
     * historical time — that is what made ETAs chronically too low after early assumes/skips.
     * If the run is <em>slower</em> than cache on substantive tests, remaining historical time is still
     * raised accordingly via {@link #computeScale}.
     * <p>
     * Cold start (no completed test yet, substantive or not) falls back to the historical average pace,
     * so remaining tests are estimated at their full cached weight until real live data says otherwise.
     *
     * @param remainingKeys keys still expected to run
     * @param cachedTimings historical durations for this persistence provider
     * @param observedVsCachedCompleted samples of (observedMs, cachedMs) for completed tests that had history
     * @param completedDurations successful completed durations this run (for live pace and fallback average)
     * @param elapsedTimeMs wall time since suite start; only its sign is used, to detect the cold-start case
     * @return estimated remaining milliseconds (never negative)
     */
    static long estimateRemainingMs(Collection<String> remainingKeys,
                                    Map<String, Long> cachedTimings,
                                    Collection<TimingSample> observedVsCachedCompleted,
                                    Collection<Long> completedDurations,
                                    long elapsedTimeMs) {
        int remainingCount = remainingKeys == null ? 0 : remainingKeys.size();
        if (remainingCount == 0) {
            return 0L;
        }

        int completedCount = completedDurations == null ? 0 : completedDurations.size();
        double fallbackAvg = fallbackAverageMs(completedDurations, cachedTimings, elapsedTimeMs);
        // fallbackAvg is guaranteed > 0 (see fallbackAverageMs), so this is always positive too.
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

        // Live pace comes only from substantive completions so neither a batch of trivial
        // assume/skip successes nor (by construction — see caller) any failed/aborted test's wall
        // time can skew it; fall back to the historical average until we have such a data point.
        double substantiveAvgMs = averageAtLeast(completedDurations, SUBSTANTIVE_OBSERVED_MS);
        double avgActualMs = substantiveAvgMs > 0.0 ? substantiveAvgMs : globalHintAvg;

        // Weight remaining work by how each remaining test's historical duration compares to the
        // suite's average historical duration, then rescale that shape to today's live pace:
        //   predict(r) = avgActual * (hint(r) / globalHintAvg)
        //   sum        = hintRemaining * avgActual / globalHintAvg
        // This is genuinely bidirectional: it raises the ETA above a flat live-pace rate when the
        // remaining tests are historically heavier than average, and lowers it below that rate when
        // they're historically lighter — both driven by today's real pace, not a historical multiplier.
        long eta = Math.round(hintRemainingMs * (avgActualMs / globalHintAvg));

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
    static double computeScale(Collection<TimingSample> observedVsCachedCompleted) {
        if (observedVsCachedCompleted == null || observedVsCachedCompleted.isEmpty()) {
            return 1.0;
        }
        long observedSum = 0L;
        long cachedSum = 0L;
        for (TimingSample sample : observedVsCachedCompleted) {
            if (!isSubstantivePair(sample)) {
                continue;
            }
            observedSum += sample.observedMs();
            cachedSum += sample.cachedMs();
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
     * {@code true} when the sample is usable for pace scaling (not an assume/skip vs huge cache).
     * See {@link #MAX_PAIR_SKEW} for why this is one-directional.
     */
    static boolean isSubstantivePair(TimingSample sample) {
        if (sample == null) {
            return false;
        }
        long observed = sample.observedMs();
        long cached = sample.cachedMs();
        if (observed < SUBSTANTIVE_OBSERVED_MS || cached <= 0L) {
            return false;
        }
        double skew = cached / (double) observed;
        return skew <= MAX_PAIR_SKEW;
    }

    private static double averagePositive(Collection<Long> values) {
        return averageAtLeast(values, 1L);
    }

    /**
     * Average of the values that are {@code >= minValue}, ignoring everything else (missing,
     * non-positive, or below the threshold). {@code 0.0} when nothing qualifies.
     */
    private static double averageAtLeast(Collection<Long> values, long minValue) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        long sum = 0L;
        int count = 0;
        for (Long value : values) {
            if (value != null && value >= minValue) {
                sum += value;
                count++;
            }
        }
        return count == 0 ? 0.0 : sum / (double) count;
    }

    private static double fallbackAverageMs(Collection<Long> completedDurations,
                                            Map<String, Long> cachedTimings,
                                            long elapsedTimeMs) {
        double avg = averagePositive(completedDurations);
        if (avg > 0.0) {
            return avg;
        }
        avg = averagePositive(cachedTimings != null ? cachedTimings.values() : null);
        if (avg > 0.0) {
            return avg;
        }
        // True cold start (no completions, no cache at all): a placeholder so ETA is non-zero and
        // visibly grows until the first test finishes, capped so a stalled start can't balloon it.
        return elapsedTimeMs > 0L ? Math.min(elapsedTimeMs, COLD_START_PLACEHOLDER_CAP_MS) : 30_000L;
    }

    static Path cacheFile(String persistenceProvider) {
        String normalized = (persistenceProvider == null || persistenceProvider.isEmpty())
                ? "unknown"
                : persistenceProvider.toLowerCase(Locale.ROOT);
        return Paths.get(System.getProperty("user.dir", "."), ".test-timing-cache-" + normalized + ".properties");
    }
}
