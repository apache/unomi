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

package org.apache.unomi.api.goals;

import java.io.Serializable;
import java.util.List;

/**
 * Performance statistics for a {@link org.apache.unomi.api.goals.Goal}.
 * Contains counts and rates (starts, conversions, etc.) used to measure
 * whether visitors complete the tracked goal.
 */
public class GoalReport implements Serializable {
    private static final long serialVersionUID = -9150361970326342064L;
    private Stat globalStats;
    private List<Stat> split;

    /**
     * Constructs a new GoalReport instance.
     */
    public GoalReport() {
    }

    /**
     * Retrieves the global statistics associated with this report.
     * @return The {@link Stat} containing global statistics.
     */
    public Stat getGlobalStats() {
        return globalStats;
    }

    /**
     * Sets the global statistics for this report.
     * @param globalStats The {@link Stat} object representing
     * the global statistics.
     */
    public void setGlobalStats(Stat globalStats) {
        this.globalStats = globalStats;
    }

    /**
     * Retrieves the list of split statistics associated with this report.
     * @return A {@link List} of {@link Stat} objects representing the splits.
     */
    public List<Stat> getSplit() {
        return split;
    }

    /**
     * Sets the list of split statistics for this report.
     * @param split The {@link List} of {@link Stat} objects
     * defining the splits.
     */
    public void setSplit(List<Stat> split) {
        this.split = split;
    }

    /**
     * Statistics
     */
    public static class Stat implements Serializable {
        private static final long serialVersionUID = 4306277648074263098L;
        private String key;
        private long startCount;
        private long targetCount;
        private double conversionRate;
        private double percentage;

        /**
         * Constructs a new {@code Stat} instance.
         */
        public Stat() {
        }

        /**
         * Retrieves the key associated with this statistic.
         * @return The unique identifier (key) for the stat.
         */
        public String getKey() {
            return key;
        }

        /**
         * Sets the key used to identify this statistic.
         * @param key The unique string key.
         */
        public void setKey(String key) {
            this.key = key;
        }

        /**
         * Retrieves the starting count of records analyzed.
         * @return The total number of records at the start count.
         */
        public long getStartCount() {
            return startCount;
        }

        /**
         * Sets the initial count for this statistic.
         * @param startCount The total number of records.
         */
        public void setStartCount(long startCount) {
            this.startCount = startCount;
        }

        /**
         * Retrieves the target count of successful records.
         * @return The number of records that met the target criteria.
         */
        public long getTargetCount() {
            return targetCount;
        }

        /**
         * Sets the target count for this statistic.
         * @param targetCount The desired or measured target count.
         */
        public void setTargetCount(long targetCount) {
            this.targetCount = targetCount;
        }

        /**
         * Retrieves the calculated conversion rate (Target
         * Count / Start Count).
         * @return The conversion rate as a double value.
         */
        public double getConversionRate() {
            return conversionRate;
        }

        /**
         * Sets the conversion rate statistic.
         * This value is typically calculated as a ratio of successful
         * targets to starting counts.
         * @param conversionRate the calculated conversion rate (target
         * count / start count).
         */
        public void setConversionRate(double conversionRate) {
            this.conversionRate = conversionRate;
        }

        /**
         * Retrieves the percentage value stored in this statistic.
         * This value represents a proportion, often relative to another total.
         * @return the percentage value.
         */
        public double getPercentage() {
            return percentage;
        }

        /**
         * Sets the calculated percentage for this goal report statistic.
         * This value is typically derived by dividing target counts by
         * a larger total count.
         * @param percentage the calculated percentage value.
         */
        public void setPercentage(double percentage) {
            this.percentage = percentage;
        }
    }

}