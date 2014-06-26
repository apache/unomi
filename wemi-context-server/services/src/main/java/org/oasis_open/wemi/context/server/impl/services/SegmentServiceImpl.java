package org.oasis_open.wemi.context.server.impl.services;

import org.apache.cxf.helpers.IOUtils;
import org.oasis_open.wemi.context.server.api.SegmentDefinition;
import org.oasis_open.wemi.context.server.api.SegmentID;
import org.oasis_open.wemi.context.server.api.User;
import org.oasis_open.wemi.context.server.api.conditions.ConditionNode;
import org.oasis_open.wemi.context.server.api.conditions.ConditionParameter;
import org.oasis_open.wemi.context.server.api.conditions.ConditionTag;
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
import java.io.Serializable;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.*;

/**
 * Created by loom on 26.04.14.
 */
@Singleton
@OsgiServiceProvider
public class SegmentServiceImpl implements SegmentService {

    private static final Logger logger = LoggerFactory.getLogger(SegmentServiceImpl.class.getName());

    Map<SegmentID, Serializable> segmentQueries = new LinkedHashMap<SegmentID, Serializable>();
    Map<String, ConditionTag> conditionTags = new HashMap<String, ConditionTag>();
    Set<ConditionTag> rootConditionTags = new LinkedHashSet<ConditionTag>();
    Map<String, ConditionNode> conditionNodesByName = new HashMap<String, ConditionNode>();
    Map<ConditionTag, Set<ConditionNode>> conditionNodesByTag = new HashMap<ConditionTag, Set<ConditionNode>>();

    @Inject
    private BundleContext bundleContext;

    @Inject
    @OsgiService
    private PersistenceService persistenceService;

    public SegmentServiceImpl() {
        logger.info("Initializing segment service...");
    }

    @PostConstruct
    public void postConstruct() {
        logger.debug("postConstruct {" + bundleContext.getBundle() + "}");

        loadPredefinedMappings();

        loadPredefinedTags();

        loadPredefinedConditionNodes();

        loadPredefinedSegments();
    }

    private void loadPredefinedMappings() {
        Enumeration<URL> predefinedMappings = bundleContext.getBundle().findEntries("META-INF/mappings", "*.json", true);
        while (predefinedMappings.hasMoreElements()) {
            URL predefinedMappingURL = predefinedMappings.nextElement();
            logger.debug("Found mapping at " + predefinedMappingURL + ", loading... ");
             try {
                 final String path = predefinedMappingURL.getPath();
                 String name = path.substring(path.lastIndexOf('/')+1, path.lastIndexOf('.'));
                 String content = IOUtils.readStringFromStream(predefinedMappingURL.openStream());
                persistenceService.createMapping(name, content);
            } catch (IOException e) {
                logger.error("Error while loading segment definition " + predefinedMappingURL, e);
            }
        }
    }

    private void loadPredefinedTags() {
        Enumeration<URL> predefinedSegmentEntries = bundleContext.getBundle().findEntries("META-INF/tags", "*.json", true);
        while (predefinedSegmentEntries.hasMoreElements()) {
            URL predefinedSegmentURL = predefinedSegmentEntries.nextElement();
            logger.debug("Found predefined tags at " + predefinedSegmentURL + ", loading... ");

            JsonReader reader = null;
            try {
                reader = Json.createReader(predefinedSegmentURL.openStream());
                JsonStructure jsonst = reader.read();

                // dumpJSON(jsonst, null, "");
                JsonObject tagObject = (JsonObject) jsonst;
                ConditionTag conditionTag = new ConditionTag(tagObject.getString("id"),
                    tagObject.getString("name"),
                    tagObject.getString("description"),
                    tagObject.getString("parent"));

                conditionTags.put(conditionTag.getId(), conditionTag);
            } catch (IOException e) {
                logger.error("Error while loading tag definition " + predefinedSegmentURL, e);
            } finally {
                if (reader != null) {
                    reader.close();
                }
            }

        }

        // now let's resolve all the children.
        for (ConditionTag conditionTag : conditionTags.values()) {
            if (conditionTag.getParentId() != null && conditionTag.getParentId().length() > 0) {
                ConditionTag parentTag = conditionTags.get(conditionTag.getParentId());
                if (parentTag != null) {
                    parentTag.getSubTags().add(conditionTag);
                }
            } else {
                rootConditionTags.add(conditionTag);
            }
        }
    }

