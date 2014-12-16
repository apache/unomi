package org.oasis_open.wemi.context.server.api.services;

import org.oasis_open.wemi.context.server.api.Metadata;
import org.oasis_open.wemi.context.server.api.PartialList;
import org.oasis_open.wemi.context.server.api.segments.Scoring;
import org.oasis_open.wemi.context.server.api.segments.Segment;
import org.oasis_open.wemi.context.server.api.User;
import org.oasis_open.wemi.context.server.api.segments.SegmentsAndScores;

import java.util.Set;

/**
 * Created by loom on 24.04.14.
 */
public interface SegmentService {

    Set<Metadata> getSegmentMetadatas();

    Set<Metadata> getSegmentMetadatas(String scope);

    Segment getSegmentDefinition(String scope, String segmentId);

    void setSegmentDefinition(Segment segment);

    void createSegmentDefinition(String scope, String segmentId, String name, String description);

    void removeSegmentDefinition(String scope, String segmentId);

    PartialList<User> getMatchingIndividuals(String scope, String segmentID, int offset, int size, String sortBy);

    long getMatchingIndividualsCount(String scope, String segmentID);

    Boolean isUserInSegment(User user, String scope, String segmentId);

    SegmentsAndScores getSegmentsAndScoresForUser(User user);

    Set<Metadata> getScoringMetadatas();

    Set<Metadata> getScoringMetadatas(String scope);

    Scoring getScoringDefinition(String scope, String scoringId);

    void setScoringDefinition(Scoring scoring);

    void createScoringDefinition(String scope, String scoringId, String name, String description);

    void removeScoringDefinition(String scope, String scoringId);

}
