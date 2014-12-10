package org.oasis_open.wemi.context.server.impl.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.lang3.StringUtils;
import org.oasis_open.wemi.context.server.api.Metadata;
import org.oasis_open.wemi.context.server.api.PartialList;
import org.oasis_open.wemi.context.server.api.PluginType;
import org.oasis_open.wemi.context.server.api.conditions.ConditionType;
import org.oasis_open.wemi.context.server.api.segments.Scoring;
import org.oasis_open.wemi.context.server.api.segments.ScoringElement;
import org.oasis_open.wemi.context.server.api.segments.Segment;
import org.oasis_open.wemi.context.server.api.User;
import org.oasis_open.wemi.context.server.api.actions.Action;
import org.oasis_open.wemi.context.server.api.conditions.Condition;
import org.oasis_open.wemi.context.server.api.rules.Rule;
import org.oasis_open.wemi.context.server.api.services.DefinitionsService;
import org.oasis_open.wemi.context.server.api.services.RulesService;
import org.oasis_open.wemi.context.server.api.services.SegmentService;
import org.oasis_open.wemi.context.server.api.services.SegmentsAndScores;
import org.oasis_open.wemi.context.server.persistence.spi.CustomObjectMapper;
import org.oasis_open.wemi.context.server.persistence.spi.PersistenceService;
import org.osgi.framework.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.*;
import java.io.IOException;
import java.net.URL;
import java.security.MessageDigest;
import java.util.*;

/**
 * Created by loom on 26.04.14.
 */
public class SegmentServiceImpl implements SegmentService, SynchronousBundleListener {

    private static final Logger logger = LoggerFactory.getLogger(SegmentServiceImpl.class.getName());

    private BundleContext bundleContext;

    private PersistenceService persistenceService;

    private DefinitionsService definitionsService;

    private RulesService rulesService;

