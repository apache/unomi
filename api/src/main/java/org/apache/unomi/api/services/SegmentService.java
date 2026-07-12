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

import java.util.List;

/**
 * Manages {@link org.apache.unomi.api.segments.Segment}s and
 * {@link org.apache.unomi.api.segments.Scoring} definitions.
 * Also recalculates segment membership for profiles when definitions change.
 */
public interface SegmentService {

    /**
     * Returns segment metadata, ordered and paged.
     *
     * @param offset zero-based index of the first result
     * @param size maximum results to return, or {@code -1} for all
     * @param sortBy optional comma-separated property list with optional {@code :asc}/{@code :desc} suffixes
     * @return segment metadata
     */
    PartialList<Metadata> getSegmentMetadatas(int offset, int size, String sortBy);

    /**
     * Returns segment metadata for a scope, ordered and paged.
     *
     * @param scope scope filter
     * @param offset zero-based index of the first result
     * @param size maximum results to return, or {@code -1} for all
     * @param sortBy optional comma-separated property list with optional {@code :asc}/{@code :desc} suffixes
     * @return segment metadata for the scope
     */
    PartialList<Metadata> getSegmentMetadatas(String scope, int offset, int size, String sortBy);

    /**
     * Returns segment metadata matching the given query.
     *
     * @param query filter for segments whose metadata should be returned
     * @return matching segment metadata
     */
    PartialList<Metadata> getSegmentMetadatas(Query query);

    /**
     * Loads a segment definition by id.
     *
     * @param segmentId segment identifier
     * @return matching segment, or {@code null} if none exists
     */
    Segment getSegmentDefinition(String segmentId);

    /**
     * Persists the specified segment in the context server.
     *
     * @param segment the segment to be persisted
     */
    void setSegmentDefinition(Segment segment);

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
     * Returns segment and scoring metadata that depend on the given segment.
     * A dependent definition references the segment in a profile-segment condition.
     *
     * @param segmentId segment identifier
     * @return dependent segment and scoring metadata
     */
    DependentMetadata getSegmentDependentMetadata(String segmentId);

    /**
     * Lists profiles that match a segment's conditions, ordered and paged.
     *
     * @param segmentID segment identifier
     * @param offset zero-based index of the first result
     * @param size maximum results to return, or {@code -1} for all
     * @param sortBy optional comma-separated property list with optional {@code :asc}/{@code :desc} suffixes
     * @return matching profiles
     */
    PartialList<Profile> getMatchingIndividuals(String segmentID, int offset, int size, String sortBy);

    /**
     * Counts profiles that match a segment's conditions.
     *
     * @param segmentID segment identifier
     * @return matching profile count
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
     * Returns current segment memberships and scores for a profile.
     *
     * @param profile profile to evaluate
     * @return segments and scores for the profile
     */
    SegmentsAndScores getSegmentsAndScoresForProfile(Profile profile);

    /**
     * Returns metadata for segments the profile belongs to.
     *
     * @param profile profile to inspect
     * @return segment metadata for the profile's memberships
     */
    List<Metadata> getSegmentMetadatasForProfile(Profile profile);

    /**
     * Returns scoring metadata, ordered and paged.
     *
     * @param offset zero-based index of the first result
     * @param size maximum results to return, or {@code -1} for all
     * @param sortBy optional comma-separated property list with optional {@code :asc}/{@code :desc} suffixes
     * @return scoring metadata
     */
    PartialList<Metadata> getScoringMetadatas(int offset, int size, String sortBy);

    /**
     * Returns scoring metadata matching the given query.
     *
     * @param query filter for scorings whose metadata should be returned
     * @return matching scoring metadata
     */
    PartialList<Metadata> getScoringMetadatas(Query query);

    /**
     * Loads a scoring definition by id.
     *
     * @param scoringId scoring identifier
     * @return matching scoring, or {@code null} if none exists
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
     * Returns segment and scoring metadata that depend on the given scoring.
     * A dependent definition references the scoring in a scoring condition.
     *
     * @param scoringId scoring identifier
     * @return dependent segment and scoring metadata
     */
    DependentMetadata getScoringDependentMetadata(String scoringId);

    /**
     * Builds a stable property key for a past-event condition pair.
     *
     * @param condition nested event condition
     * @param parentCondition parent past-event condition
     * @return generated property key
     */
    String getGeneratedPropertyKey(Condition condition, Condition parentCondition);

    /**
     * This will recalculate the past event conditions from existing rules
     * It will also recalculate date relative Segments and Scorings (when they contains date expression conditions for example)
     * This operation can be heavy and take time, it will:
     * - browse existing rules to extract the past event condition,
     * - query the matching events for those conditions,
     * - update the corresponding profiles
     * - reevaluate segments/scorings linked to this rules to engaged/disengaged profiles after the occurrences have been updated
     * - reevaluate segments/scoring that contains date expressions
     * So use it carefully or execute this method in a dedicated thread.
     */
    void recalculatePastEventConditions();

    /**
     * This will recalculate the past event conditions from existing rules
     * It will also recalculate date relative Segments and Scorings (when they contains date expression conditions for example)
     * This operation can be heavy and take time, it will:
     * - browse existing rules to extract the past event condition,
     * - query the matching events for those conditions,
     * - update the corresponding profiles
     * - reevaluate segments/scorings linked to this rules to engaged/disengaged profiles after the occurrences have been updated
     * - reevaluate segments/scoring that contains date expressions
     * So use it carefully or execute this method in a dedicated thread.
     *
     * @param sendProfileUpdateEvents if true, profileUpdated events will be sent when profiles are updated. Set to false to disable
     *                                 event sending (useful in tests to avoid race conditions).
     */
    void recalculatePastEventConditions(boolean sendProfileUpdateEvents);
}
