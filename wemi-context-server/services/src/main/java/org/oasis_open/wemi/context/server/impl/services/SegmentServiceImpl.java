package org.oasis_open.wemi.context.server.impl.services;

import org.oasis_open.wemi.context.server.api.SegmentDefinition;
import org.oasis_open.wemi.context.server.api.SegmentDescription;
import org.oasis_open.wemi.context.server.api.User;
import org.oasis_open.wemi.context.server.api.conditions.*;
import org.oasis_open.wemi.context.server.api.services.DefinitionsService;
import org.oasis_open.wemi.context.server.api.services.SegmentService;
import org.oasis_open.wemi.context.server.persistence.spi.PersistenceService;
import org.ops4j.pax.cdi.api.OsgiService;
import org.ops4j.pax.cdi.api.OsgiServiceProvider;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.json.*;
import java.net.URL;
import java.util.*;

/**
 * Created by loom on 26.04.14.
 */
@Singleton
@OsgiServiceProvider
public class SegmentServiceImpl implements SegmentService, BundleListener {

    private static final Logger logger = LoggerFactory.getLogger(SegmentServiceImpl.class.getName());

    Map<String, SegmentDefinition> segmentQueries = new LinkedHashMap<String, SegmentDefinition>();

    @Inject
    private BundleContext bundleContext;

    @Inject
    @OsgiService
    private PersistenceService persistenceService;

    @Inject
    private DefinitionsService definitionsService;

    public SegmentServiceImpl() {
        logger.info("Initializing segment service...");
    }

    @PostConstruct
    public void postConstruct() {
        logger.debug("postConstruct {" + bundleContext.getBundle() + "}");
        loadPredefinedSegments(bundleContext);
        bundleContext.addBundleListener(this);
    }

    @PreDestroy
    public void preDestroy() {
        bundleContext.removeBundleListener(this);
    }

    private void loadPredefinedSegments(BundleContext bundleContext) {
        Enumeration<URL> predefinedSegmentEntries = bundleContext.getBundle().findEntries("META-INF/wemi/segments", "*.json", true);
        if (predefinedSegmentEntries == null) {
            return;
        }
        while (predefinedSegmentEntries.hasMoreElements()) {
            URL predefinedSegmentURL = predefinedSegmentEntries.nextElement();
            logger.debug("Found predefined segment at " + predefinedSegmentURL + ", loading... ");

            JsonReader reader = null;
            try {
                reader = Json.createReader(predefinedSegmentURL.openStream());
                JsonStructure jsonst = reader.read();

                // dumpJSON(jsonst, null, "");
                JsonObject segmentObject = (JsonObject) jsonst;
                SegmentDescription segmentDescription = new SegmentDescription(segmentObject.getString("id"), segmentObject.getString("name"), segmentObject.getString("description"));

                SegmentDefinition segment = new SegmentDefinition(segmentDescription);

                Condition condition = ParserHelper.parseCondition(definitionsService, segmentObject.getJsonObject("condition"));
                segment.setRootCondition(condition);
                persistenceService.saveQuery(segmentDescription.getId(), condition);

                segmentQueries.put(segmentDescription.getId(), segment);
            } catch (Exception e) {
                logger.error("Error while loading segment definition " + predefinedSegmentURL, e);
            } finally {
                if (reader != null) {
                    reader.close();
                }
            }
        }

        /* Deactivated temporarily because the saveQuery doesn't work since the conditionTypes are not properly initialized
        predefinedSegmentEntries = bundleContext.getBundle().findEntries("META-INF/segments", "*.json.jackson", true);
        while (predefinedSegmentEntries.hasMoreElements()) {
            URL predefinedSegmentURL = predefinedSegmentEntries.nextElement();
            try {
                SegmentDefinition segment = ParserHelper.getObjectMapper().readValue(predefinedSegmentURL, SegmentDefinition.class);
                ParserHelper.resolveConditionTypes(segment.getRootCondition());
                persistenceService.saveQuery(segment.getSegmentID().getId(), segment.getRootCondition());
                segmentQueries.put(segment.getSegmentID(), segment);
            } catch (IOException e) {
                logger.error("Error while loading segment definition " + predefinedSegmentURL, e);
            }
        }
        */
    }

    public Set<User> getMatchingIndividuals(String segmentDescription) {
        return null;
    }

    public Boolean isUserInSegment(User user, String segmentId) {
        Set<String> matchingSegments = getSegmentsForUser(user);

        return matchingSegments.contains(segmentId);
    }

    public Set<String> getSegmentsForUser(User user) {
        return new HashSet<String>(persistenceService.getMatchingSavedQueries(user));
    }

    public Set<SegmentDescription> getSegmentDescriptions() {
        Set<SegmentDescription> descriptions = new HashSet<SegmentDescription>();
        for (SegmentDefinition definition : segmentQueries.values()) {
            descriptions.add(definition.getSegmentDescription());
        }
        return descriptions;
    }

    public SegmentDefinition getSegmentDefinition(String segmentId) {
        return segmentQueries.get(segmentId);
    }

    public void setSegmentDefinition(String segmentId, SegmentDefinition segmentDefinition) {
        persistenceService.saveQuery(segmentId, segmentDefinition.getRootCondition());
        // make sure we update the name and description metadata that might not match, so first we remove the entry from the map
        segmentQueries.remove(segmentId);
        segmentQueries.put(segmentId, segmentDefinition);
    }

    @Override
    public void createSegmentDefinition(String segmentId, String name, String description) {
        SegmentDescription segmentDescription = new SegmentDescription(segmentId, name, description);
        SegmentDefinition segmentDefinition = new SegmentDefinition(segmentDescription);
        Condition rootCondition = new Condition();
        rootCondition.setConditionType(definitionsService.getConditionType("andCondition"));
        rootCondition.getParameterValues().put("subConditions", new ArrayList<Condition>());
        segmentDefinition.setRootCondition(rootCondition);

        setSegmentDefinition(segmentId, segmentDefinition);
    }

    public void removeSegmentDefinition(String segmentId) {
        persistenceService.removeQuery(segmentId);
        segmentQueries.remove(segmentId);
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
