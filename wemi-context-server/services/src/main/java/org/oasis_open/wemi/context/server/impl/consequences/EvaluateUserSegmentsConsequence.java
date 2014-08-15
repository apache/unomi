package org.oasis_open.wemi.context.server.impl.consequences;

import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.consequences.Consequence;
import org.oasis_open.wemi.context.server.api.consequences.ConsequenceExecutor;
import org.oasis_open.wemi.context.server.api.services.SegmentService;

import java.util.Set;

/**
 * Created by toto on 14/08/14.
 */
public class EvaluateUserSegmentsConsequence implements ConsequenceExecutor {

    private SegmentService segmentService;

    public SegmentService getSegmentService() {
        return segmentService;
    }

    public void setSegmentService(SegmentService segmentService) {
        this.segmentService = segmentService;
    }

    @Override
    public boolean execute(Consequence consequence, Event event) {
        Set<String> segments = segmentService.getSegmentsForUser(event.getUser());
        if (!segments.equals(event.getUser().getSegments())) {
            event.getUser().setSegments(segments);
            return true;
        }
        return false;
    }
}
