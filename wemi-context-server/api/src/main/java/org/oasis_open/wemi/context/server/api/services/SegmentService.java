package org.oasis_open.wemi.context.server.api.services;

import org.oasis_open.wemi.context.server.api.*;

import java.util.Set;

/**
 * Created by loom on 24.04.14.
 */
public interface SegmentService {

    Set<User> getMatchingIndividuals (String segmentIDs);
    Boolean isUserInSegment (User user, String segmentDescription);
    Set<String> getSegmentsForUser(User user);
    Set<SegmentDescription> getSegmentDescriptions();
    SegmentDefinition getSegmentDefinition (String segmentDescription);
    void setSegmentDefinition (String segmentId, SegmentDefinition segmentDefinition);
    void createSegmentDefinition(String segmentId, String name, String description);
    void removeSegmentDefinition(String segmentDescription);

}
