package org.oasis_open.wemi.context.server.impl.services;

import org.apache.cxf.helpers.IOUtils;
import org.oasis_open.wemi.context.server.api.conditions.ConditionTag;
import org.oasis_open.wemi.context.server.api.conditions.ConditionType;
import org.oasis_open.wemi.context.server.api.conditions.Parameter;
import org.oasis_open.wemi.context.server.api.consequences.ConsequenceType;
import org.oasis_open.wemi.context.server.api.services.DefinitionsService;
import org.oasis_open.wemi.context.server.persistence.spi.PersistenceService;
import org.ops4j.pax.cdi.api.OsgiService;
import org.ops4j.pax.cdi.api.OsgiServiceProvider;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.enterprise.inject.Default;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.json.*;
import java.net.URL;
import java.util.*;

@Singleton
@Default
@OsgiServiceProvider
public class DefinitionsServiceImpl implements DefinitionsService {

    private static final Logger logger = LoggerFactory.getLogger(DefinitionsServiceImpl.class.getName());

    Map<String, ConditionTag> conditionTags = new HashMap<String, ConditionTag>();
    Set<ConditionTag> rootConditionTags = new LinkedHashSet<ConditionTag>();
    Map<String, ConditionType> conditionTypeByName = new HashMap<String, ConditionType>();
    Map<String, ConsequenceType> consequencesTypeByName = new HashMap<String, ConsequenceType>();
    Map<ConditionTag, Set<ConditionType>> conditionTypeByTag = new HashMap<ConditionTag, Set<ConditionType>>();

    public DefinitionsServiceImpl() {
        System.out.println("Instantiating definitions service...");
    }

    @Inject
    private BundleContext bundleContext;

    @Inject
    @OsgiService
    private PersistenceService persistenceService;

    @PostConstruct
    public void postConstruct() {
        logger.debug("postConstruct {" + bundleContext.getBundle() + "}");

        loadPredefinedMappings();

        loadPredefinedTags();

        loadPredefinedCondition();
        loadPredefinedConsequences();
    }