    private void loadPredefinedConditionNodes() {
        Enumeration<URL> predefinedSegmentEntries = bundleContext.getBundle().findEntries("META-INF/conditions", "*.json", true);
        while (predefinedSegmentEntries.hasMoreElements()) {
            URL predefinedConditionNodeURL = predefinedSegmentEntries.nextElement();
            logger.debug("Found predefined conditions at " + predefinedConditionNodeURL + ", loading... ");

            JsonReader reader = null;
            try {
                reader = Json.createReader(predefinedConditionNodeURL.openStream());
                JsonStructure jsonst = reader.read();

                // dumpJSON(jsonst, null, "");
                JsonObject conditionObject = (JsonObject) jsonst;

                String id = conditionObject.getString("id");
                String name = conditionObject.getString("name");
                String description = conditionObject.getString("description");
                JsonArray tagArray = conditionObject.getJsonArray("tags");
                Set<String> tagIds = new LinkedHashSet<String>();
                for (int i=0; i < tagArray.size(); i++) {
                    tagIds.add(tagArray.getString(i));
                }
                String clazz = conditionObject.getString("class");

                Class conditionNodeClass = bundleContext.getBundle().loadClass(clazz);

                Constructor conditionNodeConstructor = conditionNodeClass.getConstructor(String.class, String.class);

                ConditionNode conditionNode = (ConditionNode) conditionNodeConstructor.newInstance(id, name);

                conditionNode.setDescription(description);
                JsonArray parameterArray = conditionObject.getJsonArray("parameters");
                for (int i=0; i < parameterArray.size(); i++) {
                    JsonObject parameterObject = parameterArray.getJsonObject(i);
                    String paramId = parameterObject.getString("id");
                    String paramName = parameterObject.getString("name");
                    String paramDescription = parameterObject.getString("description");
                    String paramType = parameterObject.getString("type");
                    boolean multivalued = parameterObject.getBoolean("multivalued");
                    String paramChoiceListInitializerClass = parameterObject.getString("choicelistInitializerClass");
                    ConditionParameter conditionParameter = new ConditionParameter(paramId, paramName, paramDescription, paramType, multivalued, paramChoiceListInitializerClass);
                    conditionNode.getConditionParameters().add(conditionParameter);
                }

                conditionNodesByName.put(conditionNode.getId(), conditionNode);
                for (String tagId : tagIds) {
                    ConditionTag conditionTag = conditionTags.get(tagId);
                    if (conditionTag != null) {
                        Set<ConditionNode> conditionNodes = conditionNodesByTag.get(conditionTag);
                        if (conditionNodes == null) {
                            conditionNodes = new LinkedHashSet<ConditionNode>();
                        }
                        conditionNodes.add(conditionNode);
                        conditionNodesByTag.put(conditionTag, conditionNodes);
                    } else {
                        // we found a tag that is not defined, we will define it automatically
                        logger.warn("Unknown tag " + tagId + " used in condition definition " + predefinedConditionNodeURL);
                    }
                }
            } catch (IOException e) {
                logger.error("Error while loading condition definition " + predefinedConditionNodeURL, e);
            } catch (ClassNotFoundException e) {
                logger.error("Error while loading condition definition " + predefinedConditionNodeURL, e);
            } catch (InstantiationException e) {
                logger.error("Error while loading condition definition " + predefinedConditionNodeURL, e);
            } catch (IllegalAccessException e) {
                logger.error("Error while loading condition definition " + predefinedConditionNodeURL, e);
            } catch (NoSuchMethodException e) {
                logger.error("Error while loading condition definition " + predefinedConditionNodeURL, e);
            } catch (InvocationTargetException e) {
                logger.error("Error while loading condition definition " + predefinedConditionNodeURL, e);
            } finally {
                if (reader != null) {
                    reader.close();
                }
            }

        }

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
                String segmentType = segmentObject.getString("type");
                if ("es-query".equals(segmentType)) {
                    JsonObject queryObject = segmentObject.getJsonObject("definition");
                    StringWriter queryStringWriter = new StringWriter();
                    JsonWriter jsonWriter = Json.createWriter(queryStringWriter);
                    jsonWriter.writeObject(queryObject);
                    jsonWriter.close();
                    segmentQueries.put(segmentID, queryStringWriter.toString());
                    persistenceService.saveQuery(segmentID.getId(), queryStringWriter.toString());
                }
            } catch (IOException e) {
                logger.error("Error while loading segment definition " + predefinedSegmentURL, e);
            } finally {
                if (reader != null) {
                    reader.close();
                }
            }

        }
    }

    public Set<User> getMatchingIndividuals(List<SegmentID> segmentIDs) {
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
        String s = segmentQueries.get(segmentID).toString();

        return new SegmentDefinition(s);
    }

    public Set<ConditionTag> getConditionTags() {
        return new HashSet<ConditionTag>(conditionTags.values());
    }

    public Set<ConditionNode> getConditions(ConditionTag conditionTag) {
        return conditionNodesByTag.get(conditionTag);
    }

    public List<ConditionParameter> getConditionParameters(ConditionNode condition) {
        return condition.getConditionParameters();
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
