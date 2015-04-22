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

import java.util.List;
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

    /**
     * Removes the segment definition associated with the specified scope and identifier. We can specify that we want the operation to be validated beforehand so that we can
     * know if any other segment that might use the segment we're trying to delete as a condition might be impacted. If <code>validate</code> is set to <code>false</code>, no
     * validation is performed. If set to <code>true</code>, we will first check if any segments depend on the one we're trying to delete and if so we will not delete the
     * segment but rather return the list of the metadata of the impacted segments. If no dependents are found, then we properly delete the segment.
     *
     * @param scope     the scope of the segment we want to delete
     * @param segmentId the identifier of the segment we want to delete within the specified scope
     * @param validate  whether or not to perform validation
     * @return a list of impacted segment metadata if any or an empty if no such impacted segments are found or validation was skipped
     */
    List<Metadata> removeSegmentDefinition(String scope, String segmentId, boolean validate);

    PartialList<Profile> getMatchingIndividuals(String scope, String segmentID, int offset, int size, String sortBy);

    long getMatchingIndividualsCount(String scope, String segmentID);

    Boolean isProfileInSegment(Profile profile, String scope, String segmentId);

    SegmentsAndScores getSegmentsAndScoresForProfile(Profile profile);

    List<Metadata> getSegmentMetadatasForProfile(Profile profile);

    Set<Metadata> getScoringMetadatas();

    Set<Metadata> getScoringMetadatas(String scope);

    Scoring getScoringDefinition(String scope, String scoringId);

    void setScoringDefinition(Scoring scoring);

    void createScoringDefinition(String scope, String scoringId, String name, String description);

    void removeScoringDefinition(String scope, String scoringId);

}
