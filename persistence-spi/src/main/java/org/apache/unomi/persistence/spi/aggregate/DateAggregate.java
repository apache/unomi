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
import java.util.Map;

public class DateAggregate extends BaseAggregate {
    private static final String DEFAULT_INTERVAL = "1M";

    private String interval;
    private String format;

    // Maps bidirectionnelles pour la conversion entre formats
    private static final Map<String, String> OLD_TO_NEW_FORMAT = Map.ofEntries(
            Map.entry("1s", "Second"),
            Map.entry("1m", "Minute"),
            Map.entry("1h", "Hour"),
            Map.entry("1d", "Day"),
            Map.entry("1w", "Week"),
            Map.entry("1M", "Month"),
            Map.entry("1q", "Quarter"),
            Map.entry("1y", "Year")
    );

    private static final Map<String, String> NEW_TO_OLD_FORMAT = createReverseMap();

    private static Map<String, String> createReverseMap() {
        Map<String, String> reverseMap = new HashMap<>();
        for (Map.Entry<String, String> entry : DateAggregate.OLD_TO_NEW_FORMAT.entrySet()) {
            reverseMap.put(entry.getValue(), entry.getKey());
        }
        return Collections.unmodifiableMap(reverseMap);
    }

    public DateAggregate(String field) {
        super(field);
        this.interval = DEFAULT_INTERVAL;
    }

    public DateAggregate(String field, String interval) {
        super(field);
        setInterval(interval);
    }

    public DateAggregate(String field, String interval, String format) {
        super(field);
        setInterval(interval);
        this.format = format;
    }

    public void setInterval(String interval) {
        this.interval = (interval != null && !interval.isEmpty()) ? interval : DEFAULT_INTERVAL;
    }

    /**
     * Returns the interval as it was originally defined
     */
    public String getInterval() {
        return interval;
    }

    /**
     * Returns the interval in the old format (1M, 1d, etc.)
     */
    public String getIntervalInOldFormat() {
        if (isOldFormat(interval)) {
            return interval;
        }
        return NEW_TO_OLD_FORMAT.getOrDefault(interval, interval);
    }

    /**
     * Returns the interval in the new format (Month, Day, etc.)
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
     * Determines if the interval uses the old format
     */
    public boolean isOldFormat(String value) {
        return OLD_TO_NEW_FORMAT.containsKey(value);
    }

    /**
     * Determines if the interval uses the new format
     */
    public boolean isNewFormat(String value) {
        return NEW_TO_OLD_FORMAT.containsKey(value);
    }

    /**
     * Converts from old format to new format
     */
    public static String convertToNewFormat(String oldFormat) {
        return OLD_TO_NEW_FORMAT.getOrDefault(oldFormat, oldFormat);
    }

    /**
     * Converts from new format to old format
     */
    public static String convertToOldFormat(String newFormat) {
        return NEW_TO_OLD_FORMAT.getOrDefault(newFormat, newFormat);
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }
}