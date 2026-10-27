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

package org.apache.unomi.persistence.spi.aggregate;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Aggregation that buckets documents by time intervals on a date/datetime field.
 * Supports legacy short interval formats (e.g., {@code 1M}) and new named formats (e.g., {@code Month}); no other
 * interval is accepted. Without an interval, buckets are one month ({@code 1M}).
 */
public class DateAggregate extends BaseAggregate {
    private static final String DEFAULT_INTERVAL = "1M";

    private String interval;
    private String format;

    // Bidirectional maps for conversion between interval formats, in increasing order
    private static final Map<String, String> OLD_TO_NEW_FORMAT = createFormatMap();

    private static final Map<String, String> NEW_TO_OLD_FORMAT = createReverseMap();

    private static final String SUPPORTED_INTERVALS =
            String.join(", ", OLD_TO_NEW_FORMAT.keySet()) + ", " + String.join(", ", OLD_TO_NEW_FORMAT.values());

    private static Map<String, String> createFormatMap() {
        Map<String, String> formats = new LinkedHashMap<>();
        formats.put("1s", "Second");
        formats.put("1m", "Minute");
        formats.put("1h", "Hour");
        formats.put("1d", "Day");
        formats.put("1w", "Week");
        formats.put("1M", "Month");
        formats.put("1q", "Quarter");
        formats.put("1y", "Year");
        return Collections.unmodifiableMap(formats);
    }

    private static Map<String, String> createReverseMap() {
        Map<String, String> reverseMap = new HashMap<>();
        for (Map.Entry<String, String> entry : DateAggregate.OLD_TO_NEW_FORMAT.entrySet()) {
            reverseMap.put(entry.getValue(), entry.getKey());
        }
        return Collections.unmodifiableMap(reverseMap);
    }

    /**
     * Creates a date aggregation with the default interval.
     *
     * @param field the field to aggregate on
     */
    public DateAggregate(String field) {
        super(field);
        this.interval = DEFAULT_INTERVAL;
    }

    /**
     * Creates a date aggregation with a specific interval (old or new format).
     *
     * @param field    the field to aggregate on
     * @param interval the interval, in old (e.g., {@code 1M}) or new (e.g., {@code Month}) format
     * @throws IllegalArgumentException if the interval is not supported (see {@link #setInterval(String)})
     */
    public DateAggregate(String field, String interval) {
        super(field);
        setInterval(interval);
    }

    /**
     * Creates a date aggregation with a specific interval and output format.
     *
     * @param field    the field to aggregate on
     * @param interval the interval, in old or new format
     * @param format   an optional output format understood by the persistence layer
     * @throws IllegalArgumentException if the interval is not supported (see {@link #setInterval(String)})
     */
    public DateAggregate(String field, String interval, String format) {
        super(field);
        setInterval(interval);
        this.format = format;
    }

    /**
     * Sets the interval. A null or empty value selects the default ({@code 1M}).
     *
     * @param interval the interval, in old (e.g. {@code 1d}) or new (e.g. {@code Day}) format
     * @throws IllegalArgumentException if the interval is in neither format, since the search engines cannot run it
     */
    public void setInterval(String interval) {
        if (interval == null || interval.isEmpty()) {
            this.interval = DEFAULT_INTERVAL;
            return;
        }
        if (!isOldFormat(interval) && !isNewFormat(interval)) {
            throw new IllegalArgumentException(
                    "Unsupported date aggregate interval '" + interval + "', use one of: " + SUPPORTED_INTERVALS);
        }
        this.interval = interval;
    }

    /**
     * Returns the interval as it was set: always a supported value, in old or new format.
     *
     * @return the interval string
     */
    public String getInterval() {
        return interval;
    }

    /**
     * Returns the interval in the old format (1M, 1d, etc.).
     *
     * @return the interval in old format
     */
    public String getIntervalInOldFormat() {
        if (isOldFormat(interval)) {
            return interval;
        }
        return NEW_TO_OLD_FORMAT.getOrDefault(interval, interval);
    }

    /**
     * Returns the interval in the new format (Month, Day, etc.).
     *
     * @return the interval in new format
     */
    public String getIntervalInNewFormat() {
        if (isNewFormat(interval)) {
            return interval;
        }
        return OLD_TO_NEW_FORMAT.getOrDefault(interval, interval);
    }

    /**
     * Compatibility method with old code
     * @deprecated Use getIntervalInNewFormat() instead
     */
    @Deprecated
    public String getIntervalByAlias(String alias) {
        if (isOldFormat(alias)) {
            return OLD_TO_NEW_FORMAT.getOrDefault(alias, alias);
        }
        return alias;
    }

    /**
     * Determines if the interval uses the old format.
     *
     * @param value the interval string to check
     * @return {@code true} if the value uses old format
     */
    public boolean isOldFormat(String value) {
        return OLD_TO_NEW_FORMAT.containsKey(value);
    }

    /**
     * Determines if the interval uses the new format.
     *
     * @param value the interval string to check
     * @return {@code true} if the value uses new format
     */
    public boolean isNewFormat(String value) {
        return NEW_TO_OLD_FORMAT.containsKey(value);
    }

    /**
     * Converts from old format to new format.
     *
     * @param oldFormat the interval in old format
     * @return the interval in new format
     */
    public static String convertToNewFormat(String oldFormat) {
        return OLD_TO_NEW_FORMAT.getOrDefault(oldFormat, oldFormat);
    }

    /**
     * Converts from new format to old format.
     *
     * @param newFormat the interval in new format
     * @return the interval in old format
     */
    public static String convertToOldFormat(String newFormat) {
        return NEW_TO_OLD_FORMAT.getOrDefault(newFormat, newFormat);
    }

    /**
     * Returns the output format, if any.
     *
     * @return the output format, or {@code null}
     */
    public String getFormat() {
        return format;
    }

    /**
     * Sets the output format.
     *
     * @param format the output format
     */
    public void setFormat(String format) {
        this.format = format;
    }
}