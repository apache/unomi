package org.oasis_open.contextserver.impl.actions;

import org.oasis_open.contextserver.api.Event;
import org.oasis_open.contextserver.api.actions.Action;
import org.oasis_open.contextserver.api.actions.ActionExecutor;
import org.oasis_open.contextserver.api.services.SegmentService;
import org.oasis_open.contextserver.api.segments.SegmentsAndScores;

import java.util.Map;
import java.util.Set;

/**
 * Created by toto on 14/08/14.
 */
public class EvaluateUserSegmentsAction implements ActionExecutor {

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
        SegmentsAndScores segmentsAndScoringForUser = segmentService.getSegmentsAndScoresForUser(event.getUser());
        Set<String> segments = segmentsAndScoringForUser.getSegments();
        if (!segments.equals(event.getUser().getSegments())) {
            event.getUser().setSegments(segments);
            updated = true;
        }
        Map<String, Integer> scores = segmentsAndScoringForUser.getScores();
        if (!scores.equals(event.getUser().getScores())) {
            event.getUser().setScores(scores);
            updated = true;
        }
        return updated;
    }
}
