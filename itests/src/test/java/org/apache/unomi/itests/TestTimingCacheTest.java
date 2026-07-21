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

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Unit tests for {@link TestTimingCache} ETA helpers and per-provider isolation.
 */
public class TestTimingCacheTest {

    private String previousUserDir;
    private Path tempDir;

    @Before
    public void setUp() throws Exception {
        previousUserDir = System.getProperty("user.dir");
        tempDir = Files.createTempDirectory("unomi-test-timing-cache");
        System.setProperty("user.dir", tempDir.toAbsolutePath().toString());
    }

    @After
    public void tearDown() {
        if (previousUserDir != null) {
            System.setProperty("user.dir", previousUserDir);
        }
    }

    @Test
    public void cacheFilesAreIsolatedPerProvider() {
        Path es = TestTimingCache.cacheFile("elasticsearch");
        Path pg = TestTimingCache.cacheFile("postgresql");
        Assert.assertNotEquals(es, pg);
        Assert.assertTrue(es.getFileName().toString().contains("elasticsearch"));
        Assert.assertTrue(pg.getFileName().toString().contains("postgresql"));
    }

    @Test
    public void saveAndLoadRoundTripPerProvider() {
        Map<String, Long> esTimings = Collections.singletonMap("FooIT#bar", 1_000L);
        Map<String, Long> pgTimings = Collections.singletonMap("FooIT#bar", 5_000L);

        TestTimingCache.save("elasticsearch", esTimings);
        TestTimingCache.save("postgresql", pgTimings);

        Assert.assertEquals(Long.valueOf(1_000L), TestTimingCache.load("elasticsearch").get("FooIT#bar"));
        Assert.assertEquals(Long.valueOf(5_000L), TestTimingCache.load("postgresql").get("FooIT#bar"));
        Assert.assertTrue(TestTimingCache.load("opensearch").isEmpty());
    }

    @Test
    public void saveIgnoresSkipLikeDurationForNewKey() {
        // A brand new key whose only observation so far is a near-instant assume/skip must not create
        // a cache entry at all — persisting it would seed the history with a non-representative value.
        TestTimingCache.save("elasticsearch", Collections.singletonMap("SkipIT#skip", 50L));
        Assert.assertNull(TestTimingCache.load("elasticsearch").get("SkipIT#skip"));
    }

    @Test
    public void saveDoesNotErodeHistoryWithLaterSkipLikeDuration() {
        // A test that's usually substantial (3000ms) but occasionally short-circuits via an assume/skip
        // (50ms) must keep its real historical average — the skip observation must not be blended in.
        TestTimingCache.save("elasticsearch", Collections.singletonMap("FlakySkipIT#test", 3_000L));
        Assert.assertEquals(Long.valueOf(3_000L), TestTimingCache.load("elasticsearch").get("FlakySkipIT#test"));

        TestTimingCache.save("elasticsearch", Collections.singletonMap("FlakySkipIT#test", 50L));
        Assert.assertEquals(Long.valueOf(3_000L), TestTimingCache.load("elasticsearch").get("FlakySkipIT#test"));
    }

    @Test
    public void saveStillSmoothsSubstantiveDurations() {
        // Sanity check that the skip-filter didn't disable smoothing for genuine observations.
        TestTimingCache.save("elasticsearch", Collections.singletonMap("SmoothedIT#test", 1_000L));
        TestTimingCache.save("elasticsearch", Collections.singletonMap("SmoothedIT#test", 2_000L));

        // updated = 1000*(1-0.3) + 2000*0.3 = 1300
        Assert.assertEquals(Long.valueOf(1_300L), TestTimingCache.load("elasticsearch").get("SmoothedIT#test"));
    }

    @Test
    public void saveOnlyPersistsSubstantiveEntriesFromMixedBatch() {
        Map<String, Long> mixed = new HashMap<>();
        mixed.put("HeavyIT#real", 5_000L);
        mixed.put("SkipIT#skip", 10L);

        TestTimingCache.save("elasticsearch", mixed);

        Map<String, Long> loaded = TestTimingCache.load("elasticsearch");
        Assert.assertEquals(Long.valueOf(5_000L), loaded.get("HeavyIT#real"));
        Assert.assertNull(loaded.get("SkipIT#skip"));
    }

    @Test
    public void computeScaleDefaultsToOneWithoutPairs() {
        Assert.assertEquals(1.0, TestTimingCache.computeScale(Collections.emptyList()), 0.0);
        Assert.assertEquals(1.0, TestTimingCache.computeScale(null), 0.0);
    }

