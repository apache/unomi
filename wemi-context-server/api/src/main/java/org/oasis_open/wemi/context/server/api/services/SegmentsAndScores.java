package org.oasis_open.wemi.context.server.api.services;

import java.util.Map;
import java.util.Set;

public class SegmentsAndScores {
    private Set<String> segments;
    private Map<String,Integer> scores;

    public SegmentsAndScores(Set<String> segments, Map<String, Integer> scores) {
        this.segments = segments;
        this.scores = scores;
    }

    public Set<String> getSegments() {
        return segments;
    }

    public Map<String, Integer> getScores() {
        return scores;
    }
}
