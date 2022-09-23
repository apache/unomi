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

package org.apache.unomi.services.impl.segments;

import com.fasterxml.jackson.core.JsonProcessingException;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.CharEncoding;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.Item;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.rules.Rule;
import org.apache.unomi.api.segments.*;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.RulesService;
import org.apache.unomi.api.services.SchedulerService;
import org.apache.unomi.api.services.SegmentService;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.apache.unomi.persistence.spi.aggregate.TermsAggregate;
import org.apache.unomi.services.impl.AbstractServiceImpl;
import org.apache.unomi.services.impl.scheduler.SchedulerServiceImpl;
import org.apache.unomi.api.utils.ParserHelper;
import org.apache.unomi.api.exceptions.BadSegmentConditionException;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.SynchronousBundleListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class SegmentServiceImpl extends AbstractServiceImpl implements SegmentService, SynchronousBundleListener {

    private static final Logger logger = LoggerFactory.getLogger(SegmentServiceImpl.class.getName());

    private static final String VALIDATION_PROFILE_ID = "validation-profile-id";
    private static final String RESET_SCORING_SCRIPT = "resetScoringPlan";
    private static final String EVALUATE_SCORING_ELEMENT_SCRIPT = "evaluateScoringPlanElement";

    private BundleContext bundleContext;

    private EventService eventService;
    private RulesService rulesService;
    private SchedulerService schedulerService;

    private long taskExecutionPeriod = 1;
    private List<Segment> allSegments;
    private List<Scoring> allScoring;
    private int segmentUpdateBatchSize = 1000;
    private long segmentRefreshInterval = 1000;
    private int aggregateQueryBucketSize = 5000;
    private int maxRetriesForUpdateProfileSegment = 0;
    private long secondsDelayForRetryUpdateProfileSegment = 1;
    private boolean batchSegmentProfileUpdate = false;
    private boolean sendProfileUpdateEventForSegmentUpdate = true;
    private int maximumIdsQueryCount = 5000;
    private boolean pastEventsDisablePartitions = false;
    private int dailyDateExprEvaluationHourUtc = 5;

    public SegmentServiceImpl() {
        logger.info("Initializing segment service...");
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void setEventService(EventService eventService) {
        this.eventService = eventService;
    }

    public void setRulesService(RulesService rulesService) {
        this.rulesService = rulesService;
    }

    public void setSchedulerService(SchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }

    public void setSegmentUpdateBatchSize(int segmentUpdateBatchSize) {
        this.segmentUpdateBatchSize = segmentUpdateBatchSize;
    }

    public void setAggregateQueryBucketSize(int aggregateQueryBucketSize) {
        this.aggregateQueryBucketSize = aggregateQueryBucketSize;
    }

    public void setMaximumIdsQueryCount(int maximumIdsQueryCount) {
        this.maximumIdsQueryCount = maximumIdsQueryCount;
    }

    public void setPastEventsDisablePartitions(boolean pastEventsDisablePartitions) {
        this.pastEventsDisablePartitions = pastEventsDisablePartitions;
    }

    public void setSegmentRefreshInterval(long segmentRefreshInterval) {
        this.segmentRefreshInterval = segmentRefreshInterval;
    }

    public void setMaxRetriesForUpdateProfileSegment(int maxRetriesForUpdateProfileSegment) {
        this.maxRetriesForUpdateProfileSegment = maxRetriesForUpdateProfileSegment;
    }

    public void setSecondsDelayForRetryUpdateProfileSegment(long secondsDelayForRetryUpdateProfileSegment) {
        this.secondsDelayForRetryUpdateProfileSegment = secondsDelayForRetryUpdateProfileSegment;
    }

    public void setBatchSegmentProfileUpdate(boolean batchSegmentProfileUpdate) {
        this.batchSegmentProfileUpdate = batchSegmentProfileUpdate;
    }

    public void setSendProfileUpdateEventForSegmentUpdate(boolean sendProfileUpdateEventForSegmentUpdate) {
        this.sendProfileUpdateEventForSegmentUpdate = sendProfileUpdateEventForSegmentUpdate;
    }

    public void setDailyDateExprEvaluationHourUtc(int dailyDateExprEvaluationHourUtc) {
        this.dailyDateExprEvaluationHourUtc = dailyDateExprEvaluationHourUtc;
    }

    public void postConstruct() throws IOException {
        logger.debug("postConstruct {" + bundleContext.getBundle() + "}");
        loadPredefinedSegments(bundleContext);
        loadPredefinedScorings(bundleContext);
        for (Bundle bundle : bundleContext.getBundles()) {
            if (bundle.getBundleContext() != null && bundle.getBundleId() != bundleContext.getBundle().getBundleId()) {
                loadPredefinedSegments(bundle.getBundleContext());
                loadPredefinedScorings(bundle.getBundleContext());
            }
        }
        bundleContext.addBundleListener(this);
        initializeTimer();
        loadScripts();
        logger.info("Segment service initialized.");
    }

    public void preDestroy() {
        bundleContext.removeBundleListener(this);
        logger.info("Segment service shutdown.");
    }

    private void processBundleStartup(BundleContext bundleContext) {
        if (bundleContext == null) {
            return;
        }
        loadPredefinedSegments(bundleContext);
        loadPredefinedScorings(bundleContext);
    }

    private void processBundleStop(BundleContext bundleContext) {
        if (bundleContext == null) {
            return;
        }
    }

    private void loadPredefinedSegments(BundleContext bundleContext) {
        Enumeration<URL> predefinedSegmentEntries = bundleContext.getBundle().findEntries("META-INF/cxs/segments", "*.json", true);
        if (predefinedSegmentEntries == null) {
            return;
        }

        while (predefinedSegmentEntries.hasMoreElements()) {
            URL predefinedSegmentURL = predefinedSegmentEntries.nextElement();
            logger.debug("Found predefined segment at " + predefinedSegmentURL + ", loading... ");

            try {
                Segment segment = CustomObjectMapper.getObjectMapper().readValue(predefinedSegmentURL, Segment.class);
                if (segment.getMetadata().getScope() == null) {
                    segment.getMetadata().setScope("systemscope");
                }
                setSegmentDefinition(segment);
                logger.info("Predefined segment with id {} registered", segment.getMetadata().getId());
            } catch (IOException e) {
                logger.error("Error while loading segment definition " + predefinedSegmentURL, e);
            }
        }
    }

    private void loadPredefinedScorings(BundleContext bundleContext) {
        Enumeration<URL> predefinedScoringEntries = bundleContext.getBundle().findEntries("META-INF/cxs/scoring", "*.json", true);
        if (predefinedScoringEntries == null) {
            return;
        }

        while (predefinedScoringEntries.hasMoreElements()) {
            URL predefinedScoringURL = predefinedScoringEntries.nextElement();
            logger.debug("Found predefined scoring at " + predefinedScoringURL + ", loading... ");

            try {
                Scoring scoring = CustomObjectMapper.getObjectMapper().readValue(predefinedScoringURL, Scoring.class);
                if (scoring.getMetadata().getScope() == null) {
                    scoring.getMetadata().setScope("systemscope");
                }
                setScoringDefinition(scoring);
                logger.info("Predefined scoring with id {} registered", scoring.getMetadata().getId());
            } catch (IOException e) {
                logger.error("Error while loading segment definition " + predefinedScoringURL, e);
            }
        }
    }

    public PartialList<Metadata> getSegmentMetadatas(int offset, int size, String sortBy) {
        return getMetadatas(offset, size, sortBy, Segment.class);
    }

    public PartialList<Metadata> getSegmentMetadatas(String scope, int offset, int size, String sortBy) {
        PartialList<Segment> segments = persistenceService.query("metadata.scope", scope, sortBy, Segment.class, offset, size);
        List<Metadata> details = new LinkedList<>();
        for (Segment definition : segments.getList()) {
            details.add(definition.getMetadata());
        }
        return new PartialList<>(details, segments.getOffset(), segments.getPageSize(), segments.getTotalSize(), segments.getTotalSizeRelation());
    }

    public PartialList<Metadata> getSegmentMetadatas(Query query) {
        return getMetadatas(query, Segment.class);
    }

    private List<Segment> getAllSegmentDefinitions() {
        List<Segment> allItems = persistenceService.getAllItems(Segment.class);
        for (Segment segment : allItems) {
            if (segment.getMetadata().isEnabled()) {
                ParserHelper.resolveConditionType(definitionsService, segment.getCondition(), "segment " + segment.getItemId());
            }
        }
        return allItems;
    }

    public Segment getSegmentDefinition(String segmentId) {
        Segment definition = persistenceService.load(segmentId, Segment.class);
        if (definition != null && definition.getMetadata().isEnabled()) {
            ParserHelper.resolveConditionType(definitionsService, definition.getCondition(), "segment " + segmentId);
        }
        return definition;
    }

    public void setSegmentDefinition(Segment segment) {
        if (segment.getMetadata().isEnabled()) {
            ParserHelper.resolveConditionType(definitionsService, segment.getCondition(), "segment " + segment.getItemId());
            if (!persistenceService.isValidCondition(segment.getCondition(), new Profile(VALIDATION_PROFILE_ID))) {
                throw new BadSegmentConditionException();
            }
            if (!segment.getMetadata().isMissingPlugins()) {
                updateAutoGeneratedRules(segment.getMetadata(), segment.getCondition());
            }
        }

        // make sure we update the name and description metadata that might not match, so first we remove the entry from the map
        persistenceService.save(segment, null, true);
        updateExistingProfilesForSegment(segment);
    }

    private boolean checkSegmentDeletionImpact(Condition condition, String segmentToDeleteId) {
        if (condition != null) {
            @SuppressWarnings("unchecked") final List<Condition> subConditions = (List<Condition>) condition.getParameter("subConditions");
            if (subConditions != null) {
                for (Condition subCondition : subConditions) {
                    if (checkSegmentDeletionImpact(subCondition, segmentToDeleteId)) {
                        return true;
                    }
                }
            } else if ("profileSegmentCondition".equals(condition.getConditionTypeId())) {
                @SuppressWarnings("unchecked") final List<String> referencedSegmentIds = (List<String>) condition.getParameter("segments");

                if (referencedSegmentIds.indexOf(segmentToDeleteId) >= 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Return an updated condition that do not contain a condition on the segmentId anymore
     * it's remove the unnecessary boolean condition (if a condition is the only one of a boolean the boolean will be remove and the subcondition returned)
     * it's return null when there is no more condition after (if the condition passed was only a segment condition on the segmentId)
     *
     * @param condition the condition to update
     * @param segmentId the segment id to remove in the condition
     * @return updated condition
     */
    private Condition updateSegmentDependentCondition(Condition condition, String segmentId) {
        if ("booleanCondition".equals(condition.getConditionTypeId())) {
            @SuppressWarnings("unchecked") final List<Condition> subConditions = (List<Condition>) condition.getParameter("subConditions");
            List<Condition> updatedSubConditions = new LinkedList<>();
            for (Condition subCondition : subConditions) {
                Condition updatedCondition = updateSegmentDependentCondition(subCondition, segmentId);
                if (updatedCondition != null) {
                    updatedSubConditions.add(updatedCondition);
                }
            }
            if (!updatedSubConditions.isEmpty()) {
                if (updatedSubConditions.size() == 1) {
                    return updatedSubConditions.get(0);
                } else {
                    condition.setParameter("subConditions", updatedSubConditions);
                    return condition;
                }
            } else {
                return null;
            }
        } else if ("profileSegmentCondition".equals(condition.getConditionTypeId())) {
            @SuppressWarnings("unchecked") final List<String> referencedSegmentIds = (List<String>) condition.getParameter("segments");
            if (referencedSegmentIds.indexOf(segmentId) >= 0) {
                referencedSegmentIds.remove(segmentId);
                if (referencedSegmentIds.isEmpty()) {
                    return null;
                } else {
                    condition.setParameter("segments", referencedSegmentIds);
                }
            }
        }
        return condition;
    }

    private Set<Segment> getSegmentDependentSegments(String segmentId) {
        Set<Segment> impactedSegments = new HashSet<>(this.allSegments.size());
        for (Segment segment : this.allSegments) {
            if (checkSegmentDeletionImpact(segment.getCondition(), segmentId)) {
                impactedSegments.add(segment);
            }
        }
        return impactedSegments;
    }

    private Set<Scoring> getSegmentDependentScorings(String segmentId) {
        Set<Scoring> impactedScoring = new HashSet<>(this.allScoring.size());
        for (Scoring scoring : this.allScoring) {
            for (ScoringElement element : scoring.getElements()) {
                if (checkSegmentDeletionImpact(element.getCondition(), segmentId)) {
                    impactedScoring.add(scoring);
                    break;
                }
            }
        }
        return impactedScoring;
    }

    public DependentMetadata getSegmentDependentMetadata(String segmentId) {
        List<Metadata> segments = new LinkedList<>();
        List<Metadata> scorings = new LinkedList<>();
        for (Segment definition : getSegmentDependentSegments(segmentId)) {
            segments.add(definition.getMetadata());
        }
        for (Scoring definition : getSegmentDependentScorings(segmentId)) {
            scorings.add(definition.getMetadata());
        }
        return new DependentMetadata(segments, scorings);
    }

    public DependentMetadata removeSegmentDefinition(String segmentId, boolean validate) {
        Set<Segment> impactedSegments = getSegmentDependentSegments(segmentId);
        Set<Scoring> impactedScorings = getSegmentDependentScorings(segmentId);
        if (!validate || (impactedSegments.isEmpty() && impactedScorings.isEmpty())) {
            // update profiles
            Condition segmentCondition = new Condition();
            segmentCondition.setConditionType(definitionsService.getConditionType("profilePropertyCondition"));
            segmentCondition.setParameter("propertyName", "segments");
            segmentCondition.setParameter("comparisonOperator", "equals");
            segmentCondition.setParameter("propertyValue", segmentId);
            updateProfilesSegment(segmentCondition, segmentId, false, false);

            // update impacted segments
            for (Segment segment : impactedSegments) {
                Condition updatedCondition = updateSegmentDependentCondition(segment.getCondition(), segmentId);
                segment.setCondition(updatedCondition);
                if (updatedCondition == null) {
                    clearAutoGeneratedRules(persistenceService.query("linkedItems", segment.getMetadata().getId(), null, Rule.class), segment.getMetadata().getId());
                    segment.getMetadata().setEnabled(false);
                }
                setSegmentDefinition(segment);
            }

            // update impacted scorings
            for (Scoring scoring : impactedScorings) {
                List<ScoringElement> updatedScoringElements = new ArrayList<>();
                for (ScoringElement scoringElement : scoring.getElements()) {
                    Condition updatedCondition = updateSegmentDependentCondition(scoringElement.getCondition(), segmentId);
                    if (updatedCondition != null) {
                        scoringElement.setCondition(updatedCondition);
                        updatedScoringElements.add(scoringElement);
                    }
                }
                scoring.setElements(updatedScoringElements);
                if (updatedScoringElements.isEmpty()) {
                    clearAutoGeneratedRules(persistenceService.query("linkedItems", scoring.getMetadata().getId(), null, Rule.class), scoring.getMetadata().getId());
                    scoring.getMetadata().setEnabled(false);
                }
                setScoringDefinition(scoring);
            }

            persistenceService.remove(segmentId, Segment.class);
            List<Rule> previousRules = persistenceService.query("linkedItems", segmentId, null, Rule.class);
            clearAutoGeneratedRules(previousRules, segmentId);
        }

        List<Metadata> segments = new LinkedList<>();
        List<Metadata> scorings = new LinkedList<>();
        for (Segment definition : impactedSegments) {
            segments.add(definition.getMetadata());
        }
        for (Scoring definition : impactedScorings) {
            scorings.add(definition.getMetadata());
        }
        return new DependentMetadata(segments, scorings);
    }

    public PartialList<Profile> getMatchingIndividuals(String segmentID, int offset, int size, String sortBy) {
        Segment segment = getSegmentDefinition(segmentID);
        if (segment == null) {
            return new PartialList<Profile>();
        }
        Condition segmentCondition = new Condition(definitionsService.getConditionType("profilePropertyCondition"));
        segmentCondition.setParameter("propertyName", "segments");
        segmentCondition.setParameter("comparisonOperator", "equals");
        segmentCondition.setParameter("propertyValue", segmentID);

        return persistenceService.query(segmentCondition, sortBy, Profile.class, offset, size);
    }

    public long getMatchingIndividualsCount(String segmentID) {
        if (getSegmentDefinition(segmentID) == null) {
            return 0;
        }

        Condition segmentCondition = new Condition(definitionsService.getConditionType("profilePropertyCondition"));
        segmentCondition.setParameter("propertyName", "segments");
        segmentCondition.setParameter("comparisonOperator", "equals");
        segmentCondition.setParameter("propertyValue", segmentID);

        return persistenceService.queryCount(segmentCondition, Profile.ITEM_TYPE);
    }

    public Boolean isProfileInSegment(Profile profile, String segmentId) {
        Set<String> matchingSegments = getSegmentsAndScoresForProfile(profile).getSegments();

        return matchingSegments.contains(segmentId);
    }

    public SegmentsAndScores getSegmentsAndScoresForProfile(Profile profile) {
        Set<String> segments = new HashSet<String>();
        Map<String, Integer> scores = new HashMap<String, Integer>();

        List<Segment> allSegments = this.allSegments;
        for (Segment segment : allSegments) {
            if (segment.getMetadata().isEnabled() && persistenceService.testMatch(segment.getCondition(), profile)) {
                segments.add(segment.getMetadata().getId());
            }
        }

        List<Scoring> allScoring = this.allScoring;
        Map<String, Integer> scoreModifiers = (Map<String, Integer>) profile.getSystemProperties().get("scoreModifiers");
        for (Scoring scoring : allScoring) {
            if (scoring.getMetadata().isEnabled()) {
                int score = 0;
                for (ScoringElement scoringElement : scoring.getElements()) {
                    if (persistenceService.testMatch(scoringElement.getCondition(), profile)) {
                        score += scoringElement.getValue();
                    }
                }
                String scoringId = scoring.getMetadata().getId();
                if (scoreModifiers != null && scoreModifiers.containsKey(scoringId) && scoreModifiers.get(scoringId) != null) {
                    score += scoreModifiers.get(scoringId);
                }
                scores.put(scoringId, score);
            }
        }

        return new SegmentsAndScores(segments, scores);
    }

    public List<Metadata> getSegmentMetadatasForProfile(Profile profile) {
        List<Metadata> metadatas = new ArrayList<>();

        List<Segment> allSegments = this.allSegments;
        for (Segment segment : allSegments) {
            if (segment.getMetadata().isEnabled() && persistenceService.testMatch(segment.getCondition(), profile)) {
                metadatas.add(segment.getMetadata());
            }
        }

        return metadatas;
    }

    public PartialList<Metadata> getScoringMetadatas(int offset, int size, String sortBy) {
        return getMetadatas(offset, size, sortBy, Scoring.class);
    }

    public PartialList<Metadata> getScoringMetadatas(Query query) {
        return getMetadatas(query, Scoring.class);
    }

    private List<Scoring> getAllScoringDefinitions() {
        List<Scoring> allItems = persistenceService.getAllItems(Scoring.class);
        for (Scoring scoring : allItems) {
            if (scoring.getMetadata().isEnabled()) {
                for (ScoringElement element : scoring.getElements()) {
                    ParserHelper.resolveConditionType(definitionsService, element.getCondition(), "scoring " + scoring.getItemId());
                }
            }
        }
        return allItems;
    }

    public Scoring getScoringDefinition(String scoringId) {
        Scoring definition = persistenceService.load(scoringId, Scoring.class);
        if (definition != null && definition.getMetadata().isEnabled()) {
            for (ScoringElement element : definition.getElements()) {
                ParserHelper.resolveConditionType(definitionsService, element.getCondition(), "scoring " + scoringId);
            }
        }
        return definition;
    }

    public void setScoringDefinition(Scoring scoring) {
        if (scoring.getMetadata().isEnabled()) {
            for (ScoringElement element : scoring.getElements()) {
                ParserHelper.resolveConditionType(definitionsService, element.getCondition(), "scoring " + scoring.getItemId() + " element ");
                if (!scoring.getMetadata().isMissingPlugins()) {
                    updateAutoGeneratedRules(scoring.getMetadata(), element.getCondition());
                }
            }
        }
        // make sure we update the name and description metadata that might not match, so first we remove the entry from the map
        persistenceService.save(scoring);

        persistenceService.createMapping(Profile.ITEM_TYPE, String.format(
                "{\n" +
                        "  \"properties\": {\n" +
                        "    \"scores\": {\n" +
                        "      \"properties\": {\n" +
                        "        \"%s\": {\n" +
                        "          \"type\":\"long\"\n" +
                        "        }\n" +
                        "      }\n" +
                        "    }\n" +
                        "  }\n" +
                        "}", scoring.getItemId()));

        updateExistingProfilesForScoring(scoring.getItemId(), scoring.getElements(), scoring.getMetadata().isEnabled());
    }

    public void createScoringDefinition(String scope, String scoringId, String name, String description) {
        Metadata metadata = new Metadata(scope, scoringId, name, description);
        Scoring scoring = new Scoring(metadata);
        Condition rootCondition = new Condition();
        rootCondition.setConditionType(definitionsService.getConditionType("booleanCondition"));
        rootCondition.setParameter("operator", "and");
        rootCondition.setParameter("subConditions", new ArrayList<Condition>());
        scoring.setElements(new ArrayList<ScoringElement>());

        setScoringDefinition(scoring);
    }

    private boolean checkScoringDeletionImpact(Condition condition, String scoringToDeleteId) {
        if (condition != null) {
            @SuppressWarnings("unchecked") final List<Condition> subConditions = (List<Condition>) condition.getParameter("subConditions");
            if (subConditions != null) {
                for (Condition subCondition : subConditions) {
                    if (checkScoringDeletionImpact(subCondition, scoringToDeleteId)) {
                        return true;
                    }
                }
            } else if ("scoringCondition".equals(condition.getConditionTypeId())) {
                if (scoringToDeleteId.equals(condition.getParameter("scoringPlanId"))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Return an updated condition that do not contain a condition on the scoringId anymore
     * it's remove the unnecessary boolean condition (if a condition is the only one of a boolean the boolean will be remove and the subcondition returned)
     * it's return null when there is no more condition after (if the condition passed was only a scoring condition on the scoringId)
     *
     * @param condition the condition to update
     * @param scoringId the scoring id to remove in the condition
     * @return updated condition
     */
    private Condition updateScoringDependentCondition(Condition condition, String scoringId) {
        if ("booleanCondition".equals(condition.getConditionTypeId())) {
            @SuppressWarnings("unchecked") final List<Condition> subConditions = (List<Condition>) condition.getParameter("subConditions");
            List<Condition> updatedSubConditions = new LinkedList<>();
            for (Condition subCondition : subConditions) {
                Condition updatedCondition = updateScoringDependentCondition(subCondition, scoringId);
                if (updatedCondition != null) {
                    updatedSubConditions.add(updatedCondition);
                }
            }
            if (!updatedSubConditions.isEmpty()) {
                if (updatedSubConditions.size() == 1) {
                    return updatedSubConditions.get(0);
                } else {
                    condition.setParameter("subConditions", updatedSubConditions);
                    return condition;
                }
            } else {
                return null;
            }
        } else if ("scoringCondition".equals(condition.getConditionTypeId())
                && scoringId.equals(condition.getParameter("scoringPlanId"))) {
            return null;
        }
        return condition;
    }

    private Set<Segment> getScoringDependentSegments(String scoringId) {
        Set<Segment> impactedSegments = new HashSet<>(this.allSegments.size());
        for (Segment segment : this.allSegments) {
            if (checkScoringDeletionImpact(segment.getCondition(), scoringId)) {
                impactedSegments.add(segment);
            }
        }
        return impactedSegments;
    }

    private Set<Scoring> getScoringDependentScorings(String scoringId) {
        Set<Scoring> impactedScoring = new HashSet<>(this.allScoring.size());
        for (Scoring scoring : this.allScoring) {
            for (ScoringElement element : scoring.getElements()) {
                if (checkScoringDeletionImpact(element.getCondition(), scoringId)) {
                    impactedScoring.add(scoring);
                    break;
                }
            }
        }
        return impactedScoring;
    }

    public DependentMetadata getScoringDependentMetadata(String scoringId) {
        List<Metadata> segments = new LinkedList<>();
        List<Metadata> scorings = new LinkedList<>();
        for (Segment definition : getScoringDependentSegments(scoringId)) {
            segments.add(definition.getMetadata());
        }
        for (Scoring definition : getScoringDependentScorings(scoringId)) {
            scorings.add(definition.getMetadata());
        }
        return new DependentMetadata(segments, scorings);
    }

    public DependentMetadata removeScoringDefinition(String scoringId, boolean validate) {
        Set<Segment> impactedSegments = getScoringDependentSegments(scoringId);
        Set<Scoring> impactedScorings = getScoringDependentScorings(scoringId);
        if (!validate || (impactedSegments.isEmpty() && impactedScorings.isEmpty())) {
            // update profiles
            updateExistingProfilesForScoring(scoringId, Collections.emptyList(), false);

            // update impacted segments
            for (Segment segment : impactedSegments) {
                Condition updatedCondition = updateScoringDependentCondition(segment.getCondition(), scoringId);
                segment.setCondition(updatedCondition);
                if (updatedCondition == null) {
                    clearAutoGeneratedRules(persistenceService.query("linkedItems", segment.getMetadata().getId(), null, Rule.class), segment.getMetadata().getId());
                    segment.getMetadata().setEnabled(false);
                }
                setSegmentDefinition(segment);
            }

            // update impacted scorings
            for (Scoring scoring : impactedScorings) {
                List<ScoringElement> updatedScoringElements = new ArrayList<>();
                for (ScoringElement scoringElement : scoring.getElements()) {
                    Condition updatedCondition = updateScoringDependentCondition(scoringElement.getCondition(), scoringId);
                    if (updatedCondition != null) {
                        scoringElement.setCondition(updatedCondition);
                        updatedScoringElements.add(scoringElement);
                    }
                }
                scoring.setElements(updatedScoringElements);
                if (updatedScoringElements.isEmpty()) {
                    clearAutoGeneratedRules(persistenceService.query("linkedItems", scoring.getMetadata().getId(), null, Rule.class), scoring.getMetadata().getId());
                    scoring.getMetadata().setEnabled(false);
                }
                setScoringDefinition(scoring);
            }

            persistenceService.remove(scoringId, Scoring.class);
            List<Rule> previousRules = persistenceService.query("linkedItems", scoringId, null, Rule.class);
            clearAutoGeneratedRules(previousRules, scoringId);
        }

        List<Metadata> segments = new LinkedList<>();
        List<Metadata> scorings = new LinkedList<>();
        for (Segment definition : impactedSegments) {
            segments.add(definition.getMetadata());
        }
        for (Scoring definition : impactedScorings) {
            scorings.add(definition.getMetadata());
        }
        return new DependentMetadata(segments, scorings);
    }

    public void updateAutoGeneratedRules(Metadata metadata, Condition condition) {
        List<Rule> previousRules = persistenceService.query("linkedItems", metadata.getId(), null, Rule.class);
        List<Rule> rules = new ArrayList<Rule>();
        if (condition != null) {
            getAutoGeneratedRules(metadata, condition, null, rules);
        }
        for (Rule rule : rules) {
            rulesService.setRule(rule);
        }
        previousRules.removeAll(rules);
        clearAutoGeneratedRules(previousRules, metadata.getId());
    }

    private void clearAutoGeneratedRules(List<Rule> rules, String idWithScope) {
        for (Rule previousRule : rules) {
            previousRule.getLinkedItems().removeAll(Collections.singleton(idWithScope));
            if (previousRule.getLinkedItems().isEmpty()) {
                // todo remove profile properties ?
                persistenceService.remove(previousRule.getItemId(), Rule.class);
            } else {
                persistenceService.update(previousRule, null, Rule.class, "linkedItems", previousRule.getLinkedItems());
            }
        }
    }

    private void getAutoGeneratedRules(Metadata metadata, Condition condition, Condition parentCondition, List<Rule> rules) {
        Set<String> tags = condition.getConditionType().getMetadata().getSystemTags();
        if (tags.contains("eventCondition") && !tags.contains("profileCondition")) {
            String key = getGeneratedPropertyKey(condition, parentCondition);
            if (key != null) {
                parentCondition.setParameter("generatedPropertyKey", key);
                Rule rule = rulesService.getRule(key);
                if (rule == null) {
                    rule = new Rule(new Metadata(metadata.getScope(), key, "Auto generated rule for " + metadata.getName(), ""));
                    rule.setCondition(condition);
                    rule.getMetadata().setHidden(true);
                    final Action action = new Action();
                    action.setActionType(definitionsService.getActionType("setEventOccurenceCountAction"));
                    action.setParameter("pastEventCondition", parentCondition);

                    rule.setActions(Arrays.asList(action));
                    rule.setLinkedItems(Arrays.asList(metadata.getId()));

                    // it's a new generated rules to keep track of the event count, we should update all the profile that match this past event
                    // it will update the count of event occurrence on the profile directly
                    recalculatePastEventOccurrencesOnProfiles(condition, parentCondition, true, false);
                } else if (!rule.getLinkedItems().contains(metadata.getId())) {
                    rule.getLinkedItems().add(metadata.getId());
                }
                rules.add(rule);
            }
        } else {
            Collection<Object> values = new ArrayList<>(condition.getParameterValues().values());
            for (Object parameterValue : values) {
                if (parameterValue instanceof Condition) {
                    getAutoGeneratedRules(metadata, (Condition) parameterValue, condition, rules);
                } else if (parameterValue instanceof Collection) {
                    for (Object subCondition : (Collection<?>) parameterValue) {
                        if (subCondition instanceof Condition) {
                            getAutoGeneratedRules(metadata, (Condition) subCondition, condition, rules);
                        }
                    }
                }
            }
        }
    }

    /**
     * This will recalculate the event counts on the profiles that match the given past event condition
     *
     * @param eventCondition                   the real condition
     * @param parentCondition                  the past event condition
     * @param forceRefresh                     will refresh the Profile index in case it's true
     * @param resetExistingProfilesNotMatching if true, will reset existing profiles having a count to 0, in case they do not have events matching anymore
     *                                         ("false" can be useful when you know that no existing profiles already exist because it's a new rule for example,
     *                                         in that case setting this to "false" allow to skip profiles queries and speedup this process.
     *                                         Otherwise use "true" here to be sure the count is reset to 0 on profiles that need to be reset)
     */
    private void recalculatePastEventOccurrencesOnProfiles(Condition eventCondition, Condition parentCondition,
                                                           boolean forceRefresh, boolean resetExistingProfilesNotMatching) {
        long t = System.currentTimeMillis();
        List<Condition> l = new ArrayList<Condition>();
        Condition andCondition = new Condition();
        andCondition.setConditionType(definitionsService.getConditionType("booleanCondition"));
        andCondition.setParameter("operator", "and");
        andCondition.setParameter("subConditions", l);

        l.add(eventCondition);

        Integer numberOfDays = (Integer) parentCondition.getParameter("numberOfDays");
        String fromDate = (String) parentCondition.getParameter("fromDate");
        String toDate = (String) parentCondition.getParameter("toDate");

        if (numberOfDays != null) {
            Condition numberOfDaysCondition = new Condition();
            numberOfDaysCondition.setConditionType(definitionsService.getConditionType("sessionPropertyCondition"));
            numberOfDaysCondition.setParameter("propertyName", "timeStamp");
            numberOfDaysCondition.setParameter("comparisonOperator", "greaterThan");
            numberOfDaysCondition.setParameter("propertyValue", "now-" + numberOfDays + "d");
            l.add(numberOfDaysCondition);
        }
        if (fromDate != null) {
            Condition startDateCondition = new Condition();
            startDateCondition.setConditionType(definitionsService.getConditionType("sessionPropertyCondition"));
            startDateCondition.setParameter("propertyName", "timeStamp");
            startDateCondition.setParameter("comparisonOperator", "greaterThanOrEqualTo");
            startDateCondition.setParameter("propertyValueDate", fromDate);
            l.add(startDateCondition);
        }
        if (toDate != null) {
            Condition endDateCondition = new Condition();
            endDateCondition.setConditionType(definitionsService.getConditionType("sessionPropertyCondition"));
            endDateCondition.setParameter("propertyName", "timeStamp");
            endDateCondition.setParameter("comparisonOperator", "lessThanOrEqualTo");
            endDateCondition.setParameter("propertyValueDate", toDate);
            l.add(endDateCondition);
        }

        String propertyKey = (String) parentCondition.getParameter("generatedPropertyKey");
        Set<String> existingProfilesWithCounts = resetExistingProfilesNotMatching ? getExistingProfilesWithPastEventOccurrenceCount(propertyKey) : Collections.emptySet();

        int updatedProfileCount = 0;
        if (pastEventsDisablePartitions) {
            Map<String, Long> eventCountByProfile = persistenceService.aggregateWithOptimizedQuery(eventCondition, new TermsAggregate("profileId"), Event.ITEM_TYPE, maximumIdsQueryCount);
            Set<String> updatedProfiles = updatePastEventOccurrencesOnProfiles(eventCountByProfile, propertyKey);
            existingProfilesWithCounts.removeAll(updatedProfiles);
            updatedProfileCount = updatedProfiles.size();
        } else {
            Map<String, Double> m = persistenceService.getSingleValuesMetrics(andCondition, new String[]{"card"}, "profileId.keyword", Event.ITEM_TYPE);
            long card = m.get("_card").longValue();
            int numParts = (int) (card / aggregateQueryBucketSize) + 2;
            for (int i = 0; i < numParts; i++) {
                Map<String, Long> eventCountByProfile = persistenceService.aggregateWithOptimizedQuery(andCondition, new TermsAggregate("profileId", i, numParts), Event.ITEM_TYPE);
                Set<String> updatedProfiles = updatePastEventOccurrencesOnProfiles(eventCountByProfile, propertyKey);
                existingProfilesWithCounts.removeAll(updatedProfiles);
                updatedProfileCount += updatedProfiles.size();
            }
        }

        // remaining existing profiles with counts should be reset to 0 since they have not been updated it means
        // that they do not have matching events anymore in the time based condition
        if (!existingProfilesWithCounts.isEmpty()) {
            updatedProfileCount += updatePastEventOccurrencesOnProfiles(
                    existingProfilesWithCounts.stream().collect(Collectors.toMap(key -> key, value -> 0L)), propertyKey).size();
        }

        if (forceRefresh && updatedProfileCount > 0) {
            persistenceService.refreshIndex(Profile.class, null);
        }

        logger.info("{} profiles updated for past event condition in {}ms", updatedProfileCount, System.currentTimeMillis() - t);
    }

    /**
     * Return the list of profile ids, for profiles that already have an event count matching the generated property key
     *
     * @param generatedPropertyKey the generated property key of the generated rule for the given past event condition.
     * @return the list of profile ids.
     */
    private Set<String> getExistingProfilesWithPastEventOccurrenceCount(String generatedPropertyKey) {
        Condition countExistsCondition = new Condition();
        countExistsCondition.setConditionType(definitionsService.getConditionType("profilePropertyCondition"));
        countExistsCondition.setParameter("propertyName", "systemProperties.pastEvents." + generatedPropertyKey);
        countExistsCondition.setParameter("comparisonOperator", "greaterThan");
        countExistsCondition.setParameter("propertyValueInteger", 0);

        Set<String> profileIds = new HashSet<>();
        if (pastEventsDisablePartitions) {
            profileIds.addAll(persistenceService.aggregateWithOptimizedQuery(countExistsCondition, new TermsAggregate("itemId"),
                    Profile.ITEM_TYPE, maximumIdsQueryCount).keySet());
        } else {
            Map<String, Double> m = persistenceService.getSingleValuesMetrics(countExistsCondition, new String[]{"card"}, "itemId.keyword", Profile.ITEM_TYPE);
            long card = m.get("_card").longValue();
            int numParts = (int) (card / aggregateQueryBucketSize) + 2;
            for (int i = 0; i < numParts; i++) {
                profileIds.addAll(persistenceService.aggregateWithOptimizedQuery(countExistsCondition, new TermsAggregate("itemId", i, numParts),
                        Profile.ITEM_TYPE).keySet());
            }
        }
        return profileIds;
    }

    public String getGeneratedPropertyKey(Condition condition, Condition parentCondition) {
        try {
            Map<String, Object> m = new HashMap<>();
            m.put("condition", condition);
            m.put("numberOfDays", parentCondition.getParameter("numberOfDays"));
            // Put fromDate and toDate only if exist - for backward compatibility
            Object fromDate = parentCondition.getParameter("fromDate");
            if (fromDate != null) {
                m.put("fromDate", parentCondition.getParameter("fromDate"));
            }
            Object toDate = parentCondition.getParameter("toDate");
            if (toDate != null) {
                m.put("fromDate", parentCondition.getParameter("toDate"));
            }

            String key = CustomObjectMapper.getObjectMapper().writeValueAsString(m);
            return "eventTriggered" + getMD5(key);
        } catch (JsonProcessingException e) {
            logger.error("Cannot generate key", e);
            return null;
        }
    }

    @Override
    public void recalculatePastEventConditions() {
        Set<String> segmentOrScoringIdsToReevaluate = new HashSet<>();
        // reevaluate auto generated rules used to store the event occurrence count on the profile
        for (Rule rule : rulesService.getAllRules()) {
            if (rule.getActions() != null && rule.getActions().size() > 0) {
                for (Action action : rule.getActions()) {
                    if (action.getActionTypeId().equals("setEventOccurenceCountAction")) {
                        Condition pastEventCondition = (Condition) action.getParameterValues().get("pastEventCondition");
                        if (pastEventCondition.containsParameter("numberOfDays")) {
                            recalculatePastEventOccurrencesOnProfiles(rule.getCondition(), pastEventCondition, true, true);
                            logger.info("Event occurrence count on profiles updated for rule: {}", rule.getItemId());
                            if (rule.getLinkedItems() != null && rule.getLinkedItems().size() > 0) {
                                segmentOrScoringIdsToReevaluate.addAll(rule.getLinkedItems());
                            }
                        }
                    }
                }
            }
        }
        int pastEventSegmentsAndScoringsSize = segmentOrScoringIdsToReevaluate.size();
        logger.info("Found {} segments or scoring plans containing pastEventCondition conditions", pastEventSegmentsAndScoringsSize);

        // get Segments and Scoring that contains relative date expressions
        segmentOrScoringIdsToReevaluate.addAll(allSegments.stream()
                .filter(segment -> segment.getCondition() != null && segment.getCondition().toString().contains("propertyValueDateExpr"))
                .map(Item::getItemId)
                .collect(Collectors.toList()));

        segmentOrScoringIdsToReevaluate.addAll(allScoring.stream()
                .filter(scoring -> scoring.getElements() != null && scoring.getElements().size() > 0 && scoring.getElements().stream()
                        .anyMatch(scoringElement -> scoringElement != null && scoringElement.getCondition() != null && scoringElement.getCondition().toString().contains("propertyValueDateExpr")))
                .map(Item::getItemId)
                .collect(Collectors.toList()));
        logger.info("Found {} segments or scoring plans containing date relative expressions", segmentOrScoringIdsToReevaluate.size() - pastEventSegmentsAndScoringsSize);

        // reevaluate segments and scoring.
        if (segmentOrScoringIdsToReevaluate.size() > 0) {
            persistenceService.refreshIndex(Profile.class, null);
            for (String linkedItem : segmentOrScoringIdsToReevaluate) {
                Segment linkedSegment = getSegmentDefinition(linkedItem);
                if (linkedSegment != null) {
                    logger.info("Start segment recalculation for segment: {} - {}", linkedSegment.getItemId(), linkedSegment.getMetadata().getName());
                    updateExistingProfilesForSegment(linkedSegment);
                    continue;
                }

                Scoring linkedScoring = getScoringDefinition(linkedItem);
                if (linkedScoring != null) {
                    logger.info("Start scoring plan recalculation for scoring plan: {} - {}", linkedScoring.getItemId(), linkedScoring.getMetadata().getName());
                    updateExistingProfilesForScoring(linkedScoring.getItemId(), linkedScoring.getElements(), linkedScoring.getMetadata().isEnabled());
                }
            }
        }
    }

    /**
     * This will update all the profiles in the given map with the according new count occurrence for the given propertyKey
     *
     * @param eventCountByProfile the events count per profileId map
     * @param propertyKey         the generate property key for this past event condition, to keep track of the count in the profile
     * @return the list of profiles for witch the count of event occurrences have been updated.
     */
    private Set<String> updatePastEventOccurrencesOnProfiles(Map<String, Long> eventCountByProfile, String propertyKey) {
        Set<String> profilesUpdated = new HashSet<>();
        Map<Item, Map> batch = new HashMap<>();
        Iterator<Map.Entry<String, Long>> entryIterator = eventCountByProfile.entrySet().iterator();
        while (entryIterator.hasNext()) {
            Map.Entry<String, Long> entry = entryIterator.next();
            String profileId = entry.getKey();
            if (!profileId.startsWith("_")) {
                Map<String, Long> pastEventCounts = new HashMap<>();
                pastEventCounts.put(propertyKey, entry.getValue());
                Map<String, Object> systemProperties = new HashMap<>();
                systemProperties.put("pastEvents", pastEventCounts);
                systemProperties.put("lastUpdated", new Date());

                Profile profile = new Profile();
                profile.setItemId(profileId);
                batch.put(profile, Collections.singletonMap("systemProperties", systemProperties));
                profilesUpdated.add(profileId);
            }

            if (batch.size() == segmentUpdateBatchSize || (!entryIterator.hasNext() && batch.size() > 0)) {
                try {
                    persistenceService.update(batch, null, Profile.class);
                } catch (Exception e) {
                    logger.error("Error updating {} profiles for past event system properties", batch.size(), e);
                } finally {
                    batch.clear();
                }
            }
        }
        return profilesUpdated;
    }

    private String getMD5(String md5) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] array = md.digest(md5.getBytes());
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < array.length; ++i) {
                sb.append(Integer.toHexString((array[i] & 0xFF) | 0x100).substring(1, 3));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private void updateExistingProfilesForSegment(Segment segment) {
        long updateProfilesForSegmentStartTime = System.currentTimeMillis();
        long updatedProfileCount = 0;
        final String segmentId = segment.getItemId();

        Condition segmentCondition = new Condition();
        segmentCondition.setConditionType(definitionsService.getConditionType("profilePropertyCondition"));
        segmentCondition.setParameter("propertyName", "segments");
        segmentCondition.setParameter("comparisonOperator", "equals");
        segmentCondition.setParameter("propertyValue", segmentId);

        if (segment.getMetadata().isEnabled()) {

            ConditionType booleanConditionType = definitionsService.getConditionType("booleanCondition");
            ConditionType notConditionType = definitionsService.getConditionType("notCondition");

            Condition profilesToAddCondition = new Condition(booleanConditionType);
            profilesToAddCondition.setParameter("operator", "and");
            List<Condition> profilesToAddSubConditions = new ArrayList<>();
            profilesToAddSubConditions.add(segment.getCondition());
            Condition notOldSegmentCondition = new Condition(notConditionType);
            notOldSegmentCondition.setParameter("subCondition", segmentCondition);
            profilesToAddSubConditions.add(notOldSegmentCondition);
            profilesToAddCondition.setParameter("subConditions", profilesToAddSubConditions);

            Condition profilesToRemoveCondition = new Condition(booleanConditionType);
            profilesToRemoveCondition.setParameter("operator", "and");
            List<Condition> profilesToRemoveSubConditions = new ArrayList<>();
            profilesToRemoveSubConditions.add(segmentCondition);
            Condition notNewSegmentCondition = new Condition(notConditionType);
            notNewSegmentCondition.setParameter("subCondition", segment.getCondition());
            profilesToRemoveSubConditions.add(notNewSegmentCondition);
            profilesToRemoveCondition.setParameter("subConditions", profilesToRemoveSubConditions);

            updatedProfileCount += updateProfilesSegment(profilesToAddCondition, segmentId, true, sendProfileUpdateEventForSegmentUpdate);
            updatedProfileCount += updateProfilesSegment(profilesToRemoveCondition, segmentId, false, sendProfileUpdateEventForSegmentUpdate);
        } else {
            updatedProfileCount += updateProfilesSegment(segmentCondition, segmentId, false, sendProfileUpdateEventForSegmentUpdate);
        }
        logger.info("{} profiles updated in {}ms", updatedProfileCount, System.currentTimeMillis() - updateProfilesForSegmentStartTime);
    }

    private long updateProfilesSegment(Condition profilesToUpdateCondition, String segmentId, boolean isAdd, boolean sendProfileUpdateEvent) {
        long updatedProfileCount = 0;
        PartialList<Profile> profiles = persistenceService.query(profilesToUpdateCondition, null, Profile.class, 0, segmentUpdateBatchSize, "10m");

        while (profiles != null && profiles.getList().size() > 0) {
            long startTime = System.currentTimeMillis();
            if (batchSegmentProfileUpdate) {
                batchUpdateProfilesSegment(segmentId, profiles.getList(), isAdd);
            } else { //send update profile one by one
                for (Profile profileToUpdate : profiles.getList()) {
                    Map<String, Object> sourceMap = buildPropertiesMapForUpdateSegment(profileToUpdate, segmentId, isAdd);
                    persistenceService.update(profileToUpdate, null, Profile.class, sourceMap);
                }
            }
            if (sendProfileUpdateEvent)
                sendProfileUpdatedEvent(profiles.getList());

            updatedProfileCount += profiles.size();
            logger.info("{} profiles {} to segment {} in {}ms", profiles.size(), isAdd ? "added" : "removed", segmentId, System.currentTimeMillis() - startTime);

            profiles = persistenceService.continueScrollQuery(Profile.class, profiles.getScrollIdentifier(), profiles.getScrollTimeValidity());
        }

        return updatedProfileCount;
    }

    private void batchUpdateProfilesSegment(String segmentId, List<Profile> profiles, boolean isAdd) {
        Map<Item, Map> profileToPropertiesMap = new HashMap<>();
        for (Profile profileToUpdate : profiles) {
            Map<String, Object> propertiesToUpdate = buildPropertiesMapForUpdateSegment(profileToUpdate, segmentId, isAdd);
            profileToPropertiesMap.put(profileToUpdate, propertiesToUpdate);
        }
        List<String> failedItemsIds = persistenceService.update(profileToPropertiesMap, null, Profile.class);
        if (failedItemsIds != null)
            failedItemsIds.forEach(s -> retryFailedSegmentUpdate(s, segmentId, isAdd));
    }

    private void retryFailedSegmentUpdate(String profileId, String segmentId, boolean isAdd) {
        if (maxRetriesForUpdateProfileSegment > 0) {
            RetryPolicy retryPolicy = new RetryPolicy()
                    .withDelay(Duration.ofSeconds(secondsDelayForRetryUpdateProfileSegment))
                    .withMaxRetries(maxRetriesForUpdateProfileSegment);

            Failsafe.with(retryPolicy).
                    run(executionContext -> {
                        logger.warn("retry updating profile segment {}, profile {}, time {}", segmentId, profileId, new Date());
                        Profile profileToAddUpdated = persistenceService.load(profileId, Profile.class);
                        Map<String, Object> sourceMapToUpdate = buildPropertiesMapForUpdateSegment(profileToAddUpdated, segmentId, isAdd);
                        boolean isUpdated = persistenceService.update(profileToAddUpdated, null, Profile.class, sourceMapToUpdate);
                        if (isUpdated == false)
                            throw new Exception(String.format("failed retry update profile segment {}, profile {}, time {}", segmentId, profileId, new Date()));
                    });
        }
    }

    private void sendProfileUpdatedEvent(List<Profile> profiles) {
        for (Profile profileToAdd : profiles) {
            sendProfileUpdatedEvent(profileToAdd);
        }
    }

    private void sendProfileUpdatedEvent(Profile profile) {
        Event profileUpdated = new Event("profileUpdated", null, profile, null, null, profile, new Date());
        profileUpdated.setPersistent(false);
        eventService.send(profileUpdated);
    }

    private Map<String, Object> buildPropertiesMapForUpdateSegment(Profile profile, String segmentId, boolean isAdd) {
        if (isAdd)
            profile.getSegments().add(segmentId);
        else
            profile.getSegments().remove(segmentId);

        Map<String, Object> sourceMap = new HashMap<>();
        sourceMap.put("segments", profile.getSegments());
        profile.setSystemProperty("lastUpdated", new Date());
        sourceMap.put("systemProperties", profile.getSystemProperties());
        return sourceMap;
    }

    private void updateExistingProfilesForScoring(String scoringId, List<ScoringElement> scoringElements, boolean isEnabled) {
        long startTime = System.currentTimeMillis();

        String[] scripts = new String[scoringElements.size() + 1];
        Map<String, Object>[] scriptParams = new HashMap[scoringElements.size() + 1];
        Condition[] conditions = new Condition[scoringElements.size() + 1];

        // reset Score
        scriptParams[0] = new HashMap<>();
        scriptParams[0].put("scoringId", scoringId);
        scripts[0] = RESET_SCORING_SCRIPT;
        conditions[0] = new Condition();
        conditions[0].setConditionType(definitionsService.getConditionType("profilePropertyCondition"));
        conditions[0].setParameter("propertyName", "scores." + scoringId);
        conditions[0].setParameter("comparisonOperator", "exists");

        // evaluate each elements of the scoring
        if (isEnabled) {
            int idx = 1;
            for (ScoringElement element : scoringElements) {
                scriptParams[idx] = new HashMap<>();
                scriptParams[idx].put("scoringId", scoringId);
                scriptParams[idx].put("scoringValue", element.getValue());
                scripts[idx] = EVALUATE_SCORING_ELEMENT_SCRIPT;
                conditions[idx] = element.getCondition();
                idx++;
            }
        }
        persistenceService.updateWithQueryAndStoredScript(null, Profile.class, scripts, scriptParams, conditions);
        logger.info("Updated scoring for profiles in {}ms", System.currentTimeMillis() - startTime);
    }

    public void bundleChanged(BundleEvent event) {
        switch (event.getType()) {
            case BundleEvent.STARTED:
                processBundleStartup(event.getBundle().getBundleContext());
                break;
            case BundleEvent.STOPPING:
                processBundleStop(event.getBundle().getBundleContext());
                break;
        }
    }

    private void initializeTimer() {

        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                try {
                    long currentTimeMillis = System.currentTimeMillis();
                    logger.info("running scheduled task to recalculate segments and scoring that contains date relative conditions");
                    recalculatePastEventConditions();
                    logger.info("finished recalculate segments and scoring that contains date relative conditions in {}ms. ", System.currentTimeMillis() - currentTimeMillis);
                } catch (Throwable t) {
                    logger.error("Error while updating profiles for segments and scoring that contains date relative conditions", t);
                }
            }
        };
        long initialDelay = SchedulerServiceImpl.getTimeDiffInSeconds(dailyDateExprEvaluationHourUtc, ZonedDateTime.now(ZoneOffset.UTC));
        logger.info("daily recalculation job for segments and scoring that contains date relative conditions will run at fixed rate, initialDelay={}, taskExecutionPeriod={}", initialDelay, TimeUnit.DAYS.toSeconds(1));
        schedulerService.getScheduleExecutorService().scheduleAtFixedRate(task, initialDelay, taskExecutionPeriod, TimeUnit.DAYS);

        task = new TimerTask() {
            @Override
            public void run() {
                try {
                    allSegments = getAllSegmentDefinitions();
                    allScoring = getAllScoringDefinitions();
                } catch (Throwable t) {
                    logger.error("Error while loading segments and scoring definitions from persistence back-end", t);
                }
            }
        };
        schedulerService.getScheduleExecutorService().scheduleAtFixedRate(task, 0, segmentRefreshInterval, TimeUnit.MILLISECONDS);
    }

    private void loadScripts() throws IOException {
        Enumeration<URL> scriptsURL = bundleContext.getBundle().findEntries("META-INF/cxs/painless", "*.painless", true);
        if (scriptsURL == null) {
            return;
        }

        Map<String, String> scriptsById = new HashMap<>();
        while (scriptsURL.hasMoreElements()) {
            URL scriptURL = scriptsURL.nextElement();
            logger.debug("Found painless script at " + scriptURL + ", loading... ");
            try (InputStream in = scriptURL.openStream()) {
                String script = IOUtils.toString(in, StandardCharsets.UTF_8);
                String scriptId = FilenameUtils.getBaseName(scriptURL.getPath());
                scriptsById.put(scriptId, script);
            }
        }
        persistenceService.storeScripts(scriptsById);
    }

    public void setTaskExecutionPeriod(long taskExecutionPeriod) {
        this.taskExecutionPeriod = taskExecutionPeriod;
    }
}
