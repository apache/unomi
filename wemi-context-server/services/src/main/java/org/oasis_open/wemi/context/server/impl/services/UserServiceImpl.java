package org.oasis_open.wemi.context.server.impl.services;

import org.oasis_open.wemi.context.server.api.*;
import org.oasis_open.wemi.context.server.api.conditions.Condition;
import org.oasis_open.wemi.context.server.api.services.DefinitionsService;
import org.oasis_open.wemi.context.server.api.services.UserService;
import org.oasis_open.wemi.context.server.persistence.spi.CustomObjectMapper;
import org.oasis_open.wemi.context.server.persistence.spi.PersistenceService;
import org.osgi.framework.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.*;

/**
 * Created by loom on 24.04.14.
 */
public class UserServiceImpl implements UserService, SynchronousBundleListener {

    private static final Logger logger = LoggerFactory.getLogger(RulesServiceImpl.class.getName());

    private BundleContext bundleContext;

    private PersistenceService persistenceService;

    private DefinitionsService definitionsService;

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

        loadPredefinedPropertyTypes(bundleContext);
        loadPredefinedPersonas(bundleContext);
        for (Bundle bundle : bundleContext.getBundles()) {
            if (bundle.getBundleContext() != null) {
                loadPredefinedPropertyTypes(bundle.getBundleContext());
                loadPredefinedPersonas(bundle.getBundleContext());
            }
        }
        bundleContext.addBundleListener(this);
    }

    public void preDestroy() {
        bundleContext.removeBundleListener(this);
    }

    private void processBundleStartup(BundleContext bundleContext) {
        if (bundleContext == null) {
            return;
        }
        loadPredefinedPropertyTypes(bundleContext);
        loadPredefinedPersonas(bundleContext);
    }

    private void processBundleStop(BundleContext bundleContext) {
    }

    public PartialList<User> getAllUsers() {
        return persistenceService.getAllItems(User.class, 0, 50, null);
    }

    public long getAllUsersCount() {
        return persistenceService.getAllItemsCount(User.ITEM_TYPE);
    }

    public PartialList<User> getUsers(String query, int offset, int size, String sortBy) {
        return persistenceService.getAllItems(User.class, offset, size, sortBy);
    }

    public PartialList<User> findUsersByPropertyValue(String propertyName, String propertyValue) {
        return persistenceService.query(propertyName, propertyValue, null, User.class, 0, -1);
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

    public User mergeUsersOnProperty(User currentUser, Session currentSession, String propertyName, String propertyValue) {
        PartialList<User> usersToMerge = findUsersByPropertyValue(propertyName, propertyValue);
        if (!usersToMerge.getList().contains(currentUser)) {
            usersToMerge.getList().add(currentUser);
            usersToMerge.setTotalSize(usersToMerge.getList().size());
        }

        if (usersToMerge.getTotalSize() == 0) {
            return null;
        }

        if (usersToMerge.getTotalSize() == 1) {
            return usersToMerge.get(0);
        }

        Set<String> allUserProperties = new LinkedHashSet<String>();
        for (User user : usersToMerge.getList()) {
            allUserProperties.addAll(user.getProperties().keySet());
        }

        Set<PropertyType> userPropertyTypes = getPropertyTypes("userProperties", true);
        Map<String, PropertyType> userPropertyTypeById = new HashMap<String, PropertyType>();
        for (PropertyType propertyType : userPropertyTypes) {
            userPropertyTypeById.put(propertyType.getId(), propertyType);
        }
        User masterUser = usersToMerge.get(0);
        Set<String> userIdsToMerge = new TreeSet<String>();
        for (User userToMerge : usersToMerge.getList()) {
            userIdsToMerge.add(userToMerge.getId());
        }
        logger.info("Merging users " + userIdsToMerge + " into user " + masterUser.getId());
        for (String userProperty : allUserProperties) {
            PropertyType propertyType = userPropertyTypeById.get(userProperty);
            String propertyMergeStrategyId = "defaultMergeStrategy";
            if (propertyType != null) {
                if (propertyType.getMergeStrategy() != null && propertyMergeStrategyId.length() > 0) {
                    propertyMergeStrategyId = propertyType.getMergeStrategy();
                }
            }
            PropertyMergeStrategyType propertyMergeStrategyType = definitionsService.getPropertyMergeStrategyType(propertyMergeStrategyId);
            if (propertyMergeStrategyType == null) {
                // we couldn't find the strategy
                if (propertyMergeStrategyId.equals("defaultMergeStrategy")) {
                    logger.warn("Couldn't resolve default strategy, ignoring property merge for property " + userProperty);
                    continue;
                } else {
                    logger.warn("Couldn't resolve strategy " + propertyMergeStrategyId + " for property " + userProperty + ", using default strategy instead");
                    propertyMergeStrategyId = "defaultMergeStrategy";
                    propertyMergeStrategyType = definitionsService.getPropertyMergeStrategyType(propertyMergeStrategyId);
                }
            }

            Collection<ServiceReference<PropertyMergeStrategyExecutor>> matchingPropertyMergeStrategyExecutors;
            try {
                matchingPropertyMergeStrategyExecutors = bundleContext.getServiceReferences(PropertyMergeStrategyExecutor.class, propertyMergeStrategyType.getFilter());
            } catch (InvalidSyntaxException e) {
                logger.error("Error retrieving strategy implementation", e);
                return null;
            }
            for (ServiceReference<PropertyMergeStrategyExecutor> propertyMergeStrategyExecutorReference : matchingPropertyMergeStrategyExecutors) {
                PropertyMergeStrategyExecutor propertyMergeStrategyExecutor = bundleContext.getService(propertyMergeStrategyExecutorReference);
                masterUser = propertyMergeStrategyExecutor.mergeProperty(userProperty, propertyType, usersToMerge.getList(), masterUser);
            }
        }

        // we now have to merge the user's segments
        for (User user : usersToMerge.getList()) {
            masterUser.getSegments().addAll(user.getSegments());
        }

        // we must now retrieve all the session associated with all the profiles and associate them with the master profile
        for (User user : usersToMerge.getList()) {
            if (user.getId().equals(masterUser.getId())) {
                continue;
            }
            PartialList<Session> userSessions = getUserSessions(user.getId(), 0, -1, null);
            if (currentSession.getUserId().equals(user.getId()) && !userSessions.getList().contains(currentSession)) {
                userSessions.getList().add(currentSession);
                userSessions.setTotalSize(userSessions.getList().size());
            }
            for (Session userSession : userSessions.getList()) {
                userSession.setUser(masterUser);
                saveSession(userSession);
            }
            // delete(user);
        }

        // we must mark all the profiles that we merged into the master as merged with the master, and they will
        // be deleted upon next load
        for (User user : usersToMerge.getList()) {
            if (user.getId().equals(masterUser.getId())) {
                continue;
            }
            user.setProperty("mergedWith", masterUser.getId());
        }

        return masterUser;
    }

    public PartialList<Session> getUserSessions(String userId, int offset, int size, String sortBy) {
        return persistenceService.query("userId", userId, sortBy, Session.class, offset, size);
    }

    public Set<PropertyType> getAllPropertyTypes() {
        return new LinkedHashSet<PropertyType>(persistenceService.getAllItems(PropertyType.class));
    }

    public Set<PropertyType> getPropertyTypes(String tagId, boolean recursive) {
        if (recursive) {
            Set<String> allTagIds = new HashSet<String>();
            collectSubTagIds(tagId, allTagIds);
            return new TreeSet<PropertyType>(persistenceService.query("tags", allTagIds.toArray(new String[allTagIds.size()]), null, PropertyType.class));
        } else {
            return new TreeSet<PropertyType>(persistenceService.query("tags", tagId, null, PropertyType.class));
        }
    }

    private void collectSubTagIds(String tagId, Set<String> allTagIds) {
        allTagIds.add(tagId);
        Tag rootTag = definitionsService.getTag(new Tag(tagId));
        if (rootTag.getSubTags() != null && rootTag.getSubTags().size() > 0) {
            for (Tag subTag : rootTag.getSubTags()) {
                collectSubTagIds(subTag.getId(), allTagIds);
            }
        }
    }

    public String getPropertyTypeMapping(String fromPropertyTypeId) {
        PartialList<PropertyType> types = persistenceService.query("automaticMappingsFrom", fromPropertyTypeId, null, PropertyType.class, 0, 1);
        if (types.size() > 0) {
            return types.get(0).getId();
        }
        return null;
    }

    public Session loadSession(String sessionId, Date dateHint) {
        Session s = persistenceService.load(sessionId, dateHint, Session.class);
        if (s == null) {
            Date yesterday = new Date(dateHint.getTime() - (24L * 60L * 60L * 1000L));
            s = persistenceService.load(sessionId, yesterday, Session.class);
            if (s == null) {
                s = persistenceService.load(sessionId, null, Session.class);
            }
        }
        return s;
    }

    public boolean saveSession(Session session) {
        persistenceService.save(session);
        return false;
    }

    public PartialList<Session> findUserSessions(String userId) {
        return persistenceService.query("userId", userId, "timeStamp:desc", Session.class, 0, 50);
    }

    @Override
    public boolean matchCondition(Condition condition, User user, Session session) {
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
        } else if (condition.getConditionType() != null && condition.getConditionType().getTagIDs().contains("userCondition")) {
            return persistenceService.testMatch(condition, user);
        } else if (condition.getConditionType() != null && condition.getConditionType().getTagIDs().contains("sessionCondition")) {
            return persistenceService.testMatch(condition, session);
        }
        return false;
    }

    public Persona loadPersona(String personaId) {
        return persistenceService.load(personaId, Persona.class);
    }

    public PersonaWithSessions loadPersonaWithSessions(String personaId) {
        Persona persona = persistenceService.load(personaId, Persona.class);
        List<PersonaSession> sessions = persistenceService.query("userId", persona.getId(), "timeStamp:desc", PersonaSession.class);
        return new PersonaWithSessions(persona, sessions);
    }

    public PartialList<Persona> getPersonas(int offset, int size, String sortBy) {
        return persistenceService.getAllItems(Persona.class, offset, size, sortBy);
    }

    public void createPersona(String personaId) {
        Persona newPersona = new Persona(personaId);

        Session session = new PersonaSession(UUID.randomUUID().toString(), newPersona, new Date());

        persistenceService.save(newPersona);
        persistenceService.save(session);
    }

    public PartialList<Session> getPersonaSessions(String personaId, int offset, int size, String sortBy) {
        return persistenceService.query("userId", personaId, sortBy, Session.class, offset, size);
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

                    persistenceService.save(propertyType);

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
                PersonaWithSessions persona = CustomObjectMapper.getObjectMapper().readValue(predefinedPersonaURL, PersonaWithSessions.class);
                persistenceService.save(persona.getPersona());

                List<PersonaSession> sessions = persona.getSessions();
                for (PersonaSession session : sessions) {
                    session.setUser(persona.getPersona());
                    persistenceService.save(session);
                }
            } catch (IOException e) {
                logger.error("Error while loading persona " + predefinedPersonaURL, e);
            }

        }
    }

    public void bundleChanged(BundleEvent event) {
        switch (event.getType()) {
            case BundleEvent.STARTED:
                processBundleStartup(event.getBundle().getBundleContext());
                break;
            case BundleEvent.STOPPING:
                processBundleStop(event.getBundle().getBundleContext());
                break;
        }
    }

}
