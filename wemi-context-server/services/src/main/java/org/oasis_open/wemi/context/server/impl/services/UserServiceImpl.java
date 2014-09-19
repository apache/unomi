package org.oasis_open.wemi.context.server.impl.services;

import org.oasis_open.wemi.context.server.api.*;
import org.oasis_open.wemi.context.server.api.conditions.Condition;
import org.oasis_open.wemi.context.server.api.services.DefinitionsService;
import org.oasis_open.wemi.context.server.api.services.UserService;
import org.oasis_open.wemi.context.server.persistence.spi.CustomObjectMapper;
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

    private Map<String, PropertyTypeGroup> propertyTypeGroupsById = new LinkedHashMap<String, PropertyTypeGroup>();
    private SortedSet<PropertyTypeGroup> propertyTypeGroups = new TreeSet<PropertyTypeGroup>();

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

        loadPredefinedPropertyTypeGroups(bundleContext);
        loadPredefinedPropertyTypes(bundleContext);
        loadPredefinedPersonas(bundleContext);
        for (Bundle bundle : bundleContext.getBundles()) {
            if (bundle.getBundleContext() != null) {
                loadPredefinedPropertyTypeGroups(bundle.getBundleContext());
                loadPredefinedPropertyTypes(bundle.getBundleContext());
                loadPredefinedPersonas(bundle.getBundleContext());
            }
        }
        bundleContext.addBundleListener(this);
    }

    public void preDestroy() {
        bundleContext.removeBundleListener(this);
    }

    public PartialList<User> getAllUsers() {
        return persistenceService.getAllItems(User.class);
    }

    public long getAllUsersCount() {
        return persistenceService.getAllItemsCount(User.class);
    }

    public PartialList<User> getUsers(String query, int offset, int size, String sortBy) {
        return persistenceService.getAllItems(User.class, offset, size, sortBy);
    }

    public PartialList<User> findUsersByPropertyValue(String propertyName, String propertyValue) {
        return new PartialList<User>();
    }

    public User load(String userId) {
        return persistenceService.load(userId, User.class);
    }

    public void save(User user) {
        persistenceService.save(user);
    }

    public void delete(User user) {
        if (user instanceof Persona) {
            persistenceService.remove(user.getItemId(), Persona.class);
        } else {
            persistenceService.remove(user.getItemId(), User.class);
        }
    }

    public Set<PropertyTypeGroup> getPropertyTypeGroups() {
        return propertyTypeGroups;
    }

    public Set<PropertyType> getAllPropertyTypes() {
        Set<PropertyType> allUserProperties = new LinkedHashSet<PropertyType>();
        for (PropertyTypeGroup propertyTypeGroup : propertyTypeGroups) {
            allUserProperties.addAll(propertyTypeGroup.getPropertyTypes());
        }
        return allUserProperties;
    }

    public Set<PropertyType> getPropertyTypes(String propertyGroupId) {
        PropertyTypeGroup propertyTypeGroup = propertyTypeGroupsById.get(propertyGroupId);
        if (propertyTypeGroup == null) {
            return null;
        }
        return propertyTypeGroup.getPropertyTypes();
    }

    public String getPropertyTypeMapping(String fromPropertyTypeId) {
        return propertyMappings.get(fromPropertyTypeId);
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
            Condition condition = CustomObjectMapper.getObjectMapper().readValue(conditionString, Condition.class);
            ParserHelper.resolveConditionType(definitionsService, condition);
            if (condition.getConditionTypeId().equals("userEventCondition")) {
                final Map<String, Object> parameters = condition.getParameterValues();
                parameters.put("target", session);
                PartialList<Event> matchingEvents = persistenceService.query(condition, "timeStamp", Event.class, 0, 100);

                String occursIn = (String) condition.getParameterValues().get("eventOccurIn");
                if (occursIn != null && occursIn.equals("last")) {
                    if (matchingEvents.size() == 0) {
                        return false;
                    }
                    final Event lastEvent = matchingEvents.get(matchingEvents.size() - 1);
                    String eventType = lastEvent.getEventType();
                    PartialList<Event> events = persistenceService.query("sessionId", session.getItemId(), "timeStamp", Event.class);
                    Collections.reverse(events.getList());
                    for (Event event : events.getList()) {
                        if (event.getEventType().equals(eventType)) {
                            return event.getItemId().equals(lastEvent.getItemId());
                        }
                    }
                    return false;
                }

                Integer minimumEventCount = !parameters.containsKey("minimumEventCount") || "".equals(parameters.get("minimumEventCount")) ? 0 : Integer.parseInt((String) parameters.get("minimumEventCount"));
                Integer maximumEventCount = !parameters.containsKey("maximumEventCount") || "".equals(parameters.get("maximumEventCount")) ? Integer.MAX_VALUE : Integer.parseInt((String) parameters.get("maximumEventCount"));

                return matchingEvents.size() >= minimumEventCount && matchingEvents.size() <= maximumEventCount;
            } else if (condition.getConditionType() != null && condition.getConditionType().getTagIDs().contains("userCondition")) {
                return persistenceService.testMatch(condition, user);
            } else if (condition.getConditionType() != null && condition.getConditionType().getTagIDs().contains("sessionCondition")) {
                return persistenceService.testMatch(condition, session);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public Persona loadPersona(String personaId) {
        return persistenceService.load(personaId, Persona.class);
    }

    public PartialList<Persona> getPersonas(int offset, int size, String sortBy) {
        return persistenceService.getAllItems(Persona.class, offset, size, sortBy);
    }

    public void createPersona(String personaId) {
        Persona newPersona = new Persona(personaId);

        Session session = new Session(UUID.randomUUID().toString(), personaId, new Date());
        newPersona.getSessions().add(session);

        persistenceService.save(newPersona);
        persistenceService.save(session);
    }

    public void bundleChanged(BundleEvent event) {
        switch (event.getType()) {
            case BundleEvent.STARTED:
                if (event.getBundle().getBundleContext() != null) {
                    loadPredefinedPropertyTypeGroups(event.getBundle().getBundleContext());
                    loadPredefinedPropertyTypes(event.getBundle().getBundleContext());
                    loadPredefinedPersonas(event.getBundle().getBundleContext());
                }
                break;
            case BundleEvent.STOPPING:
                // @todo remove bundle-defined resources (is it possible ?)
                break;
        }
    }

    private void loadPredefinedPropertyTypeGroups(BundleContext bundleContext) {
        if (bundleContext == null) {
            return;
        }
        Enumeration<URL> predefinedPropertyTypeGroupEntries = bundleContext.getBundle().findEntries("META-INF/wemi/properties", "*PropertyGroup.json", true);
        if (predefinedPropertyTypeGroupEntries == null) {
            return;
        }

        while (predefinedPropertyTypeGroupEntries.hasMoreElements()) {
            URL predefinedPropertyTypeGroupURL = predefinedPropertyTypeGroupEntries.nextElement();
            logger.debug("Found predefined property group at " + predefinedPropertyTypeGroupURL + ", loading... ");

            try {
                PropertyTypeGroup propertyTypeGroup = CustomObjectMapper.getObjectMapper().readValue(predefinedPropertyTypeGroupURL, PropertyTypeGroup.class);
                ParserHelper.populatePluginType(propertyTypeGroup, bundleContext.getBundle());
                propertyTypeGroups.add(propertyTypeGroup);
                propertyTypeGroupsById.put(propertyTypeGroup.getId(), propertyTypeGroup);
            } catch (IOException e) {
                logger.error("Error while loading property group " + predefinedPropertyTypeGroupURL, e);
            }

        }
    }

    private void loadPredefinedPropertyTypes(BundleContext bundleContext) {
        if (bundleContext == null) {
            return;
        }
        Enumeration<URL> predefinedPropertyTypeEntries = bundleContext.getBundle().findEntries("META-INF/wemi/properties", "*.json", true);
        if (predefinedPropertyTypeEntries == null) {
            return;
        }

        while (predefinedPropertyTypeEntries.hasMoreElements()) {
            URL predefinedPropertyTypeURL = predefinedPropertyTypeEntries.nextElement();
            logger.debug("Found predefined property type at " + predefinedPropertyTypeURL + ", loading... ");

            try {
                if (!predefinedPropertyTypeURL.toExternalForm().endsWith("PropertyGroup.json")) {
                    PropertyType propertyType = CustomObjectMapper.getObjectMapper().readValue(predefinedPropertyTypeURL, PropertyType.class);
                    ParserHelper.resolveValueType(definitionsService, propertyType);
                    ParserHelper.populatePluginType(propertyType, bundleContext.getBundle());
                    PropertyTypeGroup propertyTypeGroup = propertyTypeGroupsById.get(propertyType.getGroupId());
                    if (propertyTypeGroup == null) {
                        logger.warn("Undeclared groupId " + propertyTypeGroup.getId() + " detected, creating dynamically...");
                        propertyTypeGroup = new PropertyTypeGroup(propertyType.getGroupId());
                        propertyTypeGroups.add(propertyTypeGroup);
                    }
                    propertyTypeGroup.getPropertyTypes().add(propertyType);
                    propertyTypeGroupsById.put(propertyType.getGroupId(), propertyTypeGroup);

                    if (propertyType.getAutomaticMappingsFrom() != null && propertyType.getAutomaticMappingsFrom().size() > 0) {
                        for (String mappingFrom : propertyType.getAutomaticMappingsFrom()) {
                            propertyMappings.put(mappingFrom, propertyType.getId());
                        }
                    }

                }
            } catch (IOException e) {
                logger.error("Error while loading properties " + predefinedPropertyTypeURL, e);
            }

        }
    }

    private void loadPredefinedPersonas(BundleContext bundleContext) {
        if (bundleContext == null) {
            return;
        }
        Enumeration<URL> predefinedPersonaEntries = bundleContext.getBundle().findEntries("META-INF/wemi/personas", "*.json", true);
        if (predefinedPersonaEntries == null) {
            return;
        }

        while (predefinedPersonaEntries.hasMoreElements()) {
            URL predefinedPersonaURL = predefinedPersonaEntries.nextElement();
            logger.debug("Found predefined persona at " + predefinedPersonaURL + ", loading... ");

            try {
                Persona persona = CustomObjectMapper.getObjectMapper().readValue(predefinedPersonaURL, Persona.class);
                persistenceService.save(persona);

                for (Session session : persona.getSessions()) {
                    persistenceService.save(session);
                }
            } catch (IOException e) {
                logger.error("Error while loading persona " + predefinedPersonaURL, e);
            }

        }
    }

}
