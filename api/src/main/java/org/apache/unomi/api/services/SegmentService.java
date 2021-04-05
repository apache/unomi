/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.unomi.api.services;

import org.apache.unomi.api.Item;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.segments.DependentMetadata;
import org.apache.unomi.api.segments.Scoring;
import org.apache.unomi.api.segments.Segment;
import org.apache.unomi.api.segments.SegmentsAndScores;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * A service to access and operate on {@link Segment}s and {@link Scoring}s
 */
public interface SegmentService {

    /**
     * Retrieves segment metadatas, ordered according to the specified {@code sortBy} String and and paged: only {@code size} of them are retrieved, starting with the {@code
     * offset}-th one.
     *
     * @param offset zero or a positive integer specifying the position of the first element in the total ordered collection of matching elements
     * @param size   a positive integer specifying how many matching elements should be retrieved or {@code -1} if all of them should be retrieved
     * @param sortBy an optional ({@code null} if no sorting is required) String of comma ({@code ,}) separated property names on which ordering should be performed, ordering elements according to the property order in the
     *               String, considering each in turn and moving on to the next one in case of equality of all preceding ones. Each property name is optionally followed by
     *               a column ({@code :}) and an order specifier: {@code asc} or {@code desc}.
     * @return a {@link PartialList} of segment metadata
     */
    PartialList<Metadata> getSegmentMetadatas(int offset, int size, String sortBy);

    /**
     * Retrieves segment metadatas for segments in the specified scope, ordered according to the specified {@code sortBy} String and and paged: only {@code size} of them are
     * retrieved, starting with the {@code offset}-th one.
     * TODO: remove?
     *
     * @param scope  the scope for which we want to retrieve segment metadata
     * @param offset zero or a positive integer specifying the position of the first element in the total ordered collection of matching elements
     * @param size   a positive integer specifying how many matching elements should be retrieved or {@code -1} if all of them should be retrieved
     * @param sortBy an optional ({@code null} if no sorting is required) String of comma ({@code ,}) separated property names on which ordering should be performed, ordering elements according to the property order in the
     *               String, considering each in turn and moving on to the next one in case of equality of all preceding ones. Each property name is optionally followed by
     *               a column ({@code :}) and an order specifier: {@code asc} or {@code desc}.
     * @return a {@link PartialList} of segment metadata
     */
    PartialList<Metadata> getSegmentMetadatas(String scope, int offset, int size, String sortBy);

    /**
     * Retrieves the metadata for segments matching the specified {@link Query}.
     *
     * @param query the query that the segments must match for their metadata to be retrieved
     * @return a {@link PartialList} of segment metadata
     */
    PartialList<Metadata> getSegmentMetadatas(Query query);

    /**
     * Retrieves the segment identified by the specified identifier.
     *
     * @param segmentId the identifier of the segment to be retrieved
     * @return the segment identified by the specified identifier or {@code null} if no such segment exists
     */
    Segment getSegmentDefinition(String segmentId);

    /**
     * Persists the specified segment in the context server.
     *
     * @param segment the segment to be persisted
     */
    void setSegmentDefinition(Segment segment);

    /**
     * @see SegmentService#setSegmentDefinition(org.apache.unomi.api.segments.Segment)
     * @param segment
     * @return
     */
    Mono<Void> setSegmentDefinitionRx(Segment segment);

    /**
     * Removes the segment definition identified by the specified identifier. We can specify that we want the operation to be validated beforehand so that we can
     * know if any other segment that might use the segment we're trying to delete as a condition might be impacted. If {@code validate} is set to {@code false}, no
     * validation is performed. If set to {@code true}, we will first check if any segment or scoring depends on the segment we're trying to delete and if so we will not delete the
     * segment but rather return the list of the metadata of the impacted items. If no dependents are found, then we properly delete the segment.
     *
     * @param segmentId the identifier of the segment we want to delete
     * @param validate  whether or not to perform validation
     * @return a list of impacted segment metadata if any or an empty list if none were found or validation was skipped
     */
    DependentMetadata removeSegmentDefinition(String segmentId, boolean validate);

    /**
     * Retrieves the list of Segment and Scoring metadata depending on the specified segment.
     * A segment or scoring is depending on a segment if it includes a profileSegmentCondition with a test on this segment.
     *
     * @param segmentId the segment identifier
     * @return a list of Segment/Scoring Metadata depending on the specified segment
     */
    DependentMetadata getSegmentDependentMetadata(String segmentId);