    @Test
    public void computeScaleUsesObservedOverCachedRatioAndClamps() {
        List<TestTimingCache.TimingSample> slower =
                Collections.singletonList(new TestTimingCache.TimingSample(2_000L, 1_000L));
        Assert.assertEquals(2.0, TestTimingCache.computeScale(slower), 0.0);

        // Substantive but very fast vs cache → clamp to MIN_SCALE (not ignored as assume-like)
        List<TestTimingCache.TimingSample> tooFast =
                Collections.singletonList(new TestTimingCache.TimingSample(300L, 2_000L));
        Assert.assertEquals(TestTimingCache.MIN_SCALE, TestTimingCache.computeScale(tooFast), 0.0);

        // Substantive slowdown within skew guard → clamp to MAX_SCALE
        List<TestTimingCache.TimingSample> tooSlow =
                Collections.singletonList(new TestTimingCache.TimingSample(15_000L, 1_000L));
        Assert.assertEquals(TestTimingCache.MAX_SCALE, TestTimingCache.computeScale(tooSlow), 0.0);
    }

    @Test
    public void computeScaleIgnoresAssumeLikePairs() {
        // 10ms observed vs 10s cached looks like a skip — must not drag scale to MIN_SCALE.
        List<TestTimingCache.TimingSample> skipPlusNormal = Arrays.asList(
                new TestTimingCache.TimingSample(10L, 10_000L),
                new TestTimingCache.TimingSample(2_000L, 2_000L));
        Assert.assertEquals(1.0, TestTimingCache.computeScale(skipPlusNormal), 0.0);
    }

    @Test
    public void computeScaleDoesNotIgnoreGenuineRegressions() {
        // Historically fast (50ms) test now takes 25x longer (1_250ms): a real regression, not a
        // skip/assume artifact, so it must still count towards raising the scale (clamped to MAX_SCALE
        // since 25x exceeds it) rather than being filtered out by the skew guard.
        List<TestTimingCache.TimingSample> regressed =
                Collections.singletonList(new TestTimingCache.TimingSample(1_250L, 50L));
        Assert.assertEquals(TestTimingCache.MAX_SCALE, TestTimingCache.computeScale(regressed), 0.0);
    }

    @Test
    public void estimateRemainingUsesLivePaceWithHistoricalHints() {
        Map<String, Long> cached = new HashMap<>();
        // Suite average historical = (1000+3000+2000)/3 = 2000
        cached.put("A#a", 1_000L);
        cached.put("B#b", 3_000L);
        cached.put("C#c", 2_000L);

        // Completed A in 500ms wall; remaining B (heavier) and C (average).
        Set<String> remaining = new HashSet<>(Arrays.asList("B#b", "C#c"));
        List<TestTimingCache.TimingSample> observedVsCached =
                Collections.singletonList(new TestTimingCache.TimingSample(500L, 1_000L));
        List<Long> completed = Collections.singletonList(500L);
        long elapsed = 500L;

        long eta = TestTimingCache.estimateRemainingMs(remaining, cached, observedVsCached, completed, elapsed);

        // rateEta = 2 * 500 = 1000
        // hintShaped = (3000+2000) * (500/2000) = 1250 → heavier remaining raises ETA
        Assert.assertEquals(1_250L, eta);
    }

    @Test
    public void estimateRemainingTracksWallClockPaceNotMinScaleCollapse() {
        Map<String, Long> cached = new HashMap<>();
        for (int i = 0; i < 50; i++) {
            cached.put("DoneIT#t" + i, 5_000L);
        }
        for (int i = 0; i < 250; i++) {
            cached.put("TodoIT#t" + i, 5_000L);
        }

        Set<String> remaining = new HashSet<>();
        for (int i = 0; i < 250; i++) {
            remaining.add("TodoIT#t" + i);
        }

        List<TestTimingCache.TimingSample> observedVsCached = new java.util.ArrayList<>();
        List<Long> completed = new java.util.ArrayList<>();
        // 50 tests in 200s wall (~4s each) while cache said 5s — realistic mild speedup
        for (int i = 0; i < 50; i++) {
            observedVsCached.add(new TestTimingCache.TimingSample(4_000L, 5_000L));
            completed.add(4_000L);
        }
        long elapsed = 200_000L;

        long eta = TestTimingCache.estimateRemainingMs(remaining, cached, observedVsCached, completed, elapsed);
        // rate / hint-shaped ≈ 250 * 4000 = 1_000_000ms (~16.7m)
        Assert.assertEquals(1_000_000L, eta);

        // Old bug: MIN_SCALE * 250 * 5000 = 312_500 (~5.2m) — chronically too low
        long oldBuggyEta = Math.round(250 * 5_000L * TestTimingCache.MIN_SCALE);
        Assert.assertTrue(eta > oldBuggyEta);
    }

