package org.oasis_open.wemi.context.server.impl.services;

import org.apache.cxf.helpers.IOUtils;
import org.oasis_open.wemi.context.server.api.conditions.Tag;
import org.oasis_open.wemi.context.server.api.conditions.ConditionType;
import org.oasis_open.wemi.context.server.api.consequences.ConsequenceType;
import org.oasis_open.wemi.context.server.api.services.DefinitionsService;
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
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Default;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.json.*;
import java.net.URL;
import java.util.*;

@ApplicationScoped
@Default
@OsgiServiceProvider
public class DefinitionsServiceImpl implements DefinitionsService, BundleListener {

    private static final Logger logger = LoggerFactory.getLogger(DefinitionsServiceImpl.class.getName());

    Map<String, Tag> tags = new HashMap<String, Tag>();
    Set<Tag> rootTags = new LinkedHashSet<Tag>();
    Map<String, ConditionType> conditionTypeByName = new HashMap<String, ConditionType>();
    Map<String, ConsequenceType> consequencesTypeByName = new HashMap<String, ConsequenceType>();
    Map<Tag, Set<ConditionType>> conditionTypeByTag = new HashMap<Tag, Set<ConditionType>>();
    Map<Tag, Set<ConsequenceType>> consequenceTypeByTag = new HashMap<Tag, Set<ConsequenceType>>();

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

        loadPredefinedMappings(bundleContext);

        loadPredefinedTags(bundleContext);

        loadPredefinedCondition(bundleContext);
        loadPredefinedConsequences(bundleContext);

