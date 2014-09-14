package org.oasis_open.wemi.context.server.impl.actions;

import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.Persona;
import org.oasis_open.wemi.context.server.api.actions.Action;
import org.oasis_open.wemi.context.server.api.actions.ActionExecutor;
import org.oasis_open.wemi.context.server.api.services.SegmentService;

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
        if (event.getUser() instanceof Persona) {
            return false;
        }

        Set<String> segments = segmentService.getSegmentsForUser(event.getUser());
        if (!segments.equals(event.getUser().getSegments())) {
            event.getUser().setSegments(segments);
            return true;
        }
        return false;
    }
}
