package org.oasis_open.contextserver.impl.services;

import org.oasis_open.contextserver.api.*;
import org.oasis_open.contextserver.api.conditions.Condition;
import org.oasis_open.contextserver.api.services.DefinitionsService;
import org.oasis_open.contextserver.api.services.ProfileService;
import org.oasis_open.contextserver.persistence.spi.CustomObjectMapper;
import org.oasis_open.contextserver.persistence.spi.PersistenceService;
import org.osgi.framework.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.*;

/**
 * Created by loom on 24.04.14.
 */
public class ProfileServiceImpl implements ProfileService, SynchronousBundleListener {

    private static final Logger logger = LoggerFactory.getLogger(RulesServiceImpl.class.getName());

    private BundleContext bundleContext;

    private PersistenceService persistenceService;

    private DefinitionsService definitionsService;

    public ProfileServiceImpl() {
        System.out.println("Initializing profile service...");
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

    public PartialList<Profile> getAllProfiles() {
        return persistenceService.getAllItems(Profile.class, 0, 50, null);
    }

    public long getAllProfilesCount() {
        return persistenceService.getAllItemsCount(Profile.ITEM_TYPE);
    }

    public PartialList<Profile> getProfiles(String query, int offset, int size, String sortBy) {
        return persistenceService.getAllItems(Profile.class, offset, size, sortBy);
    }

    public PartialList<Profile> findProfilesByPropertyValue(String propertyName, String propertyValue) {
        return persistenceService.query(propertyName, propertyValue, null, Profile.class, 0, -1);
    }

    public Profile load(String profileId) {
        return persistenceService.load(profileId, Profile.class);
    }

    public void save(Profile profile) {
        persistenceService.save(profile);
    }

    public void delete(String profileId, boolean persona) {
        if (persona) {
            persistenceService.remove(profileId, Persona.class);
        } else {
            persistenceService.remove(profileId, Profile.class);
        }
    }

    public Profile mergeProfilesOnProperty(Profile currentProfile, Session currentSession, String propertyName, String propertyValue) {
        PartialList<Profile> profilesToMerge = findProfilesByPropertyValue(propertyName, propertyValue);
        if (!profilesToMerge.getList().contains(currentProfile)) {
            profilesToMerge.getList().add(currentProfile);
            profilesToMerge.setTotalSize(profilesToMerge.getList().size());
        }

        if (profilesToMerge.getTotalSize() == 0) {
            return null;
        }

        Profile masterProfile = profilesToMerge.get(0);

        // now let's remove all the already merged profiles from the list.
        PartialList<Profile> filteredProfilesToMerge = new PartialList<Profile>(new ArrayList<Profile>(), 0, 0, 0);
        for (Profile filteredProfile : profilesToMerge.getList()) {
            if (!filteredProfile.getId().equals(masterProfile.getId()) &&
                    filteredProfile.getProperty("mergedWith") != null &&
                    filteredProfile.getProperty("mergedWith").equals(masterProfile.getId())) {
                // profile was already merged with the master profile, we will not merge him again.
                continue;
            }
            filteredProfilesToMerge.getList().add(filteredProfile);
        }
        filteredProfilesToMerge.setTotalSize(filteredProfilesToMerge.getList().size());
        profilesToMerge = filteredProfilesToMerge;

        if (profilesToMerge.getTotalSize() == 1) {
            return profilesToMerge.get(0);
        }

        Set<String> allProfileProperties = new LinkedHashSet<String>();
        for (Profile profile : profilesToMerge.getList()) {
            allProfileProperties.addAll(profile.getProperties().keySet());
        }

        Set<PropertyType> profilePropertyTypes = getPropertyTypes("profileProperties", true);
        Map<String, PropertyType> profilePropertyTypeById = new HashMap<String, PropertyType>();
        for (PropertyType propertyType : profilePropertyTypes) {
            profilePropertyTypeById.put(propertyType.getId(), propertyType);
        }
        Set<String> profileIdsToMerge = new TreeSet<String>();
        for (Profile profileToMerge : profilesToMerge.getList()) {
            profileIdsToMerge.add(profileToMerge.getId());
        }
        logger.info("Merging profiles " + profileIdsToMerge + " into profile " + masterProfile.getId());
        for (String profileProperty : allProfileProperties) {
            PropertyType propertyType = profilePropertyTypeById.get(profileProperty);
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
                    logger.warn("Couldn't resolve default strategy, ignoring property merge for property " + profileProperty);
                    continue;
                } else {
                    logger.warn("Couldn't resolve strategy " + propertyMergeStrategyId + " for property " + profileProperty + ", using default strategy instead");
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
                masterProfile = propertyMergeStrategyExecutor.mergeProperty(profileProperty, propertyType, profilesToMerge.getList(), masterProfile);
            }
        }

        // we now have to merge the profile's segments
        for (Profile profile : profilesToMerge.getList()) {
            masterProfile.getSegments().addAll(profile.getSegments());
        }

        // we must now retrieve all the session associated with all the profiles and associate them with the master profile
        for (Profile profile : profilesToMerge.getList()) {
            if (profile.getId().equals(masterProfile.getId())) {
                continue;
            }
            PartialList<Session> profileSessions = getProfileSessions(profile.getId(), 0, -1, null);
            if (currentSession.getProfileId().equals(profile.getId()) && !profileSessions.getList().contains(currentSession)) {
                profileSessions.getList().add(currentSession);
                profileSessions.setTotalSize(profileSessions.getList().size());
            }
            for (Session profileSession : profileSessions.getList()) {
                profileSession.setProfile(masterProfile);
                saveSession(profileSession);
            }
            // delete(profile);
        }

        // we must mark all the profiles that we merged into the master as merged with the master, and they will
        // be deleted upon next load
        for (Profile profile : profilesToMerge.getList()) {
            if (profile.getId().equals(masterProfile.getId())) {
                continue;
            }
            profile.setProperty("mergedWith", masterProfile.getId());
        }

        return masterProfile;
    }

    public PartialList<Session> getProfileSessions(String profileId, int offset, int size, String sortBy) {
        return persistenceService.query("profileId", profileId, sortBy, Session.class, offset, size);
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
        Tag rootTag = definitionsService.getTag(tagId);
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

    public PartialList<Session> findProfileSessions(String profileId) {
        return persistenceService.query("profileId", profileId, "timeStamp:desc", Session.class, 0, 50);
    }

    @Override
    public boolean matchCondition(Condition condition, Profile profile, Session session) {
        ParserHelper.resolveConditionType(definitionsService, condition);
        if (condition.getConditionTypeId().equals("profileEventCondition")) {
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
        } else if (condition.getConditionType() != null && condition.getConditionType().getTagIDs().contains("profileCondition")) {
            return persistenceService.testMatch(condition, profile);
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
        List<PersonaSession> sessions = persistenceService.query("profileId", persona.getId(), "timeStamp:desc", PersonaSession.class);
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
        return persistenceService.query("profileId", personaId, sortBy, Session.class, offset, size);
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
                    session.setProfile(persona.getPersona());
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
