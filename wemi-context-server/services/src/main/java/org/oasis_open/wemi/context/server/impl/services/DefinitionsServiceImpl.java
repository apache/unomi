package org.oasis_open.wemi.context.server.impl.services;

import org.apache.cxf.helpers.IOUtils;
import org.oasis_open.wemi.context.server.api.conditions.Tag;
import org.oasis_open.wemi.context.server.api.conditions.ConditionType;
import org.oasis_open.wemi.context.server.api.consequences.ConsequenceType;
import org.oasis_open.wemi.context.server.api.services.DefinitionsService;
import org.oasis_open.wemi.context.server.persistence.spi.MapperHelper;
import org.oasis_open.wemi.context.server.persistence.spi.PersistenceService;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;
import org.osgi.service.http.HttpContext;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URL;
import java.util.*;

public class DefinitionsServiceImpl implements DefinitionsService, BundleListener {

    private static final Logger logger = LoggerFactory.getLogger(DefinitionsServiceImpl.class.getName());

    Map<String, Tag> tags = new HashMap<String, Tag>();
    Set<Tag> rootTags = new LinkedHashSet<Tag>();
    Map<String, ConditionType> conditionTypeByName = new HashMap<String, ConditionType>();
    Map<String, ConsequenceType> consequencesTypeByName = new HashMap<String, ConsequenceType>();
    Map<Tag, Set<ConditionType>> conditionTypeByTag = new HashMap<Tag, Set<ConditionType>>();
    Map<Tag, Set<ConsequenceType>> consequenceTypeByTag = new HashMap<Tag, Set<ConsequenceType>>();

    private List<String> registeredAliases = new ArrayList<String>();

    public DefinitionsServiceImpl() {
        System.out.println("Instantiating definitions service...");
    }

    private BundleContext bundleContext;

    private PersistenceService persistenceService;

    private HttpService httpService;

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setHttpService(HttpService httpService) {
        this.httpService = httpService;
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void postConstruct() {
        logger.debug("postConstruct {" + bundleContext.getBundle() + "}");

        processBundleStartup(bundleContext);

        /*
        for (Bundle bundle : bundleContext.getBundles()) {
            if (bundle.getBundleContext() != null) {
                processBundleStartup(bundle.getBundleContext());
            }
        }
        */
        bundleContext.addBundleListener(this);
    }

    private void processBundleStartup(BundleContext bundleContext) {
        if (bundleContext == null) {
            return;
        }
        loadPredefinedMappings(bundleContext);

        loadPredefinedTags(bundleContext);

        loadPredefinedCondition(bundleContext);
        loadPredefinedConsequences(bundleContext);

        registerHttpResources(bundleContext);
    }

    private void registerHttpResources(BundleContext bundleContext) {
        String httpResourcesHeaderValue = bundleContext.getBundle().getHeaders().get("Wemi-Http-Resources");
        if (httpResourcesHeaderValue == null || httpResourcesHeaderValue.length() == 0) {
            return;
        }
        HttpContext httpContext = new CustomHttpContext(bundleContext.getBundle());
        String[] httpResourcePairs = httpResourcesHeaderValue.split(",");
        for (String httpResourcePair : httpResourcePairs) {
            httpResourcePair = httpResourcePair.trim();
            String[] httpResourceParts = httpResourcePair.split("=");
            if (httpResourceParts == null || httpResourceParts.length != 2) {
                continue;
            }
            if (bundleContext.getBundle().getEntry(httpResourceParts[1].trim()) != null) {
                try {
                    String alias = "/plugins/" + bundleContext.getBundle().getSymbolicName() + httpResourceParts[0].trim();
                    if (alias.endsWith("/")) {
                        alias = alias.substring(0, alias.length() - 1);
                    }
                    httpService.registerResources(alias, httpResourceParts[1].trim(), httpContext);
                } catch (NamespaceException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void preDestroy() {
        for (String registeredAlias : registeredAliases) {
            httpService.unregister(registeredAlias);
        }
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

            try {
                Tag tag = MapperHelper.getObjectMapper().readValue(predefinedTagURL, Tag.class);
                tags.put(tag.getId(), tag);
            } catch (IOException e) {
                logger.error("Error while loading segment definition " + predefinedTagEntries, e);
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

            try {
                ConditionType conditionType = MapperHelper.getObjectMapper().readValue(predefinedConditionURL, ConditionType.class);
                conditionTypeByName.put(conditionType.getId(), conditionType);
                for (String tagId : conditionType.getTagIDs()) {
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

            try {
                ConsequenceType consequenceType = MapperHelper.getObjectMapper().readValue(predefinedConsequenceURL, ConsequenceType.class);
                consequencesTypeByName.put(consequenceType.getId(), consequenceType);
                for (String tagId : consequenceType.getTagIds()) {
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
                if (event.getBundle().getBundleContext() != null) {
                    processBundleStartup(event.getBundle().getBundleContext());
                }
                break;
            case BundleEvent.STOPPING:
                // @todo remove bundle-defined resources (is it possible ?)
                break;
        }
    }

    public class CustomHttpContext implements HttpContext {

        /**
         * Bundle using the {@link HttpService}.
         */
        private final Bundle bundle;

        public CustomHttpContext(Bundle bundle) {
            this.bundle = bundle;
        }

        public boolean handleSecurity(HttpServletRequest request, HttpServletResponse response) throws IOException {
            return true;
        }

        public URL getResource(String name) {
            final String normalizedname = normalizeResourcePath(name);
            return bundle.getResource(normalizedname);
        }

        public String getMimeType(String name) {
            return null;
        }


    }

    /**
     * Normalize the path for accesing a resource, meaning that will replace
     * consecutive slashes and will remove a leading slash if present.
     *
     * @param path path to normalize
     * @return normalized path or the original path if there is nothing to be
     * replaced.
     */
    public static String normalizeResourcePath(final String path) {
        if (path == null) {
            return null;
        }
        String normalizedPath = replaceSlashes(path.trim());
        if (normalizedPath.startsWith("/") && normalizedPath.length() > 1) {
            normalizedPath = normalizedPath.substring(1);
        }
        return normalizedPath;
    }

    /**
     * Replaces multiple subsequent slashes with one slash. E.g. ////a//path//
     * will becaome /a/path/
     *
     * @param target target sring to be replaced
     * @return a string where the subsequent slashes are replaced with one slash
     */
    static String replaceSlashes(final String target) {
        String replaced = target;
        if (replaced != null) {
            replaced = replaced.replaceAll("/+", "/");
        }
        return replaced;
    }

}