    @Test
    public void estimateRemainingUnaffectedByFailedTestWallTime() {
        // ProgressListener never adds a failed/aborted test's duration to completedDurations (see
        // testFinished), but the suite-wide elapsed clock keeps advancing regardless of outcome.
        // The ETA must be driven by the one substantive success, not by elapsed/completedCount
        // (which would have been 50_200 / 1 = 50_200ms/test — a ~250x inflation).
        Map<String, Long> cached = new HashMap<>();
        cached.put("FlakyIT#a", 200L);
        cached.put("FlakyIT#b", 200L);

        Set<String> remaining = new HashSet<>(Collections.singletonList("FlakyIT#b"));
        List<Long> completed = Collections.singletonList(200L);
        long elapsedIncludingFailures = 50_200L; // 10 failed tests @ 5s each + the 200ms success

        long eta = TestTimingCache.estimateRemainingMs(
                remaining, cached, Collections.emptyList(), completed, elapsedIncludingFailures);

        Assert.assertEquals(200L, eta);
    }

    @Test
    public void estimateRemainingIgnoresAssumeDilutionInLivePace() {
        // 50 assume-like tests complete in ~50ms each (below SUBSTANTIVE_OBSERVED_MS) before any of
        // the 250 historically-heavy tests have run. The old elapsed/completedCount pace would have
        // been dragged down to ~50ms/test, collapsing the ETA for the heavy tests still ahead.
        Map<String, Long> cached = new HashMap<>();
        for (int i = 0; i < 50; i++) {
            cached.put("SkipIT#t" + i, 50L);
        }
        for (int i = 0; i < 250; i++) {
            cached.put("HeavyIT#t" + i, 5_000L);
        }

        Set<String> remaining = new HashSet<>();
        for (int i = 0; i < 250; i++) {
            remaining.add("HeavyIT#t" + i);
        }

        List<Long> completed = new java.util.ArrayList<>();
        for (int i = 0; i < 50; i++) {
            completed.add(50L);
        }
        long elapsed = 50L * 50L;

        long eta = TestTimingCache.estimateRemainingMs(
                remaining, cached, Collections.emptyList(), completed, elapsed);

        // No substantive completions yet → live pace falls back to the historical average, so the
        // heavy remaining tests are estimated at their full cached weight (250 * 5_000 = 1_250_000),
        // not diluted down toward the ~50ms/test pace of the skips seen so far.
        Assert.assertEquals(1_250_000L, eta);
    }

    @Test
    public void estimateRemainingAppliesSlowdownBoostWhenSubstantiveScaleExceedsOne() {
        // A completed on the same key family as remaining R, but 4x slower than its own cache entry —
        // a genuine per-test regression that should raise the ETA above the plain hint-shaped estimate.
        Map<String, Long> cached = new HashMap<>();
        cached.put("A#a", 50L);
        cached.put("R#r", 1_000L);

        Set<String> remaining = new HashSet<>(Collections.singletonList("R#r"));
        List<TestTimingCache.TimingSample> observedVsCached =
                Collections.singletonList(new TestTimingCache.TimingSample(200L, 50L));
        List<Long> completed = Collections.singletonList(200L);
        long elapsed = 200L;

        long eta = TestTimingCache.estimateRemainingMs(remaining, cached, observedVsCached, completed, elapsed);

        // Un-boosted hint-shaped estimate: 1000 * (200/525) ≈ 381
        // robustScale = 200/50 = 4.0 (MAX_SCALE); shrink = 1/(1+15); softenedScale = 1 + shrink*3 = 1.1875
        // boosted estimate: 1000 * 1.1875 = 1187.5 → 1188, which wins over the un-boosted 381
        Assert.assertEquals(1_188L, eta);
    }

    @Test
    public void estimateRemainingUsesHistoricalAverageWhenNothingCompleted() {
        Map<String, Long> cached = new HashMap<>();
        cached.put("A#a", 1_000L);
        cached.put("B#b", 3_000L);

        Set<String> remaining = Collections.singleton("C#c");
        long eta = TestTimingCache.estimateRemainingMs(
                remaining, cached, Collections.emptyList(), Collections.emptyList(), 0L);
        // avg of history = 2000
        Assert.assertEquals(2_000L, eta);
    }

    @Test
    public void estimateRemainingReturnsZeroForEmptyOrNullRemainingKeys() {
        Map<String, Long> cached = Collections.singletonMap("A#a", 1_000L);
        List<Long> completed = Collections.singletonList(500L);

        Assert.assertEquals(0L, TestTimingCache.estimateRemainingMs(
                Collections.emptySet(), cached, Collections.emptyList(), completed, 500L));
        Assert.assertEquals(0L, TestTimingCache.estimateRemainingMs(
                null, cached, Collections.emptyList(), completed, 500L));
    }