    /**
     * Retrieves a list of profiles matching the conditions defined by the segment identified by the specified identifier, ordered according to the specified {@code sortBy}
     * String and and paged: only {@code size} of them are retrieved, starting with the {@code offset}-th one.
     *
     * @param segmentID the identifier of the segment for which we want to retrieve matching profiles
     * @param offset    zero or a positive integer specifying the position of the first element in the total ordered collection of matching elements
     * @param size      a positive integer specifying how many matching elements should be retrieved or {@code -1} if all of them should be retrieved
     * @param sortBy    an optional ({@code null} if no sorting is required) String of comma ({@code ,}) separated property names on which ordering should be performed, ordering elements according to the property order in the
     *                  String, considering each in turn and moving on to the next one in case of equality of all preceding ones. Each property name is optionally followed by
     *                  a column ({@code :}) and an order specifier: {@code asc} or {@code desc}.
     * @return a {@link PartialList} of profiles matching the specified segment
     */
    PartialList<Profile> getMatchingIndividuals(String segmentID, int offset, int size, String sortBy);

    /**
     * Retrieves the number of profiles matching the conditions defined by the segment identified by the specified identifier.
     *
     * @param segmentID the identifier of the segment for which we want to retrieve matching profiles
     * @return the number of profiles matching the conditions defined by the segment identified by the specified identifier
     */
    long getMatchingIndividualsCount(String segmentID);

    /**
     * Determines whether the specified profile is part of the segment identified by the specified identifier.
     *
     * @param profile   the profile we want to check
     * @param segmentId the identifier of the segment against which we want to check the profile
     * @return {@code true} if the specified profile is in the specified segment, {@code false} otherwise
     */
    Boolean isProfileInSegment(Profile profile, String segmentId);

    /**
     * Retrieves the segments and scores for the specified profile.
     *
     * @param profile the profile for which we want to retrieve segments and scores
     * @return a {@link SegmentsAndScores} instance encapsulating the segments and scores for the specified profile
     */
    SegmentsAndScores getSegmentsAndScoresForProfile(Profile profile);

    /**
     * Retrieves the list of segment metadata for the segments the specified profile is a member of.
     *
     * @param profile the profile for which we want to retrieve the segment metadata
     * @return the (possibly empty) list of segment metadata for the segments the specified profile is a member of
     */
    List<Metadata> getSegmentMetadatasForProfile(Profile profile);

    /**
     * Retrieves the set of all scoring metadata.
     *
     * @param offset the offset
     * @param size   the size
     * @param sortBy sort by
     * @return the set of all scoring metadata
     */
    PartialList<Metadata> getScoringMetadatas(int offset, int size, String sortBy);

    /**
     * Retrieves the set of scoring metadata for scorings matching the specified query.
     *
     * @param query the query the scorings must match for their metadata to be retrieved
     * @return the set of scoring metadata for scorings matching the specified query
     */
    PartialList<Metadata> getScoringMetadatas(Query query);

    /**
     * Retrieves the scoring identified by the specified identifier.
     *
     * @param scoringId the identifier of the scoring to be retrieved
     * @return the scoring identified by the specified identifier or {@code null} if no such scoring exists
     */
    Scoring getScoringDefinition(String scoringId);

    /**
     * Persists the specified scoring in the context server.
     *
     * @param scoring the scoring to be persisted
     */
    void setScoringDefinition(Scoring scoring);

    /**
     * Creates a scoring with the specified scope, identifier, name and description.
     *
     * @param scope       the scope for the new scoring
     * @param scoringId   the identifier for the new scoring
     * @param name        the name of the new scoring
     * @param description the description of the new scoring
     * @see Item Item's description for a discussion of scope
     */
    void createScoringDefinition(String scope, String scoringId, String name, String description);

    /**
     * Removes the scoring definition identified by the specified identifier. We can specify that we want the operation to be validated beforehand so that we can
     * know if any other segment that might use the segment we're trying to delete as a condition might be impacted. If {@code validate} is set to {@code false}, no
     * validation is performed. If set to {@code true}, we will first check if any segment or scoring depends on the scoring we're trying to delete and if so we will not delete the
     * scoring but rather return the list of the metadata of the impacted items. If no dependents are found, then we properly delete the scoring.
     *
     * @param scoringId the identifier of the scoring we want to delete
     * @param validate  whether or not to perform validation
     * @return a list of impacted items metadata if any or an empty list if none were found or validation was skipped
     */
    DependentMetadata removeScoringDefinition(String scoringId, boolean validate);

    /**
     * Retrieves the list of Segment and Scoring metadata depending on the specified scoring.
     * A segment or scoring is depending on a segment if it includes a scoringCondition with a test on this scoring.
     *
     * @param scoringId the segment identifier
     * @return a list of Segment/Scoring Metadata depending on the specified scoring
     */
    DependentMetadata getScoringDependentMetadata(String scoringId);

    /**
     * Get generated property key for past event condition
     * @param condition The event condition
     * @param parentCondition The past event condition
     * @return
     */
    String getGeneratedPropertyKey(Condition condition, Condition parentCondition);
}
