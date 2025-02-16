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
import org.apache.unomi.api.*;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.exceptions.BadSegmentConditionException;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.rules.Rule;
import org.apache.unomi.api.segments.*;
import org.apache.unomi.api.services.*;
import org.apache.unomi.api.services.ConditionValidationService.ValidationError;
import org.apache.unomi.api.services.ConditionValidationService.ValidationErrorType;
import org.apache.unomi.api.services.cache.CacheableTypeConfig;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.api.utils.ConditionBuilder;
import org.apache.unomi.api.utils.ParserHelper;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.apache.unomi.persistence.spi.aggregate.TermsAggregate;
import org.apache.unomi.services.impl.cache.AbstractMultiTypeCachingService;
import org.apache.unomi.services.impl.scheduler.SchedulerServiceImpl;
import org.apache.unomi.tracing.api.RequestTracer;
import org.apache.unomi.tracing.api.TracerService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class SegmentServiceImpl extends AbstractMultiTypeCachingService implements SegmentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SegmentServiceImpl.class.getName());

    private static final String VALIDATION_PROFILE_ID = "validation-profile-id";
    private static final String RESET_SCORING_SCRIPT = "resetScoringPlan";
    private static final String EVALUATE_SCORING_ELEMENT_SCRIPT = "evaluateScoringPlanElement";

    private EventService eventService;
    private RulesService rulesService;
    private DefinitionsService definitionsService;
    private ConditionValidationService conditionValidationService;
    private TracerService tracerService;

    private long taskExecutionPeriod = 1;
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
        LOGGER.info("Initializing segment service...");
    }

    public void setEventService(EventService eventService) {
        this.eventService = eventService;
    }

    public void setRulesService(RulesService rulesService) {
        this.rulesService = rulesService;
    }

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    public void setConditionValidationService(ConditionValidationService conditionValidationService) {
        this.conditionValidationService = conditionValidationService;
    }

    public void setTracerService(TracerService tracerService) {
        this.tracerService = tracerService;
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

    public void setTenantService(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @Override
    protected Set<CacheableTypeConfig<?>> getTypeConfigs() {
        Set<CacheableTypeConfig<?>> configs = new HashSet<>();
        configs.add(new CacheableTypeConfig<>(
            Segment.class,
            Segment.ITEM_TYPE,
            "segments",
            true,
            true,
            1000L,
            segment -> segment.getMetadata().getId()
        ));
        configs.add(new CacheableTypeConfig<>(
            Scoring.class,
            "scoring",
            "scoring",
            true,
            true,
            1000L,
            scoring -> scoring.getMetadata().getId()
        ));
        return configs;
    }

    @Override
    public void postConstruct() {
        super.postConstruct();
        initializeTimer();
        LOGGER.info("Segment service initialized.");
    }

    public void preDestroy() {
        super.preDestroy();
        LOGGER.info("Segment service shutdown.");
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
        contextManager.executeAsSystem(() -> {
            Enumeration<URL> predefinedSegmentEntries = bundleContext.getBundle().findEntries("META-INF/cxs/segments", "*.json", true);
            if (predefinedSegmentEntries == null) {
                return;
            }

            while (predefinedSegmentEntries.hasMoreElements()) {
                URL predefinedSegmentURL = predefinedSegmentEntries.nextElement();
                LOGGER.debug("Found predefined segment at {}, loading... ", predefinedSegmentURL);

                try {
                    Segment segment = CustomObjectMapper.getObjectMapper().readValue(predefinedSegmentURL, Segment.class);
                    if (segment.getMetadata().getScope() == null) {
                        segment.getMetadata().setScope("systemscope");
                    }
                    setSegmentDefinition(segment);
                    LOGGER.info("Predefined segment with id {} registered", segment.getMetadata().getId());
                } catch (IOException e) {
                    LOGGER.error("Error while loading segment definition {}", predefinedSegmentURL, e);
                }
            }
        });
    }

    private void loadPredefinedScorings(BundleContext bundleContext) {
        contextManager.executeAsSystem(() -> {
            Enumeration<URL> predefinedScoringEntries = bundleContext.getBundle().findEntries("META-INF/cxs/scoring", "*.json", true);
            if (predefinedScoringEntries == null) {
                return;
            }

            while (predefinedScoringEntries.hasMoreElements()) {
                URL predefinedScoringURL = predefinedScoringEntries.nextElement();
                LOGGER.debug("Found predefined scoring at {}, loading... ", predefinedScoringURL);

                try {
                    Scoring scoring = CustomObjectMapper.getObjectMapper().readValue(predefinedScoringURL, Scoring.class);
                    if (scoring.getMetadata().getScope() == null) {
                        scoring.getMetadata().setScope("systemscope");
                    }
                    setScoringDefinition(scoring);
                    LOGGER.info("Predefined scoring with id {} registered", scoring.getMetadata().getId());
                } catch (IOException e) {
                    LOGGER.error("Error while loading segment definition {}", predefinedScoringURL, e);
                }
            }
        });
    }

    public PartialList<Metadata> getSegmentMetadatas(int offset, int size, String sortBy) {
        return getSegmentMetadatas(null, offset, size, sortBy);
    }

    public PartialList<Metadata> getSegmentMetadatas(String scope, int offset, int size, String sortBy) {
        String currentTenantId = contextManager.getCurrentContext().getTenantId();
        List<Metadata> details = new LinkedList<>();

        // Get system tenant segments first
        if (!TenantService.SYSTEM_TENANT.equals(currentTenantId)) {
            contextManager.executeAsSystem(() -> {
                Condition systemTenantCondition = new Condition(definitionsService.getConditionType("booleanCondition"));
                systemTenantCondition.setParameter("operator", "and");
                List<Condition> systemConditions = new ArrayList<>();

                Condition systemTenantCheck = new Condition(definitionsService.getConditionType("sessionPropertyCondition"));
                systemTenantCheck.setParameter("propertyName", "tenantId");
                systemTenantCheck.setParameter("comparisonOperator", "equals");
                systemTenantCheck.setParameter("propertyValue", TenantService.SYSTEM_TENANT);
                systemConditions.add(systemTenantCheck);

                if (scope != null) {
                    Condition systemScopeCheck = new Condition(definitionsService.getConditionType("sessionPropertyCondition"));
                    systemScopeCheck.setParameter("propertyName", "metadata.scope");
                    systemScopeCheck.setParameter("comparisonOperator", "equals");
                    systemScopeCheck.setParameter("propertyValue", scope);
                    systemConditions.add(systemScopeCheck);
                }

                systemTenantCondition.setParameter("subConditions", systemConditions);

                PartialList<Segment> systemSegments = persistenceService.query(systemTenantCondition, sortBy, Segment.class, 0, -1);
                for (Segment definition : systemSegments.getList()) {
                    details.add(definition.getMetadata());
                }
                return null;
            });
        }

        // Get current tenant segments (will override system segments with same ID)
        Condition tenantCondition = new Condition(definitionsService.getConditionType("booleanCondition"));
        tenantCondition.setParameter("operator", "and");
        List<Condition> conditions = new ArrayList<>();

        Condition tenantCheck = new Condition(definitionsService.getConditionType("sessionPropertyCondition"));
        tenantCheck.setParameter("propertyName", "tenantId");
        tenantCheck.setParameter("comparisonOperator", "equals");
        tenantCheck.setParameter("propertyValue", currentTenantId);
        conditions.add(tenantCheck);

        if (scope != null) {
            Condition scopeCheck = new Condition(definitionsService.getConditionType("sessionPropertyCondition"));
            scopeCheck.setParameter("propertyName", "metadata.scope");
            scopeCheck.setParameter("comparisonOperator", "equals");
            scopeCheck.setParameter("propertyValue", scope);
            conditions.add(scopeCheck);
        }

        tenantCondition.setParameter("subConditions", conditions);

        PartialList<Segment> segments = persistenceService.query(tenantCondition, sortBy, Segment.class, 0, -1);
        Map<String, Metadata> mergedDetails = new HashMap<>();

        // Add system tenant segments first
        for (Metadata metadata : details) {
            mergedDetails.put(metadata.getId(), metadata);
        }

        // Override with current tenant segments
        for (Segment definition : segments.getList()) {
            mergedDetails.put(definition.getMetadata().getId(), definition.getMetadata());
        }

        // Convert to list and apply pagination
        List<Metadata> finalDetails = new ArrayList<>(mergedDetails.values());
        if (sortBy != null) {
            // TODO: Implement sorting of merged results
        }

        int totalSize = finalDetails.size();
        int fromIndex = offset;
        int toIndex = offset + size;
        if (fromIndex >= totalSize) {
            return new PartialList<Metadata>(new ArrayList<>(), offset, size, totalSize, PartialList.Relation.EQUAL);
        }
        if (toIndex > totalSize) {
            toIndex = totalSize;
        }
        finalDetails = finalDetails.subList(fromIndex, toIndex);

        return new PartialList<Metadata>(finalDetails, offset, size, totalSize, PartialList.Relation.EQUAL);
    }

    public PartialList<Metadata> getSegmentMetadatas(Query query) {
        return getMetadatas(query, Segment.class);
    }

    @Override
    public Segment getSegmentDefinition(String segmentId) {
        String currentTenant = contextManager.getCurrentContext().getTenantId();
        Segment segment = cacheService.getWithInheritance(segmentId, currentTenant, Segment.class);
        if (segment != null && segment.getMetadata().isEnabled()) {
            ParserHelper.resolveConditionType(definitionsService, segment.getCondition(), "segment " + segmentId);
        }
        return segment;
    }

    @Override
    public void setSegmentDefinition(Segment segment) {
        if (segment == null) {
            throw new IllegalArgumentException("Segment cannot be null");
        }
        if (segment.getMetadata() == null) {
            throw new IllegalArgumentException("Segment metadata cannot be null");
        }

        if (segment.getCondition() != null) {
            // Start validation operation in tracer
            if (tracerService != null) {
                RequestTracer tracer = tracerService.getCurrentTracer();
                if (tracer != null && tracer.isEnabled()) {
                    tracer.startOperation("segment-condition-validation", "Validating segment condition: " + segment.getItemId(), segment.getCondition());
                }
            }

            List<ValidationError> validationErrors = conditionValidationService.validate(segment.getCondition());

            // Add validation info to tracer
            if (tracerService != null) {
                RequestTracer tracer = tracerService.getCurrentTracer();
                if (tracer != null && tracer.isEnabled()) {
                    tracer.addValidationInfo(validationErrors, "segment-condition-validation");
                    tracer.endOperation(!validationErrors.isEmpty(), String.format("Segment validation completed with %d errors", validationErrors.size()));
                }
            }

            // Separate errors and warnings
            List<ValidationError> errors = validationErrors.stream()
                .filter(error -> error.getType() != ValidationErrorType.MISSING_RECOMMENDED_PARAMETER)
                .collect(Collectors.toList());

            List<ValidationError> warnings = validationErrors.stream()
                .filter(error -> error.getType() == ValidationErrorType.MISSING_RECOMMENDED_PARAMETER)
                .collect(Collectors.toList());

            // Log warnings but don't block the operation
            if (!warnings.isEmpty()) {
                StringBuilder warningMessage = new StringBuilder("Segment condition has warnings:");
                for (ValidationError warning : warnings) {
                    warningMessage.append("\n- ").append(warning.getMessage());
                }
                LOGGER.warn(warningMessage.toString());
            }

            // Only throw exception for actual errors
            if (!errors.isEmpty()) {
                StringBuilder errorMessage = new StringBuilder("Invalid segment condition:");
                for (ValidationError error : errors) {
                    errorMessage.append("\n- ").append(error.getMessage());
                }
                throw new IllegalArgumentException(errorMessage.toString());
            }

            if (segment.getMetadata().isEnabled()) {
                ParserHelper.resolveConditionType(definitionsService, segment.getCondition(), "segment " + segment.getItemId());
                if (!persistenceService.isValidCondition(segment.getCondition(), new Profile(VALIDATION_PROFILE_ID))) {
                    throw new BadSegmentConditionException();
                }
            }
        }

        // Update auto-generated rules if metadata is enabled and no missing plugins
        if (segment.getMetadata().isEnabled() && !segment.getMetadata().isMissingPlugins()) {
            updateAutoGeneratedRules(segment.getMetadata(), segment.getCondition());
        }

        segment.setTenantId(contextManager.getCurrentContext().getTenantId());

        // Save segment and update cache
        persistenceService.save(segment, null, true);
        cacheService.put(Segment.ITEM_TYPE, segment.getItemId(), segment.getTenantId(), segment);
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
                return referencedSegmentIds.contains(segmentToDeleteId);
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
            if (referencedSegmentIds.contains(segmentId)) {
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
        String currentTenant = contextManager.getCurrentContext().getTenantId();
        Map<String, Segment> tenantSegments = cacheService.getTenantCache(currentTenant, Segment.class);
        Set<Segment> impactedSegments = new HashSet<>();
        if (tenantSegments != null) {
            for (Segment segment : tenantSegments.values()) {
                if (checkSegmentDeletionImpact(segment.getCondition(), segmentId)) {
                    impactedSegments.add(segment);
                }
            }
        }
        return impactedSegments;
    }

    private Set<Scoring> getSegmentDependentScorings(String segmentId) {
        String currentTenant = contextManager.getCurrentContext().getTenantId();
        Map<String, Scoring> tenantScoring = cacheService.getTenantCache(currentTenant, Scoring.class);
        Set<Scoring> impactedScorings = new HashSet<>();
        if (tenantScoring != null) {
            for (Scoring scoring : tenantScoring.values()) {
                if (checkSegmentDeletionImpact(scoring.getElements().get(0).getCondition(), segmentId)) {
                    impactedScorings.add(scoring);
                }
            }
        }
        return impactedScorings;
    }

    public DependentMetadata getSegmentDependentMetadata(String segmentId) {
        List<Metadata> segments = new ArrayList<>();
        List<Metadata> scorings = new ArrayList<>();

        String currentTenant = contextManager.getCurrentContext().getTenantId();
        Map<String, Segment> tenantSegments = cacheService.getTenantCache(currentTenant, Segment.class);
        Map<String, Scoring> tenantScoring = cacheService.getTenantCache(currentTenant, Scoring.class);

        if (tenantSegments != null) {
            for (Segment segment : tenantSegments.values()) {
                if (checkSegmentDeletionImpact(segment.getCondition(), segmentId)) {
                    segments.add(segment.getMetadata());
                }
            }
        }

        if (tenantScoring != null) {
            for (Scoring scoring : tenantScoring.values()) {
                if (checkSegmentDeletionImpact(scoring.getElements().get(0).getCondition(), segmentId)) {
                    scorings.add(scoring.getMetadata());
                }
            }
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
            cacheService.remove(Segment.ITEM_TYPE, segmentId, contextManager.getCurrentContext().getTenantId(), Segment.class);
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
        RequestTracer tracer = tracerService.getCurrentTracer();
        tracer.trace("Checking if profile is in segment: " + segmentId, profile.getItemId());
        Set<String> matchingSegments = getSegmentsAndScoresForProfile(profile).getSegments();
        boolean isInSegment = matchingSegments.contains(segmentId);
        tracer.trace("Profile " + profile.getItemId() + " is " + (isInSegment ? "in" : "not in") + " segment: " + segmentId, profile.getItemId());
        return isInSegment;
    }

    public SegmentsAndScores getSegmentsAndScoresForProfile(Profile profile) {
        RequestTracer tracer = tracerService.getCurrentTracer();
        tracer.trace("Getting segments and scores for profile: " + profile.getItemId(), profile.getItemId());
        Set<String> segments = new HashSet<String>();
        Map<String, Integer> scores = new HashMap<String, Integer>();

        String currentTenant = contextManager.getCurrentContext().getTenantId();
        tracer.trace("Current tenant: " + currentTenant, profile.getItemId());

        // Get system tenant segments and scoring first
        Map<String, Segment> systemSegments = cacheService.getTenantCache("system", Segment.class);
        Map<String, Scoring> systemScoring = cacheService.getTenantCache("system", Scoring.class);

        if (systemSegments != null) {
            for (Segment segment : systemSegments.values()) {
                if (segment.getMetadata().isEnabled() && persistenceService.testMatch(segment.getCondition(), profile)) {
                    segments.add(segment.getMetadata().getId());
                    tracer.trace("Profile matches system segment: " + segment.getMetadata().getId(), profile.getItemId());
                }
            }
        }

        // Get current tenant segments and scoring
        Map<String, Segment> tenantSegments = cacheService.getTenantCache(currentTenant, Segment.class);
        Map<String, Scoring> tenantScoring = cacheService.getTenantCache(currentTenant, Scoring.class);

        if (tenantSegments != null) {
            for (Segment segment : tenantSegments.values()) {
                if (segment.getMetadata().isEnabled() && persistenceService.testMatch(segment.getCondition(), profile)) {
                    segments.add(segment.getMetadata().getId());
                    tracer.trace("Profile matches tenant segment: " + segment.getMetadata().getId(), profile.getItemId());
                }
            }
        }

        // Process scoring
        if (systemScoring != null) {
            processScoring(systemScoring, profile, scores);
        }
        if (tenantScoring != null) {
            processScoring(tenantScoring, profile, scores);
        }

        return new SegmentsAndScores(segments, scores);
    }

    private void processScoring(Map<String, Scoring> scoringMap, Profile profile, Map<String, Integer> scores) {
        RequestTracer tracer = tracerService.getCurrentTracer();
        Map<String, Integer> scoreModifiers = (Map<String, Integer>) profile.getSystemProperties().get("scoreModifiers");
        for (Scoring scoring : scoringMap.values()) {
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
    }

    public List<Metadata> getSegmentMetadatasForProfile(Profile profile) {
        List<Metadata> metadatas = new ArrayList<>();
        String currentTenant = contextManager.getCurrentContext().getTenantId();

        // Get system tenant segments first
        if (!TenantService.SYSTEM_TENANT.equals(currentTenant)) {
            contextManager.executeAsSystem(() -> {
                Map<String, Segment> systemSegments = cacheService.getTenantCache(TenantService.SYSTEM_TENANT, Segment.class);
                if (systemSegments != null) {
                    for (Segment segment : systemSegments.values()) {
                        if (segment.getMetadata().isEnabled() && persistenceService.testMatch(segment.getCondition(), profile)) {
                            metadatas.add(segment.getMetadata());
                        }
                    }
                }
                return null;
            });
        }

        // Get current tenant segments (will override system segments with same ID)
        Map<String, Segment> tenantSegments = cacheService.getTenantCache(currentTenant, Segment.class);
        Map<String, Metadata> mergedMetadatas = new HashMap<>();

        // Add system tenant metadatas first
        for (Metadata metadata : metadatas) {
            mergedMetadatas.put(metadata.getId(), metadata);
        }

        // Override with current tenant metadatas
        if (tenantSegments != null) {
            for (Segment segment : tenantSegments.values()) {
                if (segment.getMetadata().isEnabled() && persistenceService.testMatch(segment.getCondition(), profile)) {
                    mergedMetadatas.put(segment.getMetadata().getId(), segment.getMetadata());
                }
            }
        }

        return new ArrayList<>(mergedMetadatas.values());
    }

    public PartialList<Metadata> getScoringMetadatas(int offset, int size, String sortBy) {
        return getMetadatas(offset, size, sortBy, Scoring.class);
    }

    public PartialList<Metadata> getScoringMetadatas(Query query) {
        return getMetadatas(query, Scoring.class);
    }

    @Override
    public Scoring getScoringDefinition(String scoringId) {
        String currentTenant = contextManager.getCurrentContext().getTenantId();
        Scoring scoring = cacheService.getWithInheritance(scoringId, currentTenant, Scoring.class);
        if (scoring != null && scoring.getMetadata().isEnabled()) {
            for (ScoringElement element : scoring.getElements()) {
                ParserHelper.resolveConditionType(definitionsService, element.getCondition(), "scoring " + scoringId);
            }
        }
        return scoring;
    }

    @Override
    public void setScoringDefinition(Scoring scoring) {
        if (scoring.getMetadata().isEnabled()) {
            for (ScoringElement element : scoring.getElements()) {
                ParserHelper.resolveConditionType(definitionsService, element.getCondition(), "scoring " + scoring.getItemId() + " element ");
                if (!scoring.getMetadata().isMissingPlugins()) {
                    updateAutoGeneratedRules(scoring.getMetadata(), element.getCondition());
                }
            }
        }

        // Save to persistence and cache
        persistenceService.save(scoring);
        cacheService.put(Scoring.ITEM_TYPE, scoring.getItemId(), scoring.getTenantId(), scoring);

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
        String currentTenant = contextManager.getCurrentContext().getTenantId();
        Map<String, Segment> tenantSegments = cacheService.getTenantCache(currentTenant, Segment.class);
        Set<Segment> impactedSegments = new HashSet<>();
        if (tenantSegments != null) {
            for (Segment segment : tenantSegments.values()) {
                if (checkScoringDeletionImpact(segment.getCondition(), scoringId)) {
                    impactedSegments.add(segment);
                }
            }
        }
        return impactedSegments;
    }

    private Set<Scoring> getScoringDependentScorings(String scoringId) {
        String currentTenant = contextManager.getCurrentContext().getTenantId();
        Map<String, Scoring> tenantScoring = cacheService.getTenantCache(currentTenant, Scoring.class);
        Set<Scoring> impactedScorings = new HashSet<>();
        if (tenantScoring != null) {
            for (Scoring scoring : tenantScoring.values()) {
                if (checkScoringDeletionImpact(scoring.getElements().get(0).getCondition(), scoringId)) {
                    impactedScorings.add(scoring);
                }
            }
        }
        return impactedScorings;
    }

    public DependentMetadata getScoringDependentMetadata(String scoringId) {
        List<Metadata> segments = new ArrayList<>();
        List<Metadata> scorings = new ArrayList<>();

        String currentTenant = contextManager.getCurrentContext().getTenantId();
        Map<String, Segment> tenantSegments = cacheService.getTenantCache(currentTenant, Segment.class);
        Map<String, Scoring> tenantScoring = cacheService.getTenantCache(currentTenant, Scoring.class);

        if (tenantSegments != null) {
            for (Segment segment : tenantSegments.values()) {
                if (checkScoringDeletionImpact(segment.getCondition(), scoringId)) {
                    segments.add(segment.getMetadata());
                }
            }
        }

        if (tenantScoring != null) {
            for (Scoring scoring : tenantScoring.values()) {
                if (checkScoringDeletionImpact(scoring.getElements().get(0).getCondition(), scoringId)) {
                    scorings.add(scoring.getMetadata());
                }
            }
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
                persistenceService.update(previousRule, Rule.class, "linkedItems", previousRule.getLinkedItems());
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

                    rule.setActions(List.of(action));
                    rule.setLinkedItems(List.of(metadata.getId()));

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
        List<Condition> l = new ArrayList<>();
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
            persistenceService.refreshIndex(Profile.class);
        }

        LOGGER.info("{} profiles updated for past event condition in {}ms", updatedProfileCount, System.currentTimeMillis() - t);
    }

    /**
     * Return the list of profile ids, for profiles that already have an event count matching the generated property key
     *
     * @param generatedPropertyKey the generated property key of the generated rule for the given past event condition.
     * @return the set of profile ids.
     */
    private Set<String> getExistingProfilesWithPastEventOccurrenceCount(String generatedPropertyKey) {
        ConditionBuilder conditionBuilder = definitionsService.getConditionBuilder();
        ConditionBuilder.ConditionItem subConditionCount = conditionBuilder.profileProperty("systemProperties.pastEvents.count").greaterThan(0);
        ConditionBuilder.ConditionItem subConditionKey = conditionBuilder.profileProperty("systemProperties.pastEvents.key").equalTo(generatedPropertyKey);
        ConditionBuilder.ConditionItem booleanCondition = conditionBuilder.and(subConditionCount, subConditionKey);
        Condition condition = conditionBuilder.nested(booleanCondition, "systemProperties.pastEvents").build();

        Set<String> profileIds = new HashSet<>();
        if (pastEventsDisablePartitions) {
            profileIds.addAll(persistenceService.aggregateWithOptimizedQuery(condition, new TermsAggregate("itemId"),
                    Profile.ITEM_TYPE, maximumIdsQueryCount).keySet());
        } else {
            Map<String, Double> m = persistenceService.getSingleValuesMetrics(condition, new String[]{"card"}, "itemId.keyword", Profile.ITEM_TYPE);
            long card = m.get("_card").longValue();
            int numParts = (int) (card / aggregateQueryBucketSize) + 2;
            for (int i = 0; i < numParts; i++) {
                profileIds.addAll(persistenceService.aggregateWithOptimizedQuery(condition, new TermsAggregate("itemId", i, numParts),
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
            LOGGER.error("Cannot generate key", e);
            return null;
        }
    }

    @Override
    public void recalculatePastEventConditions() {
        Set<String> segmentOrScoringIdsToReevaluate = new HashSet<>();
        // reevaluate auto generated rules used to store the event occurrence count on the profile
        for (Rule rule : rulesService.getAllRules()) {
            if (rule.getActions() != null && !rule.getActions().isEmpty()) {
                for (Action action : rule.getActions()) {
                    if (action.getActionTypeId().equals("setEventOccurenceCountAction")) {
                        Condition pastEventCondition = (Condition) action.getParameterValues().get("pastEventCondition");
                        if (pastEventCondition.containsParameter("numberOfDays")) {
                            recalculatePastEventOccurrencesOnProfiles(rule.getCondition(), pastEventCondition, true, true);
                            LOGGER.info("Event occurrence count on profiles updated for rule: {}", rule.getItemId());
                            if (rule.getLinkedItems() != null && rule.getLinkedItems().size() > 0) {
                                segmentOrScoringIdsToReevaluate.addAll(rule.getLinkedItems());
                            }
                        }
                    }
                }
            }
        }
        int pastEventSegmentsAndScoringsSize = segmentOrScoringIdsToReevaluate.size();
        LOGGER.info("Found {} segments or scoring plans containing pastEventCondition conditions", pastEventSegmentsAndScoringsSize);

        // get Segments and Scoring that contains relative date expressions
        String currentTenant = contextManager.getCurrentContext().getTenantId();
        Map<String, Segment> tenantSegments = cacheService.getTenantCache(currentTenant, Segment.class);
        Map<String, Scoring> tenantScoring = cacheService.getTenantCache(currentTenant, Scoring.class);

        if (tenantSegments != null) {
            segmentOrScoringIdsToReevaluate.addAll(tenantSegments.values().stream()
                    .filter(segment -> segment.getCondition() != null && segment.getCondition().toString().contains("propertyValueDateExpr"))
                    .map(Item::getItemId)
                    .collect(Collectors.toList()));
        }

        if (tenantScoring != null) {
            segmentOrScoringIdsToReevaluate.addAll(tenantScoring.values().stream()
                    .filter(scoring -> scoring.getElements() != null && !scoring.getElements().isEmpty() && scoring.getElements().stream()
                            .anyMatch(scoringElement -> scoringElement != null && scoringElement.getCondition() != null && scoringElement.getCondition().toString().contains("propertyValueDateExpr")))
                    .map(Item::getItemId)
                    .collect(Collectors.toList()));
        }
        LOGGER.info("Found {} segments or scoring plans containing date relative expressions", segmentOrScoringIdsToReevaluate.size() - pastEventSegmentsAndScoringsSize);

        // reevaluate segments and scoring.
        if (!segmentOrScoringIdsToReevaluate.isEmpty()) {
            persistenceService.refreshIndex(Profile.class, null);
            for (String linkedItem : segmentOrScoringIdsToReevaluate) {
                Segment linkedSegment = getSegmentDefinition(linkedItem);
                if (linkedSegment != null) {
                    LOGGER.info("Start segment recalculation for segment: {} - {}", linkedSegment.getItemId(), linkedSegment.getMetadata().getName());
                    updateExistingProfilesForSegment(linkedSegment);
                    continue;
                }

                Scoring linkedScoring = getScoringDefinition(linkedItem);
                if (linkedScoring != null) {
                    LOGGER.info("Start scoring plan recalculation for scoring plan: {} - {}", linkedScoring.getItemId(), linkedScoring.getMetadata().getName());
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
     * @return the set of profiles for witch the count of event occurrences have been updated.
     */
    private Set<String> updatePastEventOccurrencesOnProfiles(Map<String, Long> eventCountByProfile, String propertyKey) {
        Set<String> profilesUpdated = new HashSet<>();
        Set<String> batchProfilesToUpdate = new HashSet<>();
        Iterator<Map.Entry<String, Long>> entryIterator = eventCountByProfile.entrySet().iterator();
        Map<String, Map<String, Object>> paramPerProfile = new HashMap<>();

        while (entryIterator.hasNext()) {
            Map.Entry<String, Long> entry = entryIterator.next();
            String profileId = entry.getKey();
            if (!profileId.startsWith("_")) {
                Map<String, Object> pastEventKeyValue = new HashMap<>();
                pastEventKeyValue.put("pastEventKey", propertyKey);
                pastEventKeyValue.put("valueToAdd", entry.getValue());
                paramPerProfile.put(profileId, pastEventKeyValue);
                profilesUpdated.add(profileId);
                batchProfilesToUpdate.add(profileId);
            }

            if (batchProfilesToUpdate.size() == segmentUpdateBatchSize || (!entryIterator.hasNext() && !batchProfilesToUpdate.isEmpty())) {
                try {
                    Condition profileIdCondition = definitionsService.getConditionBuilder().condition("idsCondition").parameter("ids", batchProfilesToUpdate).parameter("match", true).build();
                    persistenceService.updateWithQueryAndStoredScript(Profile.class, new String[]{"updatePastEventOccurences"}, new Map[]{paramPerProfile}, new Condition[]{profileIdCondition});
                } catch (Exception e) {
                    LOGGER.error("Error updating {} profiles for past event system properties", paramPerProfile.size(), e);
                } finally {
                    paramPerProfile.clear();
                    batchProfilesToUpdate.clear();
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
        } catch (NoSuchAlgorithmException e) {
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
        LOGGER.info("{} profiles updated in {}ms", updatedProfileCount, System.currentTimeMillis() - updateProfilesForSegmentStartTime);
    }

    private long updateProfilesSegment(Condition profilesToUpdateCondition, String segmentId, boolean isAdd, boolean sendProfileUpdateEvent) {
        long updatedProfileCount = 0;
        PartialList<Profile> profiles = persistenceService.query(profilesToUpdateCondition, null, Profile.class, 0, segmentUpdateBatchSize, "10m");

        while (profiles != null && !profiles.getList().isEmpty()) {
            long startTime = System.currentTimeMillis();
            if (batchSegmentProfileUpdate) {
                batchUpdateProfilesSegment(segmentId, profiles.getList(), isAdd);
            } else { //send update profile one by one
                for (Profile profileToUpdate : profiles.getList()) {
                    Map<String, Object> sourceMap = buildPropertiesMapForUpdateSegment(profileToUpdate, segmentId, isAdd);
                    persistenceService.update(profileToUpdate, Profile.class, sourceMap);
                }
            }
            if (sendProfileUpdateEvent)
                sendProfileUpdatedEvent(profiles.getList());

            updatedProfileCount += profiles.size();
            LOGGER.info("{} profiles {} to segment {} in {}ms", profiles.size(), isAdd ? "added" : "removed", segmentId, System.currentTimeMillis() - startTime);

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
        List<String> failedItemsIds = persistenceService.update(profileToPropertiesMap, Profile.class);
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
                        LOGGER.warn("retry updating profile segment {}, profile {}, time {}", segmentId, profileId, new Date());
                        Profile profileToAddUpdated = persistenceService.load(profileId, Profile.class);
                        Map<String, Object> sourceMapToUpdate = buildPropertiesMapForUpdateSegment(profileToAddUpdated, segmentId, isAdd);
                        boolean isUpdated = persistenceService.update(profileToAddUpdated, Profile.class, sourceMapToUpdate);
                        if (!isUpdated)
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
        persistenceService.updateWithQueryAndStoredScript(Profile.class, scripts, scriptParams, conditions);
        LOGGER.info("Updated scoring for profiles in {}ms", System.currentTimeMillis() - startTime);
    }

    public void bundleChanged(BundleEvent event) {
        contextManager.executeAsSystem(() -> {
            switch (event.getType()) {
                case BundleEvent.STARTED:
                    processBundleStartup(event.getBundle().getBundleContext());
                    break;
                case BundleEvent.STOPPING:
                    processBundleStop(event.getBundle().getBundleContext());
                    break;
            }
        });
    }
    private void initializeTimer() {
        long initialDelay = SchedulerServiceImpl.getTimeDiffInSeconds(dailyDateExprEvaluationHourUtc, ZonedDateTime.now(ZoneOffset.UTC));

        LOGGER.info("daily recalculation job for segments and scoring that contains date relative conditions will run at fixed rate, " +
                "initialDelay={}, taskExecutionPeriod={} in seconds", initialDelay, TimeUnit.DAYS.toSeconds(taskExecutionPeriod));

        schedulerService.newTask("segment-date-recalculation")
            .withInitialDelay(initialDelay, TimeUnit.SECONDS)
            .withPeriod(taskExecutionPeriod, TimeUnit.DAYS)
            .withFixedRate()  // Run at fixed intervals
            .withSimpleExecutor(() -> {
                contextManager.executeAsSystem(() -> {
                    try {
                        long currentTimeMillis = System.currentTimeMillis();
                        LOGGER.info("running scheduled task to recalculate segments and scoring that contains date relative conditions");
                        recalculatePastEventConditions();
                        LOGGER.info("finished recalculate segments and scoring that contains date relative conditions in {}ms. ", System.currentTimeMillis() - currentTimeMillis);
                    } catch (Throwable t) {
                        LOGGER.error("Error while updating profiles for segments and scoring that contains date relative conditions", t);
                    }
                });
            })
            .schedule();
    }
    public void setTaskExecutionPeriod(long taskExecutionPeriod) {
        this.taskExecutionPeriod = taskExecutionPeriod;
    }

    protected <T extends MetadataItem> PartialList<Metadata> getMetadatas(int offset, int size, String sortBy, Class<T> clazz) {
        String currentTenantId = contextManager.getCurrentContext().getTenantId();
        List<Metadata> details = new LinkedList<>();

        // Get system tenant items first
        if (!TenantService.SYSTEM_TENANT.equals(currentTenantId)) {
            contextManager.executeAsSystem(() -> {
                Condition systemTenantCondition = new Condition(definitionsService.getConditionType("sessionPropertyCondition"));
                systemTenantCondition.setParameter("propertyName", "tenantId");
                systemTenantCondition.setParameter("comparisonOperator", "equals");
                systemTenantCondition.setParameter("propertyValue", TenantService.SYSTEM_TENANT);
                PartialList<T> systemItems = persistenceService.query(systemTenantCondition, sortBy, clazz, 0, -1);
                for (T definition : systemItems.getList()) {
                    details.add(definition.getMetadata());
                }
                return null;
            });
        }

        // Get current tenant items (will override system items with same ID)
        Condition tenantCondition = new Condition(definitionsService.getConditionType("sessionPropertyCondition"));
        tenantCondition.setParameter("propertyName", "tenantId");
        tenantCondition.setParameter("comparisonOperator", "equals");
        tenantCondition.setParameter("propertyValue", currentTenantId);
        PartialList<T> items = persistenceService.query(tenantCondition, sortBy, clazz, 0, -1);
        Map<String, Metadata> mergedDetails = new HashMap<>();

        // Add system tenant items first
        for (Metadata metadata : details) {
            mergedDetails.put(metadata.getId(), metadata);
        }

        // Override with current tenant items
        for (T definition : items.getList()) {
            mergedDetails.put(definition.getMetadata().getId(), definition.getMetadata());
        }

        // Convert to list and apply pagination
        List<Metadata> finalDetails = new ArrayList<>(mergedDetails.values());
        if (sortBy != null) {
            // TODO: Implement sorting of merged results
        }

        int totalSize = finalDetails.size();
        int fromIndex = offset;
        int toIndex = offset + size;
        if (fromIndex >= totalSize) {
            return new PartialList<Metadata>(new ArrayList<>(), offset, size, totalSize, PartialList.Relation.EQUAL);
        }
        if (toIndex > totalSize) {
            toIndex = totalSize;
        }
        finalDetails = finalDetails.subList(fromIndex, toIndex);

        return new PartialList<Metadata>(finalDetails, offset, size, totalSize, PartialList.Relation.EQUAL);
    }

    protected <T extends MetadataItem> PartialList<Metadata> getMetadatas(Query query, Class<T> clazz) {
        definitionsService.resolveConditionType(query.getCondition());
        String currentTenantId = contextManager.getCurrentContext().getTenantId();
        if (currentTenantId == null) {
            LOGGER.error("No current tenant id available, unable retrieve segments");
            return new PartialList<>();
        }

        List<Metadata> details = new LinkedList<>();

        // Get system tenant items first
        if (!TenantService.SYSTEM_TENANT.equals(currentTenantId)) {
            contextManager.executeAsSystem(() -> {
                Condition systemTenantCondition = new Condition(definitionsService.getConditionType("booleanCondition"));
                systemTenantCondition.setParameter("operator", "and");
                List<Condition> systemConditions = new ArrayList<>();

                Condition systemTenantCheck = new Condition(definitionsService.getConditionType("sessionPropertyCondition"));
                systemTenantCheck.setParameter("propertyName", "tenantId");
                systemTenantCheck.setParameter("comparisonOperator", "equals");
                systemTenantCheck.setParameter("propertyValue", TenantService.SYSTEM_TENANT);
                systemConditions.add(systemTenantCheck);

                systemConditions.add(query.getCondition());
                systemTenantCondition.setParameter("subConditions", systemConditions);

                PartialList<T> systemItems = persistenceService.query(systemTenantCondition, query.getSortby(), clazz, 0, -1);
                for (T definition : systemItems.getList()) {
                    details.add(definition.getMetadata());
                }
                return null;
            });
        }

        // Get current tenant items (will override system items with same ID)
        Condition tenantCondition = new Condition(definitionsService.getConditionType("booleanCondition"));
        tenantCondition.setParameter("operator", "and");
        List<Condition> conditions = new ArrayList<>();

        Condition tenantCheck = new Condition(definitionsService.getConditionType("sessionPropertyCondition"));
        tenantCheck.setParameter("propertyName", "tenantId");
        tenantCheck.setParameter("comparisonOperator", "equals");
        tenantCheck.setParameter("propertyValue", currentTenantId);
        conditions.add(tenantCheck);

        conditions.add(query.getCondition());
        tenantCondition.setParameter("subConditions", conditions);

        PartialList<T> items = persistenceService.query(tenantCondition, query.getSortby(), clazz, 0, -1);
        Map<String, Metadata> mergedDetails = new HashMap<>();

        // Add system tenant items first
        for (Metadata metadata : details) {
            mergedDetails.put(metadata.getId(), metadata);
        }

        // Override with current tenant items
        for (T definition : items.getList()) {
            mergedDetails.put(definition.getMetadata().getId(), definition.getMetadata());
        }

        // Convert to list and apply pagination
        List<Metadata> finalDetails = new ArrayList<>(mergedDetails.values());
        if (query.getSortby() != null) {
            // TODO: Implement sorting of merged results
        }

        int totalSize = finalDetails.size();
        int fromIndex = query.getOffset();
        int toIndex = fromIndex + query.getLimit();
        if (fromIndex >= totalSize) {
            return new PartialList<Metadata>(new ArrayList<>(), query.getOffset(), query.getLimit(), totalSize, PartialList.Relation.EQUAL);
        }
        if (toIndex > totalSize) {
            toIndex = totalSize;
        }
        finalDetails = finalDetails.subList(fromIndex, toIndex);

        return new PartialList<Metadata>(finalDetails, query.getOffset(), query.getLimit(), totalSize, PartialList.Relation.EQUAL);
    }
}
