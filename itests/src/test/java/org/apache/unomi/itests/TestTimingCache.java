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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Local, best-effort cache of individual IT execution times, used by {@link ProgressListener} to make its
 * estimated-time-remaining calculation more accurate than a single flat running average.
 * <p>
 * The cache is a plain properties file kept in the {@code itests} module directory (not {@code target/}) so
 * it survives {@code mvn clean}, with one file per search engine since Elasticsearch and OpenSearch runs have
 * different timing profiles and shouldn't be averaged together.
 * <p>
 * This is a local developer convenience, not build state: all I/O failures are swallowed so a missing or
 * unwritable cache (e.g. a read-only or ephemeral CI workspace) never fails the IT run - it just falls back
 * to the flat average for every test, matching the prior behavior.
 */
final class TestTimingCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestTimingCache.class);

    /** Weight given to a freshly observed duration when blending it into the persisted average. */
    private static final double SMOOTHING = 0.3;

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
     * Loads the previously persisted timings for the given search engine.
     *
     * @param searchEngine the search engine the current run targets (e.g. "elasticsearch", "opensearch")
     * @return a mutable map of cache key to last known duration in milliseconds; empty if no cache
     *         exists yet or it could not be read
     */
    static Map<String, Long> load(String searchEngine) {
        Map<String, Long> timings = new HashMap<>();
        Path cacheFile = cacheFile(searchEngine);
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
     * Merges freshly observed durations into the persisted cache for the given search engine, smoothing
     * each updated entry with an exponential moving average so a single unusually slow/fast run doesn't
     * swing future ETAs too far.
     *
     * @param searchEngine the search engine the run just executed against
     * @param observedTimings durations (in milliseconds) observed during the run that just finished
     */
    static void save(String searchEngine, Map<String, Long> observedTimings) {
        if (observedTimings.isEmpty()) {
            return;
        }
        Path cacheFile = cacheFile(searchEngine);
        try {
            Map<String, Long> merged = load(searchEngine);
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
                props.store(writer, "Apache Unomi IT test timing cache (local dev aid, safe to delete)");
            }
            Files.move(tempFile, cacheFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            LOGGER.debug("Unable to persist test timing cache at {} (ETAs will just use the in-run average next time): {}",
                    cacheFile, e.getMessage());
        }
    }

    private static Path cacheFile(String searchEngine) {
        String normalizedEngine = (searchEngine == null || searchEngine.isEmpty())
                ? "unknown"
                : searchEngine.toLowerCase(Locale.ROOT);
        return Paths.get(System.getProperty("user.dir", "."), ".test-timing-cache-" + normalizedEngine + ".properties");
    }
}