        bundleContext.addBundleListener(this);
    }

    @PreDestroy
    public void preDestroy() {
        bundleContext.removeBundleListener(this);
    }


    private void loadPredefinedMappings(BundleContext bundleContext) {
        Enumeration<URL> predefinedMappings = bundleContext.getBundle().findEntries("META-INF/wemi/mappings", "*.json", true);
        if (predefinedMappings == null) {
            return;
        }
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

    private void loadPredefinedTags(BundleContext bundleContext) {
        Enumeration<URL> predefinedTagEntries = bundleContext.getBundle().findEntries("META-INF/wemi/tags", "*.json", true);
        if (predefinedTagEntries == null) {
            return;
        }
        while (predefinedTagEntries.hasMoreElements()) {
            URL predefinedTagURL = predefinedTagEntries.nextElement();
            logger.debug("Found predefined tags at " + predefinedTagURL + ", loading... ");

            JsonReader reader = null;
            try {
                reader = Json.createReader(predefinedTagURL.openStream());
                JsonStructure jsonst = reader.read();

                // dumpJSON(jsonst, null, "");
                JsonObject tagObject = (JsonObject) jsonst;
                Tag tag = new Tag(tagObject.getString("id"),
                        tagObject.getString("name"),
                        tagObject.getString("description"),
                        tagObject.getString("parent"));

                tags.put(tag.getId(), tag);
            } catch (Exception e) {
                logger.error("Error while loading tag definition " + predefinedTagURL, e);
            } finally {
                if (reader != null) {
                    reader.close();
                }
            }

        }

        // now let's resolve all the children.
        for (Tag tag : tags.values()) {
            if (tag.getParentId() != null && tag.getParentId().length() > 0) {
                Tag parentTag = tags.get(tag.getParentId());
                if (parentTag != null) {
                    parentTag.getSubTags().add(tag);
                }
            } else {
                rootTags.add(tag);
            }
        }
    }

    private void loadPredefinedCondition(BundleContext bundleContext) {
        Enumeration<URL> predefinedConditionEntries = bundleContext.getBundle().findEntries("META-INF/wemi/conditions", "*.json", true);
        if (predefinedConditionEntries == null) {
            return;
        }
        while (predefinedConditionEntries.hasMoreElements()) {
            URL predefinedConditionURL = predefinedConditionEntries.nextElement();
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
                String queryBuilderFilter = conditionObject.getString("queryBuilderFilter");
                JsonArray tagArray = conditionObject.getJsonArray("tags");
                Set<String> tagIds = new LinkedHashSet<String>();
                for (int i = 0; i < tagArray.size(); i++) {
                    tagIds.add(tagArray.getString(i));
                }

                ConditionType conditionType = new ConditionType(id, name);
                conditionType.setDescription(description);
                conditionType.setQueryBuilderFilter(queryBuilderFilter);
                conditionType.setParameters(ParserHelper.parseParameters(conditionObject));
                conditionType.setTagIDs(tagIds);

                conditionTypeByName.put(conditionType.getId(), conditionType);
                for (String tagId : tagIds) {
                    Tag tag = tags.get(tagId);
                    if (tag != null) {
                        conditionType.getTags().add(tag);
                        Set<ConditionType> conditionTypes = conditionTypeByTag.get(tag);
                        if (conditionTypes == null) {
                            conditionTypes = new LinkedHashSet<ConditionType>();
                        }
                        conditionTypes.add(conditionType);
                        conditionTypeByTag.put(tag, conditionTypes);
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

    private void loadPredefinedConsequences(BundleContext bundleContext) {
        Enumeration<URL> predefinedConsequencesEntries = bundleContext.getBundle().findEntries("META-INF/wemi/consequences", "*.json", true);
        if (predefinedConsequencesEntries == null) {
            return;
        }
        while (predefinedConsequencesEntries.hasMoreElements()) {
            URL predefinedConsequenceURL = predefinedConsequencesEntries.nextElement();
            logger.debug("Found predefined consequence at " + predefinedConsequenceURL + ", loading... ");

            JsonReader reader = null;
            try {
                reader = Json.createReader(predefinedConsequenceURL.openStream());
                JsonStructure jsonst = reader.read();

                // dumpJSON(jsonst, null, "");
                JsonObject conditionObject = (JsonObject) jsonst;

                String id = conditionObject.getString("id");
                String name = conditionObject.getString("name");
                String description = conditionObject.getString("description");
                String serviceFilter = conditionObject.getString("serviceFilter");
                JsonArray tagArray = conditionObject.getJsonArray("tags");
                Set<String> tagIds = new LinkedHashSet<String>();
                for (int i = 0; i < tagArray.size(); i++) {
                    tagIds.add(tagArray.getString(i));
                }

                ConsequenceType consequenceType = new ConsequenceType(id, name);

                consequenceType.setDescription(description);
                consequenceType.setParameters(ParserHelper.parseParameters(conditionObject));
                consequenceType.setTagIds(tagIds);
                consequenceType.setServiceFilter(serviceFilter);

                consequencesTypeByName.put(consequenceType.getId(), consequenceType);
                for (String tagId : tagIds) {
                    Tag tag = tags.get(tagId);
                    if (tag != null) {
                        consequenceType.getTags().add(tag);
                        Set<ConsequenceType> consequenceTypes = consequenceTypeByTag.get(tag);
                        if (consequenceTypes == null) {
                            consequenceTypes = new LinkedHashSet<ConsequenceType>();
                        }
                        consequenceTypes.add(consequenceType);
                        consequenceTypeByTag.put(tag, consequenceTypes);
                    } else {
                        // we found a tag that is not defined, we will define it automatically
                        logger.warn("Unknown tag " + tagId + " used in consequence definition " + predefinedConsequenceURL);
                    }
                }

            } catch (Exception e) {
                logger.error("Error while loading consequence definition " + predefinedConsequenceURL, e);
            } finally {
                if (reader != null) {
                    reader.close();
                }
            }
        }

    }

    public Set<Tag> getAllTags() {
        return new HashSet<Tag>(tags.values());
    }

    public Set<Tag> getRootTags() {
        return rootTags;
    }

    public Tag getTag(Tag tag) {
        Tag completeTag = tags.get(tag.getId());
        if (completeTag == null) {
            return null;
        }
        return completeTag;
    }

    public Collection<ConditionType> getAllConditionTypes() {
        return conditionTypeByName.values();
    }

    public Set<ConditionType> getConditionTypesByTag(Tag tag) {
        return conditionTypeByTag.get(tag);
    }

    public ConditionType getConditionType(String name) {
        return conditionTypeByName.get(name);
    }

    public Collection<ConsequenceType> getAllConsequenceTypes() {
        return consequencesTypeByName.values();
    }

    public Set<ConsequenceType> getConsequenceTypeByTag(Tag tag) {
        return null;
    }

    public ConsequenceType getConsequenceType(String name) {
        return consequencesTypeByName.get(name);
    }

    public void bundleChanged(BundleEvent event) {
        switch (event.getType()) {
            case BundleEvent.STARTED:
                loadPredefinedMappings(event.getBundle().getBundleContext());
                loadPredefinedTags(event.getBundle().getBundleContext());
                loadPredefinedCondition(event.getBundle().getBundleContext());
                loadPredefinedConsequences(event.getBundle().getBundleContext());
                break;
            case BundleEvent.STOPPING:
                // @todo remove bundle-defined resources (is it possible ?)
                break;
        }
    }
}
