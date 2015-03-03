package org.oasis_open.contextserver.impl.actions;

import org.oasis_open.contextserver.api.Event;
import org.oasis_open.contextserver.api.actions.Action;
import org.oasis_open.contextserver.api.actions.ActionExecutor;
import org.oasis_open.contextserver.api.services.SegmentService;
import org.oasis_open.contextserver.api.segments.SegmentsAndScores;

import java.util.Map;
import java.util.Set;

public class EvaluateProfileSegmentsAction implements ActionExecutor {

    private SegmentService segmentService;

    public SegmentService getSegmentService() {
        return segmentService;
    }

    public void setSegmentService(SegmentService segmentService) {
        this.segmentService = segmentService;
    }

    @Override
    public boolean execute(Action action, Event event) {
        boolean updated = false;
        SegmentsAndScores segmentsAndScoringForProfile = segmentService.getSegmentsAndScoresForProfile(event.getProfile());
        Set<String> segments = segmentsAndScoringForProfile.getSegments();
        if (!segments.equals(event.getProfile().getSegments())) {
            event.getProfile().setSegments(segments);
            updated = true;
        }
        Map<String, Integer> scores = segmentsAndScoringForProfile.getScores();
        if (!scores.equals(event.getProfile().getScores())) {
            event.getProfile().setScores(scores);
            updated = true;
        }
        return updated;
    }
}