    public SegmentServiceImpl() {
        logger.info("Initializing segment service...");
    }

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
                    setSegmentDefinition(segment.getItemId(), segment);
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
                    setSegmentDefinition(segment.getItemId(), segment);
                }
            }
        }
    }

    private void loadPredefinedSegments(BundleContext bundleContext) {
        Enumeration<URL> predefinedSegmentEntries = bundleContext.getBundle().findEntries("META-INF/wemi/segments", "*.json", true);
        if (predefinedSegmentEntries == null) {
            return;
        }
        while (predefinedSegmentEntries.hasMoreElements()) {
            URL predefinedSegmentURL = predefinedSegmentEntries.nextElement();
            logger.debug("Found predefined segment at " + predefinedSegmentURL + ", loading... ");

            try {
                Segment segment = CustomObjectMapper.getObjectMapper().readValue(predefinedSegmentURL, Segment.class);
                if (getSegmentDefinition(segment.getMetadata().getId()) == null) {
                    setSegmentDefinition(segment.getMetadata().getId(), segment);
                }
            } catch (IOException e) {
                logger.error("Error while loading segment definition " + predefinedSegmentURL, e);
            }
        }
    }

    private void loadPredefinedScorings(BundleContext bundleContext) {
        Enumeration<URL> predefinedScoringEntries = bundleContext.getBundle().findEntries("META-INF/wemi/scoring", "*.json", true);
        if (predefinedScoringEntries == null) {
            return;
        }
        while (predefinedScoringEntries.hasMoreElements()) {
            URL predefinedScoringURL = predefinedScoringEntries.nextElement();
            logger.debug("Found predefined scoring at " + predefinedScoringURL + ", loading... ");

            try {
                Scoring scoring = CustomObjectMapper.getObjectMapper().readValue(predefinedScoringURL, Scoring.class);
                if (getScoringDefinition(scoring.getMetadata().getId()) == null) {
                    setScoringDefinition(scoring.getMetadata().getId(), scoring);
                }
            } catch (IOException e) {
                logger.error("Error while loading segment definition " + predefinedScoringURL, e);
            }
        }
    }

    public PartialList<User> getMatchingIndividuals(String segmentID, int offset, int size, String sortBy) {
        if (getSegmentDefinition(segmentID) == null) {
            return new PartialList<User>();
        }
        return persistenceService.query(getSegmentDefinition(segmentID).getCondition(), sortBy, User.class, offset, size);
    }

    public long getMatchingIndividualsCount(String segmentID) {
        if (getSegmentDefinition(segmentID) == null) {
            return 0;
        }
        return persistenceService.queryCount(getSegmentDefinition(segmentID).getCondition(), User.ITEM_TYPE);
    }

    public Boolean isUserInSegment(User user, String segmentId) {
        Set<String> matchingSegments = getSegmentsAndScoresForUser(user).getSegments();

        return matchingSegments.contains(segmentId);
    }

    public SegmentsAndScores getSegmentsAndScoresForUser(User user) {
        List<String> savedQueries = persistenceService.getMatchingSavedQueries(user);
        Set<String> segments = new HashSet<String>();
        Map<String,Integer> scores = new HashMap<String, Integer>();
        new SegmentsAndScores(segments,scores);
        for (String s : savedQueries) {
            if (s.startsWith("segment_")) {
                segments.add(s.substring("segment_".length()));
            } else if (s.startsWith("scoring_")) {
                String scoringId = s.substring("scoring_".length());
                int index = Integer.parseInt(StringUtils.substringAfterLast(scoringId, "_"));
                scoringId = StringUtils.substringBeforeLast(scoringId, "_");
                Scoring sc = getScoringDefinition(scoringId);
                if (!scores.containsKey(scoringId)) {
                    scores.put(scoringId, 0);
                }
                scores.put(scoringId, scores.get(scoringId) + sc.getElements().get(index).getValue());
            }
        }
        return new SegmentsAndScores(segments, scores);
    }

    public Set<Metadata> getSegmentMetadatas() {
        Set<Metadata> descriptions = new HashSet<Metadata>();
        for (Segment definition : persistenceService.getAllItems(Segment.class, 0, 50, null).getList()) {
            descriptions.add(definition.getMetadata());
        }
        return descriptions;
    }

    public Segment getSegmentDefinition(String segmentId) {
        Segment definition = persistenceService.load(segmentId, Segment.class);
        if (definition != null) {
            ParserHelper.resolveConditionType(definitionsService, definition.getCondition());
        }
        return definition;
    }

    public void setSegmentDefinition(String segmentId, Segment segment) {
        ParserHelper.resolveConditionType(definitionsService, segment.getCondition());
        if (segment.getMetadata().isEnabled() && !segment.getMetadata().isMissingPlugins()) {
            createAutoGeneratedRules(segment.getCondition());
            persistenceService.saveQuery("segment_" + segmentId, segment.getCondition());
        } else {
            persistenceService.removeQuery("segment_" + segmentId);
        }
        // make sure we update the name and description metadata that might not match, so first we remove the entry from the map
        persistenceService.save(segment);
    }

    public void createSegmentDefinition(String segmentId, String name, String description) {
        Metadata metadata = new Metadata(segmentId, name, description);
        Segment segment = new Segment(metadata);
        Condition rootCondition = new Condition();
        rootCondition.setConditionType(definitionsService.getConditionType("andCondition"));
        rootCondition.getParameterValues().put("subConditions", new ArrayList<Condition>());
        segment.setCondition(rootCondition);

        setSegmentDefinition(segmentId, segment);
    }

    public void removeSegmentDefinition(String segmentId) {
        persistenceService.removeQuery("segment_" + segmentId);
        persistenceService.remove(segmentId, Segment.class);
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

    public void setScoringDefinition(String scoringId, Scoring scoring) {
        for (ScoringElement element : scoring.getElements()) {
            ParserHelper.resolveConditionType(definitionsService, element.getCondition());
        }
        int i = 0;
        for (ScoringElement element : scoring.getElements()) {
            if (scoring.getMetadata().isEnabled() && !scoring.getMetadata().isMissingPlugins()) {
                createAutoGeneratedRules(element.getCondition());
                persistenceService.saveQuery("scoring_" + scoringId + "_" + i, element.getCondition());
            } else {
                persistenceService.removeQuery("scoring_" + scoringId + "_" + i);
            }
            i++;
        }
        // make sure we update the name and description metadata that might not match, so first we remove the entry from the map
        persistenceService.save(scoring);
    }

    public void createScoringDefinition(String scoringId, String name, String description) {
        Metadata metadata = new Metadata(scoringId, name, description);
        Scoring scoring = new Scoring(metadata);
        Condition rootCondition = new Condition();
        rootCondition.setConditionType(definitionsService.getConditionType("andCondition"));
        rootCondition.getParameterValues().put("subConditions", new ArrayList<Condition>());
        scoring.setElements(new ArrayList<ScoringElement>());

        setScoringDefinition(scoringId, scoring);
    }

    public void removeScoringDefinition(String scoringId) {
        Scoring scoring = getScoringDefinition(scoringId);
        int i = 0;
        for (ScoringElement element : scoring.getElements()) {
            persistenceService.removeQuery("scoring_" + scoringId + "_" + i);
            i++;
        }
        persistenceService.remove(scoringId, Scoring.class);
    }

    public void createAutoGeneratedRules(Condition condition) {
        List<Rule> rules = new ArrayList<Rule>();
        getAutoGeneratedRules(condition, null, rules);
        for (Rule rule : rules) {
            rulesService.setRule(rule.getMetadata().getId(), rule);
        }
    }

    private void getAutoGeneratedRules(Condition condition, Condition parentCondition, List<Rule> rules) {
        if (condition.getConditionType().getTagIDs().contains("eventCondition") && !condition.getConditionType().getTagIDs().contains("userCondition")) {
            try {
                String key = CustomObjectMapper.getObjectMapper().writeValueAsString(CustomObjectMapper.getObjectMapper().writeValueAsString(parentCondition));
                key = "eventTriggered" + getMD5(key);
                condition.getParameterValues().put("generatedPropertyKey", key);
                if (rulesService.getRule(key) == null) {
                    Rule rule = new Rule(new Metadata(key, "Auto generated rule", ""));
                    rule.setCondition(condition);
                    rule.getMetadata().setHidden(true);
                    final Action action = new Action();
                    action.setActionType(definitionsService.getActionType("setEventOccurenceCountAction"));
                    action.getParameterValues().put("eventCondition", parentCondition);
                    rule.setActions(Arrays.asList(action));
                    rules.add(rule);
                }
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        } else {
            for (Object parameterValue : condition.getParameterValues().values()) {
                if (parameterValue instanceof Condition) {
                    getAutoGeneratedRules((Condition) parameterValue, condition, rules);
                } else if (parameterValue instanceof Collection) {
                    for (Object subCondition : (Collection) parameterValue) {
                        if (subCondition instanceof Condition) {
                            getAutoGeneratedRules((Condition) subCondition, condition, rules);
                        }
                    }
                }
            }
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
        }
        return null;
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
}
