package org.oasis_open.wemi.context.server.impl.services;

import org.apache.cxf.helpers.IOUtils;
import org.oasis_open.wemi.context.server.api.SegmentDefinition;
import org.oasis_open.wemi.context.server.api.SegmentID;
import org.oasis_open.wemi.context.server.api.User;
import org.oasis_open.wemi.context.server.api.conditions.*;
import org.oasis_open.wemi.context.server.api.services.SegmentService;
import org.oasis_open.wemi.context.server.impl.conditions.ConditionNodeESQueryGeneratorVisitor;
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
    Map<String, ConditionTag> conditionTags = new HashMap<String, ConditionTag>();
    Set<ConditionTag> rootConditionTags = new LinkedHashSet<ConditionTag>();
    Map<String, ConditionTypeNode> conditionTypeNodesByName = new HashMap<String, ConditionTypeNode>();
    Map<ConditionTag, Set<ConditionTypeNode>> conditionTypeNodesByTag = new HashMap<ConditionTag, Set<ConditionTypeNode>>();

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

                ConditionTypeNode conditionNode = new ConditionTypeNode(id, name);

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

                conditionTypeNodesByName.put(conditionNode.getId(), conditionNode);
                for (String tagId : tagIds) {
                    ConditionTag conditionTag = conditionTags.get(tagId);
                    if (conditionTag != null) {
                        Set<ConditionTypeNode> conditionNodes = conditionTypeNodesByTag.get(conditionTag);
                        if (conditionNodes == null) {
                            conditionNodes = new LinkedHashSet<ConditionTypeNode>();
                        }
                        conditionNodes.add(conditionNode);
                        conditionTypeNodesByTag.put(conditionTag, conditionNodes);
                    } else {
                        // we found a tag that is not defined, we will define it automatically
                        logger.warn("Unknown tag " + tagId + " used in condition definition " + predefinedConditionNodeURL);
                    }
                }
            } catch (IOException e) {
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

                SegmentDefinition segment = new SegmentDefinition();

                JsonObject conditionObject = segmentObject.getJsonObject("condition");
                if (conditionObject != null) {
                    ConditionNode node = getConditionNode(conditionObject);
                    segment.setRootConditionNode(node);

                    new ConditionNodeESQueryGeneratorVisitor().visit(node);
                } else {
                    String segmentType = segmentObject.getString("type");
                    if ("es-query".equals(segmentType)) {
                        JsonObject queryObject = segmentObject.getJsonObject("definition");
                        StringWriter queryStringWriter = new StringWriter();
                        JsonWriter jsonWriter = Json.createWriter(queryStringWriter);
                        jsonWriter.writeObject(queryObject);
                        jsonWriter.close();
                        segment.setExpression(queryStringWriter.toString());
                        persistenceService.saveQuery(segmentID.getId(), queryStringWriter.toString());
                    }
                }
                segmentQueries.put(segmentID, segment);
            } catch (IOException e) {
                logger.error("Error while loading segment definition " + predefinedSegmentURL, e);
            } finally {
                if (reader != null) {
                    reader.close();
                }
            }

        }
    }

    private ConditionNode getConditionNode(JsonObject object) {
        String conditionType = object.getString("type");
        ConditionTypeNode typeNode = conditionTypeNodesByName.get(conditionType);
        JsonObject parameterValues = object.getJsonObject("parameterValues");

        ConditionNode node = new ConditionNode();
        node.setConditionTypeNode(typeNode);
        List<ConditionParameterValue> values = new ArrayList<ConditionParameterValue>();
        node.setConditionParameterValues(values);

        for (ConditionParameter parameter : typeNode.getConditionParameters()) {
            final ArrayList<Object> objects = new ArrayList<Object>();
            values.add(new ConditionParameterValue(parameter.getName(), objects));

            if (parameter.isMultivalued()) {
                JsonArray array = parameterValues.getJsonArray(parameter.getId());
                for (JsonValue value : array) {
                    objects.add(getParameterValue(parameter, value));
                }
            } else {
                objects.add(getParameterValue(parameter, parameterValues.get(parameter.getId())));
            }
        }
        return node;
    }

    private Object getParameterValue(ConditionParameter parameter, JsonValue value) {
        if (parameter.getType().equals("ConditionNode")) {
            return getConditionNode((JsonObject) value);
        } else if (parameter.getType().equals("comparisonOperator")) {
            return ((JsonString)value).getString();
        } else if (parameter.getType().equals("string")) {
            return ((JsonString)value).getString();
        }
        return null;
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
        return segmentQueries.get(segmentID);
    }

    public Set<ConditionTag> getConditionTags() {
        return new HashSet<ConditionTag>(conditionTags.values());
    }

    public Set<ConditionTypeNode> getConditions(ConditionTag conditionTag) {
        return conditionTypeNodesByTag.get(conditionTag);
    }

    public List<ConditionParameter> getConditionParameters(ConditionTypeNode condition) {
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
