package org.oasis_open.contextserver.plugins.baseplugin.actions;

/*
 * #%L
 * context-server-services
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

import org.oasis_open.contextserver.api.Event;
import org.oasis_open.contextserver.api.actions.Action;
import org.oasis_open.contextserver.api.actions.ActionExecutor;
import org.oasis_open.contextserver.api.services.EventService;
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
    public int execute(Action action, Event event) {
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
        return updated ? EventService.PROFILE_UPDATED : EventService.NO_CHANGE;
    }
}
