package org.oasis_open.contextserver.api.services;

/*
 * #%L
 * context-server-api
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

import org.oasis_open.contextserver.api.Metadata;
import org.oasis_open.contextserver.api.PartialList;
import org.oasis_open.contextserver.api.Profile;
import org.oasis_open.contextserver.api.segments.Scoring;
import org.oasis_open.contextserver.api.segments.Segment;
import org.oasis_open.contextserver.api.segments.SegmentsAndScores;

import java.util.Map;
import java.util.Set;

public interface SegmentService {

    Set<Metadata> getSegmentMetadatas();

    Set<Metadata> getSegmentMetadatas(String scope);

    /**
     * Retrieves segment metadata by scope. If no scope is provided, then all segment metadata is returned. If a scope is provided, we only return the segment metadata
     * associated with the provided scope with, optionally, if <code>includeShared</code> is <code>true</code>, shared segments.
     *
     * @param scope         a potentially <code>null</code> scope about which we want to retrieve metadata
     * @param includeShared <code>true</code> if we want to also include shared segments, <code>false</code> otherwise
     * @return a <code>Map</code> of segment metadata, each entry consisting of a scope name and a set of associated segment metadata
     */
    Map<String, Set<Metadata>> getScopedSegmentMetadata(String scope, boolean includeShared);

    Segment getSegmentDefinition(String scope, String segmentId);

    void setSegmentDefinition(Segment segment);

    void removeSegmentDefinition(String scope, String segmentId);

    PartialList<Profile> getMatchingIndividuals(String scope, String segmentID, int offset, int size, String sortBy);

    long getMatchingIndividualsCount(String scope, String segmentID);

    Boolean isProfileInSegment(Profile profile, String scope, String segmentId);

    SegmentsAndScores getSegmentsAndScoresForProfile(Profile profile);

    Set<Metadata> getScoringMetadatas();

    Set<Metadata> getScoringMetadatas(String scope);

    Scoring getScoringDefinition(String scope, String scoringId);

    void setScoringDefinition(Scoring scoring);

    void createScoringDefinition(String scope, String scoringId, String name, String description);

    void removeScoringDefinition(String scope, String scoringId);

}
