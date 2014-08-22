package org.oasis_open.wemi.context.server.api.goals;

import java.io.Serializable;
import java.util.Map;

/**
 * Created by toto on 21/08/14.
 */
public class GoalReport implements Serializable {
    private Stat globalStats;
    private Map<String,Stat> split;

    public GoalReport() {
    }

    public Stat getGlobalStats() {
        return globalStats;
    }

    public void setGlobalStats(Stat globalStats) {
        this.globalStats = globalStats;
    }

    public Map<String,Stat> getSplit() {
        return split;
    }

    public void setSplit(Map<String, Stat> split) {
        this.split = split;
    }

    public static class Stat implements Serializable {
        private long startCount;
        private long targetCount;
        private double conversionRate;

        public Stat() {
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

    }

}