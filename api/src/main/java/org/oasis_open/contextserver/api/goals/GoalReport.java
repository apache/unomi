package org.oasis_open.contextserver.api.goals;

/*
 * #%L
 * context-server-api
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2014 - 2015 Jahia Solutions
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.io.Serializable;
import java.util.List;

public class GoalReport implements Serializable {
    private static final long serialVersionUID = -9150361970326342064L;
    private Stat globalStats;
    private List<Stat> split;

    public GoalReport() {
    }

    public Stat getGlobalStats() {
        return globalStats;
    }

    public void setGlobalStats(Stat globalStats) {
        this.globalStats = globalStats;
    }

    public List<Stat> getSplit() {
        return split;
    }

    public void setSplit(List<Stat> split) {
        this.split = split;
    }

    public static class Stat implements Serializable {
        private static final long serialVersionUID = 4306277648074263098L;
        private String key;
        private long startCount;
        private long targetCount;
        private double conversionRate;
        private double percentage;

        public Stat() {
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public long getStartCount() {
            return startCount;
        }

        public void setStartCount(long startCount) {
            this.startCount = startCount;
        }

        public long getTargetCount() {
            return targetCount;
        }

        public void setTargetCount(long targetCount) {
            this.targetCount = targetCount;
        }

        public double getConversionRate() {
            return conversionRate;
        }

        public void setConversionRate(double conversionRate) {
            this.conversionRate = conversionRate;
        }

        public double getPercentage() {
            return percentage;
        }

        public void setPercentage(double percentage) {
            this.percentage = percentage;
        }
    }

}