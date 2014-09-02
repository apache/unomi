package org.oasis_open.wemi.context.server.impl.services;

import org.oasis_open.wemi.context.server.api.*;
import org.oasis_open.wemi.context.server.api.conditions.Condition;
import org.oasis_open.wemi.context.server.api.services.DefinitionsService;
import org.oasis_open.wemi.context.server.api.services.UserService;
import org.oasis_open.wemi.context.server.persistence.spi.MapperHelper;
import org.oasis_open.wemi.context.server.persistence.spi.PersistenceService;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.*;

/**
 * Created by loom on 24.04.14.
 */
public class UserServiceImpl implements UserService, BundleListener {

    private static final Logger logger = LoggerFactory.getLogger(RulesServiceImpl.class.getName());

    private BundleContext bundleContext;

    private PersistenceService persistenceService;

    private DefinitionsService definitionsService;

    private Map<String, UserPropertyGroup> userPropertyGroupsById = new LinkedHashMap<String, UserPropertyGroup>();
    private SortedSet<UserPropertyGroup> userPropertyGroups = new TreeSet<UserPropertyGroup>();

    private Map<String, String> propertyMappings = new HashMap<String, String>();

    public UserServiceImpl() {
        System.out.println("Initializing user service...");
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

    public void postConstruct() {
        logger.debug("postConstruct {" + bundleContext.getBundle() + "}");

        loadPredefinedUserPropertyGroups(bundleContext);
        loadPredefinedUserProperties(bundleContext);
        for (Bundle bundle : bundleContext.getBundles()) {
            if (bundle.getBundleContext() != null) {
                loadPredefinedUserPropertyGroups(bundle.getBundleContext());
                loadPredefinedUserProperties(bundle.getBundleContext());
            }
        }
        bundleContext.addBundleListener(this);
    }

    public void preDestroy() {
        bundleContext.removeBundleListener(this);
    }

    public Collection<User> getAllUsers() {
        return persistenceService.getAllItems(User.class);
    }

    public Collection<User> getUsers(String query, int offset, int size) {
        return persistenceService.getAllItems(User.class, offset, size);
    }

    public List<User> findUsersByPropertyValue(String propertyName, String propertyValue) {
        return new ArrayList<User>();
    }

    public User load(String userId) {
        return persistenceService.load(userId, User.class);
    }

    public boolean save(User user) {
        persistenceService.save(user);
        return false;
    }

    public Set<UserPropertyGroup> getUserPropertyGroups() {
        return userPropertyGroups;
    }

    public Set<UserProperty> getAllUserProperties() {
        Set<UserProperty> allUserProperties = new LinkedHashSet<UserProperty>();
        for (UserPropertyGroup userPropertyGroup : userPropertyGroups) {
            allUserProperties.addAll(userPropertyGroup.getUserProperties());
        }
        return allUserProperties;
    }

    public Set<UserProperty> getUserProperties(String propertyGroupId) {
        UserPropertyGroup userPropertyGroup = userPropertyGroupsById.get(propertyGroupId);
        if (userPropertyGroup == null) {
            return null;
        }
        return userPropertyGroup.getUserProperties();
    }

    public String getUserPropertyMapping(String fromPropertyName) {
        return propertyMappings.get(fromPropertyName);
    }

    public Session loadSession(String sessionId) {
        return persistenceService.load(sessionId, Session.class);
    }

    public boolean saveSession(Session session) {
        persistenceService.save(session);
        return false;
    }

    @Override
    public boolean matchCondition(String conditionString, User user, Session session) {
        try {
            Condition condition = MapperHelper.getObjectMapper().readValue(conditionString, Condition.class);
            ParserHelper.resolveConditionType(definitionsService, condition);
            if (condition.getConditionTypeId().equals("userEventCondition")) {
                final Map<String, Object> parameters = condition.getParameterValues();
                parameters.put("target", session);
                List<Event> matchingEvents = persistenceService.query(condition, "timeStamp", Event.class);

                String occursIn = (String) condition.getParameterValues().get("eventOccurIn");
                if (occursIn != null && occursIn.equals("last")) {
                    if (matchingEvents.size() == 0) {
                        return false;
                    }
                    final Event lastEvent = matchingEvents.get(matchingEvents.size() - 1);
                    String eventType = lastEvent.getEventType();
                    List<Event> events = persistenceService.query("sessionId", session.getItemId(), "timeStamp", Event.class);
                    Collections.reverse(events);
                    for (Event event : events) {
                        if (event.getEventType().equals(eventType)) {
                            return event.getItemId().equals(lastEvent.getItemId());
                        }
                    }
                    return false;
                }

                Integer minimumEventCount = !parameters.containsKey("minimumEventCount") || "".equals(parameters.get("minimumEventCount")) ? 0 : Integer.parseInt((String) parameters.get("minimumEventCount"));
                Integer maximumEventCount = !parameters.containsKey("maximumEventCount") || "".equals(parameters.get("maximumEventCount")) ? Integer.MAX_VALUE : Integer.parseInt((String) parameters.get("maximumEventCount"));

                return matchingEvents.size() >= minimumEventCount && matchingEvents.size() <= maximumEventCount;
            } else if (condition.getConditionType().getTagIDs().contains("userCondition")) {
                return persistenceService.testMatch(condition, user);
            } else if (condition.getConditionType().getTagIDs().contains("sessionCondition")) {
                return persistenceService.testMatch(condition, session);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void bundleChanged(BundleEvent event) {
        switch (event.getType()) {
            case BundleEvent.STARTED:
                if (event.getBundle().getBundleContext() != null) {
                    loadPredefinedUserPropertyGroups(event.getBundle().getBundleContext());
                    loadPredefinedUserProperties(event.getBundle().getBundleContext());
                }
                break;
            case BundleEvent.STOPPING:
                // @todo remove bundle-defined resources (is it possible ?)
                break;
        }
    }

    private void loadPredefinedUserPropertyGroups(BundleContext bundleContext) {
        if (bundleContext == null) {
            return;
        }
        Enumeration<URL> predefinedUserPropertyGroupEntries = bundleContext.getBundle().findEntries("META-INF/wemi/user", "*PropertyGroup.json", true);
        if (predefinedUserPropertyGroupEntries == null) {
            return;
        }

        while (predefinedUserPropertyGroupEntries.hasMoreElements()) {
            URL predefinedUserPropertyGroupURL = predefinedUserPropertyGroupEntries.nextElement();
            logger.debug("Found predefined user property group at " + predefinedUserPropertyGroupURL + ", loading... ");

            try {
                UserPropertyGroup userPropertyGroup = MapperHelper.getObjectMapper().readValue(predefinedUserPropertyGroupURL, UserPropertyGroup.class);
                ParserHelper.populatePluginType(userPropertyGroup, bundleContext.getBundle());
                userPropertyGroups.add(userPropertyGroup);
                userPropertyGroupsById.put(userPropertyGroup.getId(), userPropertyGroup);
            } catch (IOException e) {
                logger.error("Error while loading user property group " + predefinedUserPropertyGroupURL, e);
            }

        }
    }

    private void loadPredefinedUserProperties(BundleContext bundleContext) {
        if (bundleContext == null) {
            return;
        }
        Enumeration<URL> predefinedUserPropertiesEntries = bundleContext.getBundle().findEntries("META-INF/wemi/user", "*.json", true);
        if (predefinedUserPropertiesEntries == null) {
            return;
        }

        while (predefinedUserPropertiesEntries.hasMoreElements()) {
            URL predefinedUserPropertyURL = predefinedUserPropertiesEntries.nextElement();
            logger.debug("Found predefined user property at " + predefinedUserPropertyURL + ", loading... ");

            try {
                if (!predefinedUserPropertyURL.toExternalForm().endsWith("PropertyGroup.json")) {
                    UserProperty userProperty = MapperHelper.getObjectMapper().readValue(predefinedUserPropertyURL, UserProperty.class);
                    ParserHelper.resolvePropertyType(definitionsService, userProperty);
                    ParserHelper.populatePluginType(userProperty, bundleContext.getBundle());
                    UserPropertyGroup userPropertyGroup = userPropertyGroupsById.get(userProperty.getGroupId());
                    if (userPropertyGroup == null) {
                        logger.warn("Undeclared groupId " + userPropertyGroup.getId() + " detected, creating dynamically...");
                        userPropertyGroup = new UserPropertyGroup(userProperty.getGroupId());
                        userPropertyGroups.add(userPropertyGroup);
                    }
                    userPropertyGroup.getUserProperties().add(userProperty);
                    userPropertyGroupsById.put(userProperty.getGroupId(), userPropertyGroup);

                    if (userProperty.getAutomaticMappingsFrom() != null && userProperty.getAutomaticMappingsFrom().size() > 0) {
                        for (String mappingFrom : userProperty.getAutomaticMappingsFrom()) {
                            propertyMappings.put(mappingFrom, userProperty.getId());
                        }
                    }

                }
            } catch (IOException e) {
                logger.error("Error while loading user properties " + predefinedUserPropertyURL, e);
            }

        }
    }

}
