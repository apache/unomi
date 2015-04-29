package org.oasis_open.contextserver.impl.services;

/*
 * #%L
 * context-server-services
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2014 - 2015 Jahia Solutions
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.apache.commons.lang3.StringUtils;
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

public class ProfileServiceImpl implements ProfileService, SynchronousBundleListener {

    private static final Logger logger = LoggerFactory.getLogger(ProfileServiceImpl.class.getName());

    private BundleContext bundleContext;

    private PersistenceService persistenceService;

    private DefinitionsService definitionsService;

    public ProfileServiceImpl() {
        logger.info("Initializing profile service...");
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

        loadPredefinedPersonas(bundleContext);
        for (Bundle bundle : bundleContext.getBundles()) {
            if (bundle.getBundleContext() != null) {
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
        loadPredefinedPersonas(bundleContext);
    }

    private void processBundleStop(BundleContext bundleContext) {
    }

    public long getAllProfilesCount() {
        return persistenceService.getAllItemsCount(Profile.ITEM_TYPE);
    }

    public PartialList<Profile> getProfiles(int offset, int size, String sortBy) {
        return getProfiles(null, null, offset, size, sortBy);
    }

    public PartialList<Profile> getProfiles(String query, int offset, int size, String sortBy) {
        return getProfiles(query, null, offset, size, sortBy);
    }

    public PartialList<Profile> getProfiles(Condition condition, int offset, int size, String sortBy) {
        return getProfiles(null, condition, offset, size, sortBy);
    }

    public PartialList<Profile> getProfiles(String query, Condition condition, int offset, int size, String sortBy) {
        if (condition != null && definitionsService.resolveConditionType(condition)) {
            if (StringUtils.isNotBlank(query)) {
                return persistenceService.queryFullText(query, condition, sortBy, Profile.class, offset, size);
            } else {
                return persistenceService.query(condition, sortBy, Profile.class, offset, size);
            }
        } else {
            if (StringUtils.isNotBlank(query)) {
                return persistenceService.queryFullText(query, sortBy, Profile.class, offset, size);
            } else {
                return persistenceService.getAllItems(Profile.class, offset, size, sortBy);
            }
        }
    }

    public PartialList<Profile> findProfilesByPropertyValue(String propertyName, String propertyValue, int offset, int size, String sortBy) {
        return persistenceService.query(propertyName, propertyValue, sortBy, Profile.class, offset, size);
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

    public boolean mergeProfilesOnProperty(Profile currentProfile, Session currentSession, String propertyName, String propertyValue) {
        List<Profile> profilesToMerge = persistenceService.query(propertyName, propertyValue, "properties.firstVisit", Profile.class);

        if (!profilesToMerge.contains(currentProfile)) {
            profilesToMerge.add(currentProfile);
        }

        if (profilesToMerge.size() == 1) {
            return false;
        }

        logger.info("Merging profiles for "+propertyName + "=" + propertyValue);

        Profile masterProfile = profilesToMerge.get(0);

        // now let's remove all the already merged profiles from the list.
        List<Profile> filteredProfilesToMerge = new ArrayList<Profile>();

        for (Profile filteredProfile : profilesToMerge) {
            if (!filteredProfile.getItemId().equals(masterProfile.getItemId()) && (
                    filteredProfile.getMergedWith() == null || !filteredProfile.getMergedWith().equals(masterProfile.getItemId()))) {
                filteredProfilesToMerge.add(filteredProfile);
            }
        }

        if (filteredProfilesToMerge.isEmpty()) {
            return false;
        }

        profilesToMerge = filteredProfilesToMerge;

        Set<String> allProfileProperties = new LinkedHashSet<String>();
        for (Profile profile : profilesToMerge) {
            allProfileProperties.addAll(profile.getProperties().keySet());
        }

        Set<PropertyType> profilePropertyTypes = definitionsService.getPropertyTypeByTag(definitionsService.getTag("profileProperties"), true);
        Map<String, PropertyType> profilePropertyTypeById = new HashMap<String, PropertyType>();
        for (PropertyType propertyType : profilePropertyTypes) {
            profilePropertyTypeById.put(propertyType.getId(), propertyType);
        }
        Set<String> profileIdsToMerge = new TreeSet<String>();
        for (Profile profileToMerge : profilesToMerge) {
            profileIdsToMerge.add(profileToMerge.getItemId());
        }
        logger.info("Merging profiles " + profileIdsToMerge + " into profile " + masterProfile.getItemId());
        boolean updated = false;

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
                for (ServiceReference<PropertyMergeStrategyExecutor> propertyMergeStrategyExecutorReference : matchingPropertyMergeStrategyExecutors) {
                    PropertyMergeStrategyExecutor propertyMergeStrategyExecutor = bundleContext.getService(propertyMergeStrategyExecutorReference);
                    updated |= propertyMergeStrategyExecutor.mergeProperty(profileProperty, propertyType, profilesToMerge, masterProfile);
                }
            } catch (InvalidSyntaxException e) {
                logger.error("Error retrieving strategy implementation", e);
            }

        }

        // we now have to merge the profile's segments
        for (Profile profile : profilesToMerge) {
            updated |= masterProfile.getSegments().addAll(profile.getSegments());
        }

        // Refresh index now to ensure we find all sessions/events
        persistenceService.refresh();
        // we must now retrieve all the session associated with all the profiles and associate them with the master profile
        for (Profile profile : profilesToMerge) {
            List<Session> sessions = persistenceService.query("profileId", profile.getItemId(), null, Session.class);
            if (currentSession.getProfileId().equals(profile.getItemId()) && !sessions.contains(currentSession)) {
                sessions.add(currentSession);
            }
            for (Session session : sessions) {
                persistenceService.update(session.getItemId(), session.getTimeStamp(), Session.class, "profileId", masterProfile.getItemId());
            }

            List<Event> events = persistenceService.query("profileId", profile.getItemId(), null, Event.class);
            for (Event event : events) {
                persistenceService.update(event.getItemId(), event.getTimeStamp(), Event.class, "profileId", masterProfile.getItemId());
            }
        }

        // we must mark all the profiles that we merged into the master as merged with the master, and they will
        // be deleted upon next load
        for (Profile profile : profilesToMerge) {
            profile.setMergedWith(masterProfile.getItemId());
            persistenceService.update(profile.getItemId(), null, Profile.class, "mergedWith", masterProfile.getItemId());
        }

        if (!currentProfile.getItemId().equals(masterProfile.getItemId())) {
            currentSession.setProfile(masterProfile);
            saveSession(currentSession);
        }

        return updated;
    }

    public PartialList<Session> getProfileSessions(String profileId, String query, int offset, int size, String sortBy) {
        if (StringUtils.isNotBlank(query)) {
            return persistenceService.queryFullText("profileId", profileId, query, sortBy, Session.class, offset, size);
        } else {
            return persistenceService.query("profileId", profileId, sortBy, Session.class, offset, size);
        }
    }

    public String getPropertyTypeMapping(String fromPropertyTypeId) {
        Collection<PropertyType> types = definitionsService.getPropertyTypeByMapping(fromPropertyTypeId);
        if (types.size() > 0) {
            return types.iterator().next().getId();
        }
        return null;
    }

    public Session loadSession(String sessionId, Date dateHint) {
        Session s = persistenceService.load(sessionId, dateHint, Session.class);
        if (s == null && dateHint != null) {
            Date yesterday = new Date(dateHint.getTime() - (24L * 60L * 60L * 1000L));
            s = persistenceService.load(sessionId, yesterday, Session.class);
        }
        return s;
    }

    public Session saveSession(Session session) {
        return persistenceService.save(session) ? session : null;
    }

    public PartialList<Session> findProfileSessions(String profileId) {
        return persistenceService.query("profileId", profileId, "timeStamp:desc", Session.class, 0, 50);
    }

    @Override
    public boolean matchCondition(Condition condition, Profile profile, Session session) {
        ParserHelper.resolveConditionType(definitionsService, condition);
        Condition profileCondition = definitionsService.extractConditionByTag(condition, "profileCondition");
        Condition sessionCondition = definitionsService.extractConditionByTag(condition, "sessionCondition");
        if (profileCondition != null && !persistenceService.testMatch(profileCondition, profile)) {
            return false;
        }
        if (sessionCondition != null && !persistenceService.testMatch(sessionCondition, session)) {
            return false;
        }
        return true;
    }

    public Persona loadPersona(String personaId) {
        return persistenceService.load(personaId, Persona.class);
    }

    public PersonaWithSessions loadPersonaWithSessions(String personaId) {
        Persona persona = persistenceService.load(personaId, Persona.class);
        if (persona == null) {
            return null;
        }
        List<PersonaSession> sessions = persistenceService.query("profileId", persona.getItemId(), "timeStamp:desc", PersonaSession.class);
        return new PersonaWithSessions(persona, sessions);
    }

    public PartialList<Persona> getPersonas(int offset, int size, String sortBy) {
        return getPersonas(null, null, offset, size, sortBy);
    }

    public PartialList<Persona> getPersonas(String query, int offset, int size, String sortBy) {
        return getPersonas(query, null, offset, size, sortBy);
    }

    public PartialList<Persona> getPersonas(Condition condition, int offset, int size, String sortBy) {
        return getPersonas(null, condition, offset, size, sortBy);
    }

    public PartialList<Persona> getPersonas(String query, Condition condition, int offset, int size, String sortBy) {
        if (condition != null && definitionsService.resolveConditionType(condition)) {
            if (StringUtils.isNotBlank(query)) {
                return persistenceService.queryFullText(query, condition, sortBy, Persona.class, offset, size);
            } else {
                return persistenceService.query(condition, sortBy, Persona.class, offset, size);
            }
        } else {
            if (StringUtils.isNotBlank(query)) {
                return persistenceService.queryFullText(query, sortBy, Persona.class, offset, size);
            } else {
                return persistenceService.getAllItems(Persona.class, offset, size, sortBy);
            }
        }
    }

    public Persona createPersona(String personaId) {
        Persona newPersona = new Persona(personaId);

        Session session = new PersonaSession(UUID.randomUUID().toString(), newPersona, new Date());

        persistenceService.save(newPersona);
        persistenceService.save(session);
        return newPersona;
    }

    public PartialList<Session> getPersonaSessions(String personaId, int offset, int size, String sortBy) {
        return persistenceService.query("profileId", personaId, sortBy, Session.class, offset, size);
    }

    private void loadPredefinedPersonas(BundleContext bundleContext) {
        if (bundleContext == null) {
            return;
        }
        Enumeration<URL> predefinedPersonaEntries = bundleContext.getBundle().findEntries("META-INF/cxs/personas", "*.json", true);
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
