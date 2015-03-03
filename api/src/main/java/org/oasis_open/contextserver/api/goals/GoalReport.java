package org.oasis_open.contextserver.api.goals;

import java.io.Serializable;
import java.util.List;

public class GoalReport implements Serializable {
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