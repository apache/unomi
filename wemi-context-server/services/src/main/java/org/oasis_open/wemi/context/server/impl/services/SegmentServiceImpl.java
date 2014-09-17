package org.oasis_open.wemi.context.server.impl.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.oasis_open.wemi.context.server.api.Metadata;
import org.oasis_open.wemi.context.server.api.PartialList;
import org.oasis_open.wemi.context.server.api.SegmentDefinition;
import org.oasis_open.wemi.context.server.api.User;
import org.oasis_open.wemi.context.server.api.actions.Action;
import org.oasis_open.wemi.context.server.api.conditions.Condition;
import org.oasis_open.wemi.context.server.api.rules.Rule;
import org.oasis_open.wemi.context.server.api.services.DefinitionsService;
import org.oasis_open.wemi.context.server.api.services.RulesService;
import org.oasis_open.wemi.context.server.api.services.SegmentService;
import org.oasis_open.wemi.context.server.persistence.spi.CustomObjectMapper;
import org.oasis_open.wemi.context.server.persistence.spi.PersistenceService;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;
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
public class SegmentServiceImpl implements SegmentService, BundleListener {

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
        for (Bundle bundle : bundleContext.getBundles()) {
            if (bundle.getBundleContext() != null) {
                loadPredefinedSegments(bundle.getBundleContext());
            }
        }
        bundleContext.addBundleListener(this);
    }

    public void preDestroy() {
        bundleContext.removeBundleListener(this);
    }

    private void loadPredefinedSegments(BundleContext bundleContext) {
        if (bundleContext == null) {
            return;
        }
        Enumeration<URL> predefinedSegmentEntries = bundleContext.getBundle().findEntries("META-INF/wemi/segments", "*.json", true);
        if (predefinedSegmentEntries == null) {
            return;
        }
        while (predefinedSegmentEntries.hasMoreElements()) {
            URL predefinedSegmentURL = predefinedSegmentEntries.nextElement();
            logger.debug("Found predefined segment at " + predefinedSegmentURL + ", loading... ");

            try {
                SegmentDefinition segment = CustomObjectMapper.getObjectMapper().readValue(predefinedSegmentURL, SegmentDefinition.class);
                if (getSegmentDefinition(segment.getMetadata().getId()) == null) {
                    setSegmentDefinition(segment.getMetadata().getId(), segment);
                }
            } catch (IOException e) {
                logger.error("Error while loading segment definition " + predefinedSegmentURL, e);
            }
        }
    }

    public PartialList<User> getMatchingIndividuals(String segmentID) {
        if (getSegmentDefinition(segmentID) == null) {
            return new PartialList<User>();
        }
        return persistenceService.query(getSegmentDefinition(segmentID).getCondition(), null, User.class);
    }

    public long getMatchingIndividualsCount(String segmentID) {
        if (getSegmentDefinition(segmentID) == null) {
            return 0;
        }
        return persistenceService.queryCount(getSegmentDefinition(segmentID).getCondition(), User.class);
    }

    public Boolean isUserInSegment(User user, String segmentId) {
        Set<String> matchingSegments = getSegmentsForUser(user);

        return matchingSegments.contains(segmentId);
    }

    public Set<String> getSegmentsForUser(User user) {
        return new HashSet<String>(persistenceService.getMatchingSavedQueries(user));
    }

    public Set<Metadata> getSegmentMetadatas() {
        Set<Metadata> descriptions = new HashSet<Metadata>();
        for (SegmentDefinition definition : persistenceService.getAllItems(SegmentDefinition.class).getList()) {
            descriptions.add(definition.getMetadata());
        }
        return descriptions;
    }

    public SegmentDefinition getSegmentDefinition(String segmentId) {
        SegmentDefinition definition = persistenceService.load(segmentId, SegmentDefinition.class);
        if (definition != null) {
            ParserHelper.resolveConditionType(definitionsService, definition.getCondition());
        }
        return definition;
    }

    public void setSegmentDefinition(String segmentId, SegmentDefinition segmentDefinition) {
        ParserHelper.resolveConditionType(definitionsService, segmentDefinition.getCondition());
        createAutoGeneratedRules(segmentDefinition.getCondition());
        persistenceService.saveQuery(segmentId, segmentDefinition.getCondition());
        // make sure we update the name and description metadata that might not match, so first we remove the entry from the map
        persistenceService.save(segmentDefinition);
    }

    public void createSegmentDefinition(String segmentId, String name, String description) {
        Metadata metadata = new Metadata(segmentId, name, description);
        SegmentDefinition segmentDefinition = new SegmentDefinition(metadata);
        Condition rootCondition = new Condition();
        rootCondition.setConditionType(definitionsService.getConditionType("andCondition"));
        rootCondition.getParameterValues().put("subConditions", new ArrayList<Condition>());
        segmentDefinition.setCondition(rootCondition);

        setSegmentDefinition(segmentId, segmentDefinition);
    }

    public void removeSegmentDefinition(String segmentId) {
        persistenceService.removeQuery(segmentId);
        persistenceService.remove(segmentId, SegmentDefinition.class);
    }

    public void createAutoGeneratedRules(Condition condition) {
        List<Rule> rules = new ArrayList<Rule>();
        getAutoGeneratedRules(condition, null, rules);
        for (Rule rule : rules) {
            rulesService.setRule(rule.getMetadata().getId(), rule);
        }
    }

    private void getAutoGeneratedRules(Condition condition, Condition parentCondition, List<Rule> rules) {
        if (condition.getConditionType().getTagIDs().contains("event")) {
            try {
                String key = CustomObjectMapper.getObjectMapper().writeValueAsString(CustomObjectMapper.getObjectMapper().writeValueAsString(parentCondition));
                key = "eventTriggered" + getMD5(key);
                condition.getParameterValues().put("generatedPropertyKey", key);
                if (rulesService.getRule(key) == null) {
                    Rule r = new Rule(new Metadata(key, "Auto generated rule", ""));
                    r.setCondition(condition);
                    final Action action = new Action();
                    action.setActionType(definitionsService.getActionType("setEventOccurenceCountAction"));
                    action.getParameterValues().put("eventCondition", parentCondition);
                    r.setActions(Arrays.asList(action));
                    rules.add(r);
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
                loadPredefinedSegments(event.getBundle().getBundleContext());
                break;
            case BundleEvent.STOPPING:
                // @todo remove bundle-defined resources (is it possible ?)
                break;
        }
    }
}