    private void loadPredefinedMappings() {
        Enumeration<URL> predefinedMappings = bundleContext.getBundle().findEntries("META-INF/mappings", "*.json", true);
        while (predefinedMappings.hasMoreElements()) {
            URL predefinedMappingURL = predefinedMappings.nextElement();
            logger.debug("Found mapping at " + predefinedMappingURL + ", loading... ");
            try {
                final String path = predefinedMappingURL.getPath();
                String name = path.substring(path.lastIndexOf('/') + 1, path.lastIndexOf('.'));
                String content = IOUtils.readStringFromStream(predefinedMappingURL.openStream());
                persistenceService.createMapping(name, content);
            } catch (Exception e) {
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
            } catch (Exception e) {
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

    private void loadPredefinedCondition() {
        Enumeration<URL> predefinedSegmentEntries = bundleContext.getBundle().findEntries("META-INF/conditions", "*.json", true);
        while (predefinedSegmentEntries.hasMoreElements()) {
            URL predefinedConditionURL = predefinedSegmentEntries.nextElement();
            logger.debug("Found predefined conditions at " + predefinedConditionURL + ", loading... ");

            JsonReader reader = null;
            try {
                reader = Json.createReader(predefinedConditionURL.openStream());
                JsonStructure jsonst = reader.read();

                // dumpJSON(jsonst, null, "");
                JsonObject conditionObject = (JsonObject) jsonst;

                String id = conditionObject.getString("id");
                String name = conditionObject.getString("name");
                String description = conditionObject.getString("description");
                JsonArray tagArray = conditionObject.getJsonArray("tags");
                Set<String> tagIds = new LinkedHashSet<String>();
                for (int i = 0; i < tagArray.size(); i++) {
                    tagIds.add(tagArray.getString(i));
                }

                ConditionType conditionNode = new ConditionType(id, name);

                conditionNode.setDescription(description);
                JsonArray parameterArray = conditionObject.getJsonArray("parameters");
                for (int i = 0; i < parameterArray.size(); i++) {
                    JsonObject parameterObject = parameterArray.getJsonObject(i);
                    String paramId = parameterObject.getString("id");
                    String paramName = parameterObject.getString("name");
                    String paramDescription = parameterObject.getString("description");
                    String paramType = parameterObject.getString("type");
                    boolean multivalued = parameterObject.getBoolean("multivalued");
                    String paramChoiceListInitializerClass = null;
                    try {
                        paramChoiceListInitializerClass = parameterObject.getString("choicelistInitializerClass");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    Parameter conditionParameter = new Parameter(paramId, paramName, paramDescription, paramType, multivalued, paramChoiceListInitializerClass);
                    conditionNode.getConditionParameters().add(conditionParameter);
                }

                conditionTypeByName.put(conditionNode.getId(), conditionNode);
                for (String tagId : tagIds) {
                    ConditionTag conditionTag = conditionTags.get(tagId);
                    if (conditionTag != null) {
                        Set<ConditionType> conditionNodes = conditionTypeByTag.get(conditionTag);
                        if (conditionNodes == null) {
                            conditionNodes = new LinkedHashSet<ConditionType>();
                        }
                        conditionNodes.add(conditionNode);
                        conditionTypeByTag.put(conditionTag, conditionNodes);
                    } else {
                        // we found a tag that is not defined, we will define it automatically
                        logger.warn("Unknown tag " + tagId + " used in condition definition " + predefinedConditionURL);
                    }
                }
            } catch (Exception e) {
                logger.error("Error while loading condition definition " + predefinedConditionURL, e);
            } finally {
                if (reader != null) {
                    reader.close();
                }
            }

        }

    }

    private void loadPredefinedConsequences() {
        Enumeration<URL> predefinedSegmentEntries = bundleContext.getBundle().findEntries("META-INF/consequences", "*.json", true);
        while (predefinedSegmentEntries.hasMoreElements()) {
            URL predefinedConditionURL = predefinedSegmentEntries.nextElement();
            logger.debug("Found predefined consequence at " + predefinedConditionURL + ", loading... ");

            JsonReader reader = null;
            try {
                reader = Json.createReader(predefinedConditionURL.openStream());
                JsonStructure jsonst = reader.read();

                // dumpJSON(jsonst, null, "");
                JsonObject conditionObject = (JsonObject) jsonst;

                String id = conditionObject.getString("id");
                String name = conditionObject.getString("name");
                String description = conditionObject.getString("description");
                String clazz = conditionObject.getString("class");
//                JsonArray tagArray = conditionObject.getJsonArray("tags");
//                Set<String> tagIds = new LinkedHashSet<String>();
//                for (int i = 0; i < tagArray.size(); i++) {
//                    tagIds.add(tagArray.getString(i));
//                }
//
                ConsequenceType consequence = new ConsequenceType(id, name);

                consequence.setDescription(description);
                consequence.setClazz(clazz);

                JsonArray parameterArray = conditionObject.getJsonArray("parameters");
                for (int i = 0; i < parameterArray.size(); i++) {
                    JsonObject parameterObject = parameterArray.getJsonObject(i);
                    String paramId = parameterObject.getString("id");
                    String paramName = parameterObject.getString("name");
                    String paramDescription = parameterObject.getString("description");
                    String paramType = parameterObject.getString("type");
                    boolean multivalued = parameterObject.getBoolean("multivalued");
                    String paramChoiceListInitializerClass = parameterObject.getString("choicelistInitializerClass");
                    Parameter conditionParameter = new Parameter(paramId, paramName, paramDescription, paramType, multivalued, paramChoiceListInitializerClass);
                    consequence.getConsequenceParameters().add(conditionParameter);
                }

                consequencesTypeByName.put(consequence.getId(), consequence);
            } catch (Exception e) {
                logger.error("Error while loading condition definition " + predefinedConditionURL, e);
            } finally {
                if (reader != null) {
                    reader.close();
                }
            }
        }

    }


    public ConditionType getConditionType(String name) {
        return conditionTypeByName.get(name);
    }

    public ConsequenceType getConsequenceType(String name) {
        return consequencesTypeByName.get(name);
    }

    public Set<ConditionTag> getConditionTags() {
        return new HashSet<ConditionTag>(conditionTags.values());
    }

    public Set<ConditionType> getConditions(ConditionTag conditionTag) {
        return conditionTypeByTag.get(conditionTag);
    }

}
