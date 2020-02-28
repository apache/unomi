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
import org.apache.unomi.api.Event;
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
import org.apache.unomi.services.impl.ParserHelper;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.SynchronousBundleListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class SegmentServiceImpl extends AbstractServiceImpl implements SegmentService, SynchronousBundleListener {

    private static final Logger logger = LoggerFactory.getLogger(SegmentServiceImpl.class.getName());

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

    public void setSegmentRefreshInterval(long segmentRefreshInterval) {
        this.segmentRefreshInterval = segmentRefreshInterval;
    }

    public void postConstruct() {
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
                // Register only if segment does not exist yet
                if (getSegmentDefinition(segment.getMetadata().getId()) == null) {
                    setSegmentDefinition(segment);
                    logger.info("Predefined segment with id {} registered", segment.getMetadata().getId());
                } else {
                    logger.info("The predefined segment with id {} is already registered, this segment will be skipped", segment.getMetadata().getId());
                }
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
                // Register only if scoring plan does not exist yet
                if (getScoringDefinition(scoring.getMetadata().getId()) == null) {
                    setScoringDefinition(scoring);
                    logger.info("Predefined scoring with id {} registered", scoring.getMetadata().getId());
                } else {
                    logger.info("The predefined scoring with id {} is already registered, this scoring will be skipped", scoring.getMetadata().getId());
                }
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
            ParserHelper.resolveConditionType(definitionsService, segment.getCondition());
        }
        return allItems;
    }

    public Segment getSegmentDefinition(String segmentId) {
        Segment definition = persistenceService.load(segmentId, Segment.class);
        if (definition != null) {
            ParserHelper.resolveConditionType(definitionsService, definition.getCondition());
        }
        return definition;
    }

    public void setSegmentDefinition(Segment segment) {
        ParserHelper.resolveConditionType(definitionsService, segment.getCondition());
        if (segment.getMetadata().isEnabled() && !segment.getMetadata().isMissingPlugins()) {
            updateAutoGeneratedRules(segment.getMetadata(), segment.getCondition());
        }
        // make sure we update the name and description metadata that might not match, so first we remove the entry from the map
        persistenceService.save(segment);

        updateExistingProfilesForSegment(segment);
    }

    private boolean checkSegmentDeletionImpact(Condition condition, String segmentToDeleteId) {
        if (condition != null) {
            @SuppressWarnings("unchecked")
            final List<Condition> subConditions = (List<Condition>) condition.getParameter("subConditions");
            if (subConditions != null) {
                for (Condition subCondition : subConditions) {
                    if (checkSegmentDeletionImpact(subCondition, segmentToDeleteId)) {
                        return true;
                    }
                }
            } else if ("profileSegmentCondition".equals(condition.getConditionTypeId())) {
                @SuppressWarnings("unchecked")
                final List<String> referencedSegmentIds = (List<String>) condition.getParameter("segments");

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
            @SuppressWarnings("unchecked")
            final List<Condition> subConditions = (List<Condition>) condition.getParameter("subConditions");
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
            @SuppressWarnings("unchecked")
            final List<String> referencedSegmentIds = (List<String>) condition.getParameter("segments");
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

            List<Profile> previousProfiles = persistenceService.query(segmentCondition, null, Profile.class);
            long updatedProfileCount = 0;
            long profileRemovalStartTime = System.currentTimeMillis();
            for (Profile profileToRemove : previousProfiles) {
                profileToRemove.getSegments().remove(segmentId);
                Map<String,Object> sourceMap = new HashMap<>();
                sourceMap.put("segments", profileToRemove.getSegments());
                profileToRemove.setSystemProperty("lastUpdated", new Date());
                sourceMap.put("systemProperties", profileToRemove.getSystemProperties());
                persistenceService.update(profileToRemove.getItemId(), null, Profile.class, sourceMap);
                updatedProfileCount++;
            }
            logger.info("Removed segment from {} profiles in {} ms", updatedProfileCount, System.currentTimeMillis() - profileRemovalStartTime);

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
            if (persistenceService.testMatch(segment.getCondition(), profile)) {
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
            for (ScoringElement element : scoring.getElements()) {
                ParserHelper.resolveConditionType(definitionsService, element.getCondition());
            }
        }
        return allItems;
    }

    public Scoring getScoringDefinition(String scoringId) {
        Scoring definition = persistenceService.load(scoringId, Scoring.class);
        if (definition != null) {
            for (ScoringElement element : definition.getElements()) {
                ParserHelper.resolveConditionType(definitionsService, element.getCondition());
            }
        }
        return definition;
    }

    public void setScoringDefinition(Scoring scoring) {
        for (ScoringElement element : scoring.getElements()) {
            ParserHelper.resolveConditionType(definitionsService, element.getCondition());
        }
        for (ScoringElement element : scoring.getElements()) {
            if (scoring.getMetadata().isEnabled() && !scoring.getMetadata().isMissingPlugins()) {
                updateAutoGeneratedRules(scoring.getMetadata(), element.getCondition());
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

        updateExistingProfilesForScoring(scoring);
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
            @SuppressWarnings("unchecked")
            final List<Condition> subConditions = (List<Condition>) condition.getParameter("subConditions");
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
            @SuppressWarnings("unchecked")
            final List<Condition> subConditions = (List<Condition>) condition.getParameter("subConditions");
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
            updateExistingProfilesForRemovedScoring(scoringId);

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
            previousRule.getLinkedItems().remove(idWithScope);
            if (previousRule.getLinkedItems().isEmpty()) {
                // todo remove profile properties ?
                persistenceService.remove(previousRule.getItemId(), Rule.class);
            } else {
                persistenceService.update(previousRule.getItemId(), null, Rule.class, "linkedItems", previousRule.getLinkedItems());
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
                    rules.add(rule);

                    updateExistingProfilesForPastEventCondition(condition, parentCondition);
                } else {
                    rule.getLinkedItems().add(metadata.getId());
                    rules.add(rule);
                }
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

    private void updateExistingProfilesForPastEventCondition(Condition eventCondition, Condition parentCondition) {
        long t = System.currentTimeMillis();
        List<Condition> l = new ArrayList<Condition>();
        Condition andCondition = new Condition();
        andCondition.setConditionType(definitionsService.getConditionType("booleanCondition"));
        andCondition.setParameter("operator", "and");
        andCondition.setParameter("subConditions", l);

        l.add(eventCondition);

        Integer numberOfDays = (Integer) parentCondition.getParameter("numberOfDays");
        if (numberOfDays != null) {
            Condition numberOfDaysCondition = new Condition();
            numberOfDaysCondition.setConditionType(definitionsService.getConditionType("sessionPropertyCondition"));
            numberOfDaysCondition.setParameter("propertyName", "timeStamp");
            numberOfDaysCondition.setParameter("comparisonOperator", "greaterThan");
            numberOfDaysCondition.setParameter("propertyValue", "now-" + numberOfDays + "d");
            l.add(numberOfDaysCondition);
        }
        String propertyKey = (String) parentCondition.getParameter("generatedPropertyKey");

        Map<String, Double> m = persistenceService.getSingleValuesMetrics(andCondition, new String[]{"card"}, "profileId.keyword", Event.ITEM_TYPE);
        long card = m.get("_card").longValue();

        int numParts = (int) (card / aggregateQueryBucketSize) + 2;
        for (int i = 0; i < numParts; i++) {
            Map<String, Long> eventCountByProfile = persistenceService.aggregateWithOptimizedQuery(andCondition, new TermsAggregate("profileId", i, numParts), Event.ITEM_TYPE);
            for (Map.Entry<String, Long> entry : eventCountByProfile.entrySet()) {
                String profileId = entry.getKey();
                if (!profileId.startsWith("_")) {
                    Map<String, Long> pastEventCounts = new HashMap<>();
                    pastEventCounts.put(propertyKey, entry.getValue());
                    Map<String, Object> systemProperties = new HashMap<>();
                    systemProperties.put("pastEvents", pastEventCounts);
                    try {
                        systemProperties.put("lastUpdated", new Date());
                        persistenceService.update(profileId, null, Profile.class, "systemProperties", systemProperties);
                    } catch (Exception e) {
                        logger.error("Error updating profile {} past event system properties", profileId, e);
                    }
                }
            }
        }

        logger.info("Profiles past condition updated in {}ms", System.currentTimeMillis() - t);
    }

    public String getGeneratedPropertyKey(Condition condition, Condition parentCondition) {
        try {
            Map<String, Object> m = new HashMap<>();
            m.put("condition", condition);
            m.put("numberOfDays", parentCondition.getParameter("numberOfDays"));
            String key = CustomObjectMapper.getObjectMapper().writeValueAsString(m);
            return "eventTriggered" + getMD5(key);
        } catch (JsonProcessingException e) {
            logger.error("Cannot generate key",e);
            return null;
        }
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
        Condition segmentCondition = new Condition();

        long updatedProfileCount = 0;

        segmentCondition.setConditionType(definitionsService.getConditionType("profilePropertyCondition"));
        segmentCondition.setParameter("propertyName", "segments");
        segmentCondition.setParameter("comparisonOperator", "equals");
        segmentCondition.setParameter("propertyValue", segment.getItemId());

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

            PartialList<Profile> profilesToRemove = persistenceService.query(profilesToRemoveCondition, null, Profile.class, 0, segmentUpdateBatchSize, "10m");
            PartialList<Profile> profilesToAdd = persistenceService.query(profilesToAddCondition, null, Profile.class, 0, segmentUpdateBatchSize, "10m");

            while (profilesToAdd.getList().size() > 0) {
                long profilesToAddStartTime = System.currentTimeMillis();
                for (Profile profileToAdd : profilesToAdd.getList()) {
                    profileToAdd.getSegments().add(segment.getItemId());
                    Map<String,Object> sourceMap = new HashMap<>();
                    sourceMap.put("segments", profileToAdd.getSegments());
                    profileToAdd.setSystemProperty("lastUpdated", new Date());
                    sourceMap.put("systemProperties", profileToAdd.getSystemProperties());
                    persistenceService.update(profileToAdd.getItemId(), null, Profile.class, sourceMap);
                    Event profileUpdated = new Event("profileUpdated", null, profileToAdd, null, null, profileToAdd, new Date());
                    profileUpdated.setPersistent(false);
                    eventService.send(profileUpdated);
                    updatedProfileCount++;
                }
                logger.info("{} profiles added to segment in {}ms", profilesToAdd.size(), System.currentTimeMillis() - profilesToAddStartTime);
                profilesToAdd = persistenceService.continueScrollQuery(Profile.class, profilesToAdd.getScrollIdentifier(), profilesToAdd.getScrollTimeValidity());
                if (profilesToAdd == null || profilesToAdd.getList().size() == 0) {
                    break;
                }
            }
            while (profilesToRemove.getList().size() > 0) {
                long profilesToRemoveStartTime = System.currentTimeMillis();
                for (Profile profileToRemove : profilesToRemove.getList()) {
                    profileToRemove.getSegments().remove(segment.getItemId());
                    Map<String,Object> sourceMap = new HashMap<>();
                    sourceMap.put("segments", profileToRemove.getSegments());
                    profileToRemove.setSystemProperty("lastUpdated", new Date());
                    sourceMap.put("systemProperties", profileToRemove.getSystemProperties());
                    persistenceService.update(profileToRemove.getItemId(), null, Profile.class, sourceMap);
                    Event profileUpdated = new Event("profileUpdated", null, profileToRemove, null, null, profileToRemove, new Date());
                    profileUpdated.setPersistent(false);
                    eventService.send(profileUpdated);
                    updatedProfileCount++;
                }
                logger.info("{} profiles removed from segment in {}ms", profilesToRemove.size(), System.currentTimeMillis() - profilesToRemoveStartTime );
                profilesToRemove = persistenceService.continueScrollQuery(Profile.class, profilesToRemove.getScrollIdentifier(), profilesToRemove.getScrollTimeValidity());
                if (profilesToRemove == null || profilesToRemove.getList().size() == 0) {
                    break;
                }
            }

        } else {
            PartialList<Profile> profilesToRemove = persistenceService.query(segmentCondition, null, Profile.class, 0, 200, "10m");
            while (profilesToRemove.getList().size() > 0) {
                long profilesToRemoveStartTime = System.currentTimeMillis();
                for (Profile profileToRemove : profilesToRemove.getList()) {
                    profileToRemove.getSegments().remove(segment.getItemId());
                    Map<String,Object> sourceMap = new HashMap<>();
                    sourceMap.put("segments", profileToRemove.getSegments());
                    profileToRemove.setSystemProperty("lastUpdated", new Date());
                    sourceMap.put("systemProperties", profileToRemove.getSystemProperties());
                    persistenceService.update(profileToRemove.getItemId(), null, Profile.class, sourceMap);
                    Event profileUpdated = new Event("profileUpdated", null, profileToRemove, null, null, profileToRemove, new Date());
                    profileUpdated.setPersistent(false);
                    eventService.send(profileUpdated);
                    updatedProfileCount++;
                }
                logger.info("{} profiles removed from segment in {}ms", profilesToRemove.size(), System.currentTimeMillis() - profilesToRemoveStartTime);
                profilesToRemove = persistenceService.continueScrollQuery(Profile.class, profilesToRemove.getScrollIdentifier(), profilesToRemove.getScrollTimeValidity());
                if (profilesToRemove == null || profilesToRemove.getList().size() == 0) {
                    break;
                }
            }
        }
        logger.info("{} profiles updated in {}ms", updatedProfileCount, System.currentTimeMillis() - updateProfilesForSegmentStartTime);
    }

    private void updateExistingProfilesForScoring(Scoring scoring) {
        long startTime = System.currentTimeMillis();
        Condition scoringCondition = new Condition();
        scoringCondition.setConditionType(definitionsService.getConditionType("profilePropertyCondition"));
        scoringCondition.setParameter("propertyName", "scores." + scoring.getItemId());
        scoringCondition.setParameter("comparisonOperator", "exists");

        String[] scripts = new String[scoring.getElements().size() + 1];
        HashMap<String, Object>[] scriptParams = new HashMap[scoring.getElements().size() + 1];
        Condition[] conditions = new Condition[scoring.getElements().size() + 1];

        String lastUpdatedScriptPart = " if (!ctx._source.containsKey(\"systemProperties\")) { ctx._source.put(\"systemProperties\", [:]) } ctx._source.systemProperties.put(\"lastUpdated\", ZonedDateTime.ofInstant(Instant.ofEpochMilli(System.currentTimeMillis()), ZoneId.of(\"Z\")))";

        scriptParams[0] = new HashMap<String, Object>();
        scriptParams[0].put("scoringId", scoring.getItemId());
        scripts[0] = "if (ctx._source.containsKey(\"systemProperties\") && ctx._source.systemProperties.containsKey(\"scoreModifiers\") && ctx._source.systemProperties.scoreModifiers.containsKey(params.scoringId) ) { ctx._source.scores.put(params.scoringId, ctx._source.systemProperties.scoreModifiers.get(params.scoringId)) } else { ctx._source.scores.remove(params.scoringId) } " +
                lastUpdatedScriptPart;
        conditions[0] = scoringCondition;

        if (scoring.getMetadata().isEnabled()) {
            String scriptToAdd = "if (!ctx._source.containsKey(\"scores\")) { ctx._source.put(\"scores\", [:])} if (ctx._source.scores.containsKey(params.scoringId) ) { ctx._source.scores.put(params.scoringId, ctx._source.scores.get(params.scoringId)+params.scoringValue) } else { ctx._source.scores.put(params.scoringId, params.scoringValue) } " +
                    lastUpdatedScriptPart;
            int idx = 1;
            for (ScoringElement element : scoring.getElements()) {
                scriptParams[idx] = new HashMap<>();
                scriptParams[idx].put("scoringId", scoring.getItemId());
                scriptParams[idx].put("scoringValue", element.getValue());
                scripts[idx] = scriptToAdd;
                conditions[idx] = element.getCondition();
                idx++;
            }
        }
        persistenceService.updateWithQueryAndScript(null, Profile.class, scripts, scriptParams, conditions);
        logger.info("Updated scoring for profiles in {}ms", System.currentTimeMillis() - startTime);
    }

    private void updateExistingProfilesForRemovedScoring(String scoringId) {
        long startTime = System.currentTimeMillis();
        Condition scoringCondition = new Condition();
        scoringCondition.setConditionType(definitionsService.getConditionType("profilePropertyCondition"));
        scoringCondition.setParameter("propertyName", "scores." + scoringId);
        scoringCondition.setParameter("comparisonOperator", "exists");
        Condition[] conditions = new Condition[1];
        conditions[0] = scoringCondition;

        HashMap<String, Object>[] scriptParams = new HashMap[1];
        scriptParams[0] = new HashMap<String, Object>();
        scriptParams[0].put("scoringId", scoringId);

        String[] script = new String[1];
        script[0] = "ctx._source.scores.remove(params.scoringId); if (!ctx._source.containsKey(\"systemProperties\")) { ctx._source.put(\"systemProperties\", [:]) } ctx._source.systemProperties.put(\"lastUpdated\", ZonedDateTime.ofInstant(Instant.ofEpochMilli(System.currentTimeMillis()), ZoneId.of(\"Z\")))";

        persistenceService.updateWithQueryAndScript(null, Profile.class, script, scriptParams, conditions);

        logger.info("Removed scoring from profiles in {}ms", System.currentTimeMillis() - startTime);
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
                    for (Metadata metadata : rulesService.getRuleMetadatas()) {
                        Rule rule = rulesService.getRule(metadata.getId());
                        for (Action action : rule.getActions()) {
                            if (action.getActionTypeId().equals("setEventOccurenceCountAction")) {
                                Condition pastEventCondition = (Condition) action.getParameterValues().get("pastEventCondition");
                                if (pastEventCondition.containsParameter("numberOfDays")) {
                                    updateExistingProfilesForPastEventCondition(rule.getCondition(), pastEventCondition);
                                }
                            }
                        }
                    }
                } catch (Throwable t) {
                    logger.error("Error while updating profiles for past event conditions", t);
                }
            }
        };
        schedulerService.getScheduleExecutorService().scheduleAtFixedRate(task, 1, taskExecutionPeriod, TimeUnit.DAYS);

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

    public void setTaskExecutionPeriod(long taskExecutionPeriod) {
        this.taskExecutionPeriod = taskExecutionPeriod;
    }

}
