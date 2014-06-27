package org.oasis_open.wemi.context.server.impl.services;

import org.oasis_open.wemi.context.server.api.SegmentDefinition;
import org.oasis_open.wemi.context.server.api.SegmentID;
import org.oasis_open.wemi.context.server.api.User;
import org.oasis_open.wemi.context.server.api.conditions.*;
import org.oasis_open.wemi.context.server.api.services.DefinitionsService;
import org.oasis_open.wemi.context.server.api.services.SegmentService;
import org.oasis_open.wemi.context.server.persistence.spi.PersistenceService;
import org.ops4j.pax.cdi.api.OsgiService;
import org.ops4j.pax.cdi.api.OsgiServiceProvider;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.json.*;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URL;
import java.util.*;

/**
 * Created by loom on 26.04.14.
 */
@Singleton
@OsgiServiceProvider
public class SegmentServiceImpl implements SegmentService {

    private static final Logger logger = LoggerFactory.getLogger(SegmentServiceImpl.class.getName());

    Map<SegmentID, SegmentDefinition> segmentQueries = new LinkedHashMap<SegmentID, SegmentDefinition>();

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
        loadPredefinedSegments();
    }

    private void loadPredefinedSegments() {
        Enumeration<URL> predefinedSegmentEntries = bundleContext.getBundle().findEntries("META-INF/segments", "*.json", true);
        while (predefinedSegmentEntries.hasMoreElements()) {
            URL predefinedSegmentURL = predefinedSegmentEntries.nextElement();
            logger.debug("Found predefined segment at " + predefinedSegmentURL + ", loading... ");

            JsonReader reader = null;
            try {
                reader = Json.createReader(predefinedSegmentURL.openStream());
                JsonStructure jsonst = reader.read();

                // dumpJSON(jsonst, null, "");
                JsonObject segmentObject = (JsonObject) jsonst;
                SegmentID segmentID = new SegmentID(segmentObject.getString("id"), segmentObject.getString("name"), segmentObject.getString("description"));

                SegmentDefinition segment = new SegmentDefinition();

                Condition condition = ParserHelper.parseCondition(definitionsService, segmentObject.getJsonObject("condition"));
                segment.setRootCondition(condition);
                persistenceService.saveQuery(segmentID.getId(), condition);

                segmentQueries.put(segmentID, segment);
            } catch (Exception e) {
                logger.error("Error while loading segment definition " + predefinedSegmentURL, e);
            } finally {
                if (reader != null) {
                    reader.close();
                }
            }

        }
    }

    public Set<User> getMatchingIndividuals(SegmentID segmentID) {
        return null;
    }

    public Boolean isUserInSegment(User user, SegmentID segmentID) {

        Set<SegmentID> matchingSegments = getSegmentsForUser(user);

        return matchingSegments.contains(segmentID);
    }

    public Set<SegmentID> getSegmentsForUser(User user) {

        Set<SegmentID> matchedSegments = new LinkedHashSet<SegmentID>();

        List<String> matchingQueries = persistenceService.getMatchingSavedQueries(user);
        if (matchingQueries.size() > 0) {
            for (String matchingQuery : matchingQueries) {
                for (SegmentID segmentID : segmentQueries.keySet()) {
                    if (matchingQuery.equals(segmentID.getId())) {
                        matchedSegments.add(segmentID);
                    }
                }
            }
        }

        return matchedSegments;
    }

    public Set<SegmentID> getSegmentIDs() {
        return segmentQueries.keySet();
    }

    public SegmentDefinition getSegmentDefinition(SegmentID segmentID) {
        return segmentQueries.get(segmentID);
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

}
