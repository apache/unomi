package org.oasis_open.wemi.context.server.api.services;

import org.oasis_open.wemi.context.server.api.Metadata;
import org.oasis_open.wemi.context.server.api.PartialList;
import org.oasis_open.wemi.context.server.api.SegmentDefinition;
import org.oasis_open.wemi.context.server.api.User;

import java.util.Set;

/**
 * Created by loom on 24.04.14.
 */
public interface SegmentService {

    PartialList<User> getMatchingIndividuals(String segmentID, int offset, int size, String sortBy);

    long getMatchingIndividualsCount(String segmentID);
    Boolean isUserInSegment (User user, String segmentDescription);
    Set<String> getSegmentsForUser(User user);
    Set<Metadata> getSegmentMetadatas();
    SegmentDefinition getSegmentDefinition (String segmentDescription);
    void setSegmentDefinition (String segmentId, SegmentDefinition segmentDefinition);
    void createSegmentDefinition(String segmentId, String name, String description);
    void removeSegmentDefinition(String segmentDescription);

}