    @Test
    public void estimateRemainingHandlesNullCachedTimingsGracefully() {
        // No historical cache at all (e.g. first-ever run, or an unreadable cache file) — must not NPE
        // and should fall back entirely to the in-run average.
        Set<String> remaining = new HashSet<>(Collections.singletonList("X#x"));
        List<Long> completed = Collections.singletonList(500L);

        long eta = TestTimingCache.estimateRemainingMs(
                remaining, null, Collections.emptyList(), completed, 500L);

        // fallbackAvg = avg(completed) = 500; no cache to weigh against, so live pace and hint-shaped
        // estimate both resolve to the plain average.
        Assert.assertEquals(500L, eta);
    }

    @Test
    public void estimateRemainingUsesFallbackAverageForUncachedRemainingKey() {
        // "Cached#x" has a direct historical entry; "Uncached#y" does not and must fall back to the
        // average of the historical cache (not 0, and not just re-using Cached#x's own value).
        Map<String, Long> cached = new HashMap<>();
        cached.put("Cached#x", 1_000L);
        cached.put("Other#unrelated", 3_000L);

        Set<String> remaining = new HashSet<>(Arrays.asList("Cached#x", "Uncached#y"));
        long eta = TestTimingCache.estimateRemainingMs(
                remaining, cached, Collections.emptyList(), Collections.emptyList(), 0L);

        // Cold start (nothing completed): hintRemainingMs = cached("Cached#x")=1000
        //                                                  + fallbackAvg(avg of cache = 2000) = 3000
        Assert.assertEquals(3_000L, eta);
    }

    @Test
    public void estimateRemainingTreatsNonPositiveElapsedAsColdStart() {
        // A negative/zero elapsed reading (e.g. clock oddity) must be treated like cold start rather
        // than feeding a nonsensical value into the live-pace division.
        Map<String, Long> cached = Collections.singletonMap("X#x", 1_000L);
        Set<String> remaining = new HashSet<>(Collections.singletonList("X#x"));
        List<Long> completed = Collections.singletonList(500L);

        long eta = TestTimingCache.estimateRemainingMs(remaining, cached, Collections.emptyList(), completed, -100L);
        Assert.assertEquals(1_000L, eta);
    }

    @Test
    public void estimateRemainingIgnoresSkipDurationWhenAveragingSubstantiveCompletions() {
        // A mix of one assume-like completion (50ms) and one substantive completion (3000ms) must
        // average pace from the substantive one only (3000), not the diluted blended average (1525).
        Map<String, Long> cached = Collections.singletonMap("R#r", 1_000L);
        Set<String> remaining = new HashSet<>(Collections.singletonList("R#r"));
        List<Long> completed = Arrays.asList(50L, 3_000L);

        long eta = TestTimingCache.estimateRemainingMs(
                remaining, cached, Collections.emptyList(), completed, 3_050L);

        Assert.assertEquals(3_000L, eta);
    }

    @Test
    public void isSubstantivePairBoundaryConditions() {
        Assert.assertFalse(TestTimingCache.isSubstantivePair(null));

        // Observed below SUBSTANTIVE_OBSERVED_MS → excluded regardless of cached.
        Assert.assertFalse(TestTimingCache.isSubstantivePair(new TestTimingCache.TimingSample(99L, 1_000L)));

        // Observed exactly at the threshold → substantive (strict "<" check, not "<=").
        Assert.assertTrue(TestTimingCache.isSubstantivePair(new TestTimingCache.TimingSample(100L, 100L)));

        // Non-positive cached → excluded regardless of observed.
        Assert.assertFalse(TestTimingCache.isSubstantivePair(new TestTimingCache.TimingSample(1_000L, 0L)));

        // Skew exactly at MAX_PAIR_SKEW → still substantive ("<=" boundary is inclusive).
        Assert.assertTrue(TestTimingCache.isSubstantivePair(new TestTimingCache.TimingSample(100L, 2_000L)));

        // Skew just past MAX_PAIR_SKEW → excluded as assume/skip-like.
        Assert.assertFalse(TestTimingCache.isSubstantivePair(new TestTimingCache.TimingSample(100L, 2_001L)));

        // observed >> cached (a genuine regression, the opposite direction) has no upper bound and is
        // never excluded — this is the one-directional behavior the skew guard is meant to have.
        Assert.assertTrue(TestTimingCache.isSubstantivePair(new TestTimingCache.TimingSample(1_000_000L, 1L)));
    }

    @Test
    public void computeScaleToleratesNullSampleInCollection() {
        List<TestTimingCache.TimingSample> withNull = Arrays.asList(
                null,
                new TestTimingCache.TimingSample(1_000L, 1_000L));
        Assert.assertEquals(1.0, TestTimingCache.computeScale(withNull), 0.0);
    }
}
