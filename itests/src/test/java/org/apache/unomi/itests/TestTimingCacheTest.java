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
    public void computeScaleDefaultsToOneWithoutPairs() {
        Assert.assertEquals(1.0, TestTimingCache.computeScale(Collections.emptyList()), 0.0);
        Assert.assertEquals(1.0, TestTimingCache.computeScale(null), 0.0);
    }

    @Test
    public void computeScaleUsesObservedOverCachedRatioAndClamps() {
        List<long[]> slower = Collections.singletonList(new long[]{2_000L, 1_000L});
        Assert.assertEquals(2.0, TestTimingCache.computeScale(slower), 0.0);

        List<long[]> tooFast = Collections.singletonList(new long[]{10L, 10_000L});
        Assert.assertEquals(TestTimingCache.MIN_SCALE, TestTimingCache.computeScale(tooFast), 0.0);

        List<long[]> tooSlow = Collections.singletonList(new long[]{50_000L, 1_000L});
        Assert.assertEquals(TestTimingCache.MAX_SCALE, TestTimingCache.computeScale(tooSlow), 0.0);
    }

    @Test
    public void estimateRemainingUsesScaledHistoryAndFallbackAverage() {
        Map<String, Long> cached = new HashMap<>();
        cached.put("A#a", 1_000L);
        cached.put("B#b", 2_000L);

        Set<String> remaining = new HashSet<>(Arrays.asList("A#a", "C#c"));
        List<long[]> observedVsCached = Collections.singletonList(new long[]{1_500L, 1_000L});
        List<Long> completed = Collections.singletonList(1_500L);

        // scale = 1.5 → A contributes 1500; C uncached → fallback avg 1500
        long eta = TestTimingCache.estimateRemainingMs(remaining, cached, observedVsCached, completed, 0L);
        Assert.assertEquals(3_000L, eta);
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
}
