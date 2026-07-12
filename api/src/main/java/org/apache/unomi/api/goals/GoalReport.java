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
 * Goal conversion report returned by {@link org.apache.unomi.api.services.GoalsService}.
 * {@link #globalStats} aggregates all traffic; {@link #split} breaks the same
 * metrics down per experiment or variant key so marketers can compare branches
 * of a goal or campaign.
 */
public class GoalReport implements Serializable {
    private static final long serialVersionUID = -9150361970326342064L;
    private Stat globalStats;
    private List<Stat> split;

    /**
     * Creates an empty goal report.
     */
    public GoalReport() {
    }

    /**
     * Aggregated statistics across all traffic.
     *
     * @return global statistics
     */
    public Stat getGlobalStats() {
        return globalStats;
    }

    /**
     * Sets the global statistics.
     *
     * @param globalStats global statistics
     */
    public void setGlobalStats(Stat globalStats) {
        this.globalStats = globalStats;
    }

    /**
     * Per-split statistics (for example A/B variants).
     *
     * @return split statistics
     */
    public List<Stat> getSplit() {
        return split;
    }

    /**
     * Sets the per-split statistics.
     *
     * @param split split statistics
     */
    public void setSplit(List<Stat> split) {
        this.split = split;
    }

    /**
     * Counts and rates for one goal report bucket.
     */
    public static class Stat implements Serializable {
        private static final long serialVersionUID = 4306277648074263098L;
        private String key;
        private long startCount;
        private long targetCount;
        private double conversionRate;
        private double percentage;

        /**
         * Creates an empty stat bucket.
         */
        public Stat() {
        }

        /**
         * Bucket key (for example a split name).
         *
         * @return stat key
         */
        public String getKey() {
            return key;
        }

        /**
         * Sets the bucket key.
         *
         * @param key stat key
         */
        public void setKey(String key) {
            this.key = key;
        }

        /**
         * Number of goal starts.
         *
         * @return start count
         */
        public long getStartCount() {
            return startCount;
        }

        /**
         * Sets the start count.
         *
         * @param startCount start count
         */
        public void setStartCount(long startCount) {
            this.startCount = startCount;
        }

        /**
         * Number of goal completions.
         *
         * @return target count
         */
        public long getTargetCount() {
            return targetCount;
        }

        /**
         * Sets the target count.
         *
         * @param targetCount target count
         */
        public void setTargetCount(long targetCount) {
            this.targetCount = targetCount;
        }

        /**
         * Conversion rate ({@code targetCount / startCount}).
         *
         * @return conversion rate
         */
        public double getConversionRate() {
            return conversionRate;
        }

        /**
         * Sets the conversion rate.
         *
         * @param conversionRate conversion rate
         */
        public void setConversionRate(double conversionRate) {
            this.conversionRate = conversionRate;
        }

        /**
         * Share of the total as a percentage.
         *
         * @return percentage value
         */
        public double getPercentage() {
            return percentage;
        }

        /**
         * Sets the percentage value.
         *
         * @param percentage percentage value
         */
        public void setPercentage(double percentage) {
            this.percentage = percentage;
        }
    }

}
