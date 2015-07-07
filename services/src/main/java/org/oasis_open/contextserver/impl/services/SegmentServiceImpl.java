package org.oasis_open.contextserver.impl.services;

/*
 * #%L
 * context-server-services
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

import com.fasterxml.jackson.core.JsonProcessingException;
import org.oasis_open.contextserver.api.*;
import org.oasis_open.contextserver.api.actions.Action;
import org.oasis_open.contextserver.api.conditions.Condition;
import org.oasis_open.contextserver.api.conditions.ConditionType;
import org.oasis_open.contextserver.api.goals.Goal;
import org.oasis_open.contextserver.api.query.Query;
import org.oasis_open.contextserver.api.rules.Rule;
import org.oasis_open.contextserver.api.segments.Scoring;
import org.oasis_open.contextserver.api.segments.ScoringElement;
import org.oasis_open.contextserver.api.segments.Segment;
import org.oasis_open.contextserver.api.segments.SegmentsAndScores;
import org.oasis_open.contextserver.api.services.DefinitionsService;
import org.oasis_open.contextserver.api.services.RulesService;
import org.oasis_open.contextserver.api.services.SegmentService;
import org.oasis_open.contextserver.persistence.spi.CustomObjectMapper;
import org.oasis_open.contextserver.persistence.spi.PersistenceService;
import org.oasis_open.contextserver.persistence.spi.aggregate.TermsAggregate;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.SynchronousBundleListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.*;
import java.io.IOException;
import java.net.URL;
import java.security.MessageDigest;
import java.util.*;

public class SegmentServiceImpl implements SegmentService, SynchronousBundleListener {

    private static final Logger logger = LoggerFactory.getLogger(SegmentServiceImpl.class.getName());

    private BundleContext bundleContext;

    private PersistenceService persistenceService;

    private DefinitionsService definitionsService;

    private RulesService rulesService;

    private long taskExecutionPeriod = 24L * 60L * 60L * 1000L;

    public SegmentServiceImpl() {
        logger.info("Initializing segment service...");
    }

    private List<Segment> allSegments;
    private List<Scoring> allScoring;

    public static void dumpJSON(JsonValue tree, String key, String depthPrefix) {
        if (key != null)
            logger.info(depthPrefix + "Key " + key + ": ");
        switch (tree.getValueType()) {
            case OBJECT:
                logger.info(depthPrefix + "OBJECT");
                JsonObject object = (JsonObject) tree;
                for (String name : object.keySet())
                    dumpJSON(object.get(name), name, depthPrefix + "  ");
                break;
            case ARRAY:
                logger.info(depthPrefix + "ARRAY");
                JsonArray array = (JsonArray) tree;
                for (JsonValue val : array)
                    dumpJSON(val, null, depthPrefix + "  ");
                break;
            case STRING:
                JsonString st = (JsonString) tree;
                logger.info(depthPrefix + "STRING " + st.getString());
                break;
            case NUMBER:
                JsonNumber num = (JsonNumber) tree;
                logger.info(depthPrefix + "NUMBER " + num.toString());
                break;
            case TRUE:
            case FALSE:
            case NULL:
                logger.info(depthPrefix + tree.getValueType().toString());
                break;
        }
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    public void setRulesService(RulesService rulesService) {
        this.rulesService = rulesService;
    }

    public void postConstruct() {
        logger.debug("postConstruct {" + bundleContext.getBundle() + "}");
        loadPredefinedSegments(bundleContext);
        loadPredefinedScorings(bundleContext);
        for (Bundle bundle : bundleContext.getBundles()) {
            if (bundle.getBundleContext() != null) {
                loadPredefinedSegments(bundle.getBundleContext());
                loadPredefinedScorings(bundle.getBundleContext());
            }
        }
        bundleContext.addBundleListener(this);
        initializeTimer();
    }

    public void preDestroy() {
        bundleContext.removeBundleListener(this);
    }


    private void processBundleStartup(BundleContext bundleContext) {
        if (bundleContext == null) {
            return;
        }
        loadPredefinedSegments(bundleContext);
        loadPredefinedScorings(bundleContext);

        List<PluginType> types = definitionsService.getTypesByPlugin().get(bundleContext.getBundle().getBundleId());
        List<String> addedConditions = new ArrayList<String>();
        if (types != null) {
            for (PluginType type : types) {
                if (type instanceof ConditionType) {
                    addedConditions.add(((ConditionType) type).getId());
                }
            }
        }
        if (!addedConditions.isEmpty()) {
            for (Segment segment : persistenceService.query("missingPlugins", "true", null, Segment.class)) {
                boolean succeed = ParserHelper.resolveConditionType(definitionsService, segment.getCondition());
                if (succeed) {
                    logger.info("Enable segment " + segment.getItemId());
                    segment.getMetadata().setMissingPlugins(false);
                    setSegmentDefinition(segment);
                }
            }
        }
    }

    private void processBundleStop(BundleContext bundleContext) {
        if (bundleContext == null) {
            return;
        }
        List<PluginType> types = definitionsService.getTypesByPlugin().get(bundleContext.getBundle().getBundleId());
        List<String> removedConditions = new ArrayList<String>();
        if (types != null) {
            for (PluginType type : types) {
                if (type instanceof ConditionType) {
                    removedConditions.add(((ConditionType) type).getId());
                }
            }
        }
        if (!removedConditions.isEmpty()) {
            for (Segment segment : persistenceService.getAllItems(Segment.class)) {
                List<String> conditions = ParserHelper.getConditionTypeIds(segment.getCondition());
                if (!Collections.disjoint(conditions, removedConditions)) {
                    logger.info("Disable segment " + segment.getItemId());
                    segment.getMetadata().setMissingPlugins(true);
                    setSegmentDefinition(segment);
                }
            }
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
                if (getSegmentDefinition(segment.getMetadata().getId()) == null) {
                    setSegmentDefinition(segment);
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
                if (getScoringDefinition(scoring.getMetadata().getId()) == null) {
                    setScoringDefinition(scoring);
                }
            } catch (IOException e) {
                logger.error("Error while loading segment definition " + predefinedScoringURL, e);
            }
        }
    }

    public PartialList<Metadata> getSegmentMetadatas(int offset, int size, String sortBy) {
        PartialList<Segment> segments = persistenceService.getAllItems(Segment.class, offset, size, sortBy);
        List<Metadata> details = new LinkedList<>();
        for (Segment definition : segments.getList()) {
            details.add(definition.getMetadata());
        }
        return new PartialList<>(details, segments.getOffset(), segments.getPageSize(), segments.getTotalSize());
    }

    public PartialList<Metadata> getSegmentMetadatas(String scope, int offset, int size, String sortBy) {
        PartialList<Segment> segments = persistenceService.query("metadata.scope", scope, sortBy, Segment.class, offset, size);
        List<Metadata> details = new LinkedList<>();
        for (Segment definition : segments.getList()) {
            details.add(definition.getMetadata());
        }
        return new PartialList<>(details, segments.getOffset(), segments.getPageSize(), segments.getTotalSize());
    }

    public PartialList<Metadata> getSegmentMetadatas(Query query) {
        if(query.isForceRefresh()){
            persistenceService.refresh();
        }
        definitionsService.resolveConditionType(query.getCondition());
        PartialList<Segment> segments = persistenceService.query(query.getCondition(), query.getSortby(), Segment.class, query.getOffset(), query.getLimit());
        List<Metadata> details = new LinkedList<>();
        for (Segment definition : segments.getList()) {
            details.add(definition.getMetadata());
        }
        return new PartialList<>(details, segments.getOffset(), segments.getPageSize(), segments.getTotalSize());
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

    private void checkIfSegmentIsImpacted(Segment segment, Condition condition, String segmentToDeleteId, Set<Segment> impactedSegments) {
        if(condition != null) {
            @SuppressWarnings("unchecked")
            final List<Condition> subConditions = (List<Condition>) condition.getParameter("subConditions");
            if (subConditions != null) {
                for (Condition subCondition : subConditions) {
                    checkIfSegmentIsImpacted(segment, subCondition, segmentToDeleteId, impactedSegments);
                }
            } else if ("profileSegmentCondition".equals(condition.getConditionTypeId())) {
                @SuppressWarnings("unchecked")
                final List<String> referencedSegmentIds = (List<String>) condition.getParameter("segments");

                if (referencedSegmentIds.indexOf(segmentToDeleteId) >= 0) {
                    impactedSegments.add(segment);
                }
            }
        }
    }

    /**
     * Return an updated condition that do not contain a condition on the segmentId anymore
     * it's remove the unnecessary boolean condition (if a condition is the only one of a boolean the boolean will be remove and the subcondition returned)
     * it's return null when there is no more condition after (if the condition passed was only a segment condition on the segmentId)
     * @param condition the condition to update
     * @param segmentId the segment id to remove in the condition
     * @return updated condition
     */
    private Condition updateImpactedCondition(Condition condition, String segmentId) {
        if ("booleanCondition".equals(condition.getConditionTypeId())) {
            @SuppressWarnings("unchecked")
            final List<Condition> subConditions = (List<Condition>) condition.getParameter("subConditions");
            List<Condition> updatedSubConditions = new LinkedList<>();
            for (Condition subCondition : subConditions) {
                Condition updatedCondition = updateImpactedCondition(subCondition, segmentId);
                if(updatedCondition != null) {
                    updatedSubConditions.add(updatedCondition);
                }
            }
            if(!updatedSubConditions.isEmpty()){
                if(updatedSubConditions.size() == 1) {
                    return updatedSubConditions.get(0);
                } else {
                    condition.setParameter("subConditions", updatedSubConditions);
                    return condition;
                }
            } else {
                return null;
            }
        } else if("profileSegmentCondition".equals(condition.getConditionTypeId())) {
            @SuppressWarnings("unchecked")
            final List<String> referencedSegmentIds = (List<String>) condition.getParameter("segments");
            if (referencedSegmentIds.indexOf(segmentId) >= 0) {
                referencedSegmentIds.remove(segmentId);
                if(referencedSegmentIds.isEmpty()) {
                    return null;
                } else {
                    condition.setParameter("segments", referencedSegmentIds);
                }
            }
        }
        return condition;
    }

    public List<Metadata> removeSegmentDefinition(String segmentId, boolean validate) {

        // search all segments to see if they define a profileSegmentCondition with the segment we're trying to delete
        // to see which segments would be impacted by this deletion
        final List<Segment> allSegments = this.allSegments;
        Set<Segment> impactedSegments = new HashSet<>(allSegments.size());
        for (Segment segment : allSegments) {
            // check whether the current segment is impacted and add it to the appropriate collections if needed
            checkIfSegmentIsImpacted(segment, segment.getCondition(), segmentId, impactedSegments);
        }

        if (!validate || impactedSegments.isEmpty()) {
            // update profiles
            Condition segmentCondition = new Condition();
            segmentCondition.setConditionType(definitionsService.getConditionType("profilePropertyCondition"));
            segmentCondition.setParameter("propertyName", "segments");
            segmentCondition.setParameter("comparisonOperator", "equals");
            segmentCondition.setParameter("propertyValue", segmentId);

            List<Profile> previousProfiles = persistenceService.query(segmentCondition, null, Profile.class);
            for (Profile profileToRemove : previousProfiles) {
                profileToRemove.getSegments().remove(segmentId);
                persistenceService.update(profileToRemove.getItemId(), null, Profile.class, "segments", profileToRemove.getSegments());
            }

            // update impacted segments
            for (Segment segment : impactedSegments) {
                Condition updatedCondition = updateImpactedCondition(segment.getCondition(), segmentId);
                segment.setCondition(updatedCondition);
                if(updatedCondition == null) {
                    clearAutoGeneratedRules(persistenceService.query("linkedItems", segment.getMetadata().getId(), null, Rule.class), segment.getMetadata().getId());
                    segment.getMetadata().setEnabled(false);
                }
                setSegmentDefinition(segment);
            }

            persistenceService.remove(segmentId, Segment.class);
            List<Rule> previousRules = persistenceService.query("linkedItems", segmentId, null, Rule.class);
            clearAutoGeneratedRules(previousRules, segmentId);
        }

        List<Metadata> metadata = new LinkedList<>();
        for (Segment definition : impactedSegments) {
            metadata.add(definition.getMetadata());
        }
        return metadata;
    }


    public PartialList<Profile> getMatchingIndividuals(String segmentID, int offset, int size, String sortBy) {
        Segment segment = getSegmentDefinition(segmentID);
        if (segment == null) {
            return new PartialList<Profile>();
        }
        return persistenceService.query(segment.getCondition(), sortBy, Profile.class, offset, size);
    }

    public long getMatchingIndividualsCount(String segmentID) {
        if (getSegmentDefinition(segmentID) == null) {
            return 0;
        }

        Condition excludeMergedProfilesCondition = new Condition(definitionsService.getConditionType("profilePropertyCondition"));
        excludeMergedProfilesCondition.setParameter("propertyName", "mergedWith");
        excludeMergedProfilesCondition.setParameter("comparisonOperator", "missing");
        Condition condition = new Condition(definitionsService.getConditionType("booleanCondition"));
        condition.setParameter("operator", "and");
        condition.setParameter("subConditions", Arrays.asList(getSegmentDefinition(segmentID).getCondition(), excludeMergedProfilesCondition));

        return persistenceService.queryCount(condition, Profile.ITEM_TYPE);
    }

    public Boolean isProfileInSegment(Profile profile, String segmentId) {
        Set<String> matchingSegments = getSegmentsAndScoresForProfile(profile).getSegments();

        return matchingSegments.contains(segmentId);
    }

    public SegmentsAndScores getSegmentsAndScoresForProfile(Profile profile) {
        Set<String> segments = new HashSet<String>();
        Map<String,Integer> scores = new HashMap<String, Integer>();

        List<Segment> allSegments = this.allSegments;
        for (Segment segment : allSegments) {
            if (persistenceService.testMatch(segment.getCondition(), profile)) {
                segments.add(segment.getMetadata().getId());
            }
        }

        List<Scoring> allScoring = this.allScoring;
        for (Scoring scoring : allScoring) {
            int score = 0;
            for (ScoringElement scoringElement : scoring.getElements()) {
                if (persistenceService.testMatch(scoringElement.getCondition(), profile)) {
                    score += scoringElement.getValue();
                }
            }
            scores.put(scoring.getMetadata().getId(), score);
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

    public Set<Metadata> getScoringMetadatas() {
        Set<Metadata> descriptions = new HashSet<Metadata>();
        for (Scoring scoring : persistenceService.getAllItems(Scoring.class, 0, 50, null).getList()) {
            descriptions.add(scoring.getMetadata());
        }
        return descriptions;
    }

    public Set<Metadata> getScoringMetadatas(Query query) {
        definitionsService.resolveConditionType(query.getCondition());
        Set<Metadata> descriptions = new HashSet<Metadata>();
        for (Scoring definition : persistenceService.query(query.getCondition(), query.getSortby(), Scoring.class, query.getOffset(), query.getLimit()).getList()) {
            descriptions.add(definition.getMetadata());
        }
        return descriptions;
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

    public void removeScoringDefinition(String scoringId) {
        persistenceService.remove(scoringId, Scoring.class);
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
        if (condition.getConditionType().getTagIDs().contains("eventCondition") && !condition.getConditionType().getTagIDs().contains("profileCondition")) {
            try {
                Map<String,Object> m = new HashMap<>(3);
                m.put("scope",metadata.getScope());
                m.put("condition", condition);
                m.put("numberOfDays", parentCondition.getParameter("numberOfDays"));
                String key = CustomObjectMapper.getObjectMapper().writeValueAsString(m);
                key = "eventTriggered" + getMD5(key);
                parentCondition.setParameter("generatedPropertyKey", key);
                Rule rule = rulesService.getRule(key);
                if (rule == null) {
                    rule = new Rule(new Metadata(metadata.getScope(), key, "Auto generated rule for "+metadata.getName(), ""));
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
            } catch (JsonProcessingException e) {
                logger.error(e.getMessage(), e);
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
        Map<String, Long> res = persistenceService.aggregateQuery(andCondition, new TermsAggregate("profileId"), Event.ITEM_TYPE);
        for (Map.Entry<String, Long> entry : res.entrySet()) {
            if (!entry.getKey().startsWith("_")) {
                Map<String,Object> p = new HashMap<>();
                p.put(propertyKey, entry.getValue());
                Map<String,Object> p2 = new HashMap<>();
                p2.put("pastEvents",p);
                try {
                    persistenceService.update(entry.getKey(), null, Profile.class, "systemProperties", p2);
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                }
            }
        }

        logger.info("Profiles past condition updated in {}", System.currentTimeMillis()-t);
    }

    private void updateExistingProfilesForSegment(Segment segment) {
        long t = System.currentTimeMillis();
        Condition segmentCondition = new Condition();

        segmentCondition.setConditionType(definitionsService.getConditionType("profilePropertyCondition"));
        segmentCondition.setParameter("propertyName", "segments");
        segmentCondition.setParameter("comparisonOperator", "equals");
        segmentCondition.setParameter("propertyValue", segment.getItemId());

        if(segment.getMetadata().isEnabled()) {
            List<Profile> previousProfiles = persistenceService.query(segmentCondition, null, Profile.class);
            List<Profile> newProfiles = persistenceService.query(segment.getCondition(), null, Profile.class);

            List<Profile> add = new ArrayList<>(newProfiles);
            add.removeAll(previousProfiles);
            previousProfiles.removeAll(newProfiles);

            for (Profile profileToAdd : add) {
                profileToAdd.getSegments().add(segment.getItemId());
                persistenceService.update(profileToAdd.getItemId(), null, Profile.class, "segments", profileToAdd.getSegments());
            }
            for (Profile profileToRemove : previousProfiles) {
                profileToRemove.getSegments().remove(segment.getItemId());
                persistenceService.update(profileToRemove.getItemId(), null, Profile.class, "segments", profileToRemove.getSegments());
            }
        } else {
            List<Profile> previousProfiles = persistenceService.query(segmentCondition, null, Profile.class);
            for (Profile profileToRemove : previousProfiles) {
                profileToRemove.getSegments().remove(segment.getItemId());
                persistenceService.update(profileToRemove.getItemId(), null, Profile.class, "segments", profileToRemove.getSegments());
            }
        }
        logger.info("Segments updated in {}", System.currentTimeMillis()-t);
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
        // TODO : timer need to be canceled in preDestroy
        Timer timer = new Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
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
            }
        };
        timer.scheduleAtFixedRate(task, getDay(1).getTime(), taskExecutionPeriod);

        task = new TimerTask() {
            @Override
            public void run() {
                allSegments = getAllSegmentDefinitions();
                allScoring = getAllScoringDefinitions();
            }
        };
        timer.scheduleAtFixedRate(task, 0, 1000);
    }

    private GregorianCalendar getDay(int offset) {
        GregorianCalendar gc = new GregorianCalendar();
        gc = new GregorianCalendar(gc.get(Calendar.YEAR), gc.get(Calendar.MONTH), gc.get(Calendar.DAY_OF_MONTH));
        gc.add(Calendar.DAY_OF_MONTH, offset);
        return gc;
    }

    public void setTaskExecutionPeriod(long taskExecutionPeriod) {
        this.taskExecutionPeriod = taskExecutionPeriod;
    }


}
