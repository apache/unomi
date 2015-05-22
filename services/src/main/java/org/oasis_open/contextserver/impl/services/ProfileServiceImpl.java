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

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.beanutils.expression.DefaultResolver;
import org.apache.commons.lang3.StringUtils;
import org.oasis_open.contextserver.api.*;
import org.oasis_open.contextserver.api.actions.Action;
import org.oasis_open.contextserver.api.conditions.Condition;
import org.oasis_open.contextserver.api.conditions.ConditionType;
import org.oasis_open.contextserver.api.query.Query;
import org.oasis_open.contextserver.api.services.DefinitionsService;
import org.oasis_open.contextserver.api.services.ProfileService;
import org.oasis_open.contextserver.api.services.QueryService;
import org.oasis_open.contextserver.impl.actions.ActionExecutorDispatcher;
import org.oasis_open.contextserver.persistence.spi.CustomObjectMapper;
import org.oasis_open.contextserver.persistence.spi.PersistenceService;
import org.oasis_open.contextserver.persistence.spi.PropertyHelper;
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

    private QueryService queryService;

    private ActionExecutorDispatcher actionExecutorDispatcher;

    private Condition purgeProfileQuery;
    private Integer purgeProfileExistTime = 0;
    private Integer purgeProfileInactiveTime = 0;
    private Integer purgeSessionsAndEventsTime = 0;
    private Integer purgeProfileInterval = 0;

    private Timer purgeProfileTimer;

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
        initializePurge();
    }

    public void preDestroy() {
        bundleContext.removeBundleListener(this);
        cancelPurge();
    }

    private void processBundleStartup(BundleContext bundleContext) {
        if (bundleContext == null) {
            return;
        }
        loadPredefinedPersonas(bundleContext);
    }

    private void processBundleStop(BundleContext bundleContext) {
    }

    public void setQueryService(QueryService queryService) {
        this.queryService = queryService;
    }

    public void setPurgeProfileExistTime(Integer purgeProfileExistTime) {
        this.purgeProfileExistTime = purgeProfileExistTime;
    }

    public void setPurgeProfileInactiveTime(Integer purgeProfileInactiveTime) {
        this.purgeProfileInactiveTime = purgeProfileInactiveTime;
    }

    public void setPurgeSessionsAndEventsTime(Integer purgeSessionsAndEventsTime) {
        this.purgeSessionsAndEventsTime = purgeSessionsAndEventsTime;
    }

    public void setPurgeProfileInterval(Integer purgeProfileInterval) {
        this.purgeProfileInterval = purgeProfileInterval;
    }

    private void initializePurge() {
        logger.info("Profile purge: Initializing");

        if(purgeProfileInactiveTime > 0 || purgeProfileExistTime > 0 || purgeSessionsAndEventsTime > 0) {
            if(purgeProfileInactiveTime > 0) {
                logger.info("Profile purge: Profile with no visits since {} days, will be purged", purgeProfileInactiveTime);
            }
            if(purgeProfileExistTime > 0) {
                logger.info("Profile purge: Profile created since {} days, will be purged", purgeProfileExistTime);
            }

            purgeProfileTimer = new Timer();
            TimerTask task = new TimerTask() {
                @Override
                public void run() {
                    long t = System.currentTimeMillis();
                    logger.debug("Profile purge: Purge triggered");

                    if(purgeProfileQuery == null){
                        ConditionType profilePropertyConditionType = definitionsService.getConditionType("profilePropertyCondition");
                        ConditionType booleanCondition = definitionsService.getConditionType("booleanCondition");
                        if (profilePropertyConditionType == null || booleanCondition == null){
                            // definition service not yet fully instantiate
                            return;
                        }

                        purgeProfileQuery = new Condition(booleanCondition);
                        purgeProfileQuery.setParameter("operator", "or");
                        List<Condition> subConditions = new ArrayList<>();

                        if(purgeProfileInactiveTime > 0) {
                            Condition inactiveTimeCondition = new Condition(profilePropertyConditionType);
                            inactiveTimeCondition.setParameter("propertyName","lastVisit");
                            inactiveTimeCondition.setParameter("comparisonOperator","lessThanOrEqualTo");
                            inactiveTimeCondition.setParameter("propertyValueDateExpr","now-"+purgeProfileInactiveTime+"d");
                            subConditions.add(inactiveTimeCondition);
                        }

                        if(purgeProfileExistTime > 0) {
                            Condition existTimeCondition = new Condition(profilePropertyConditionType);
                            existTimeCondition.setParameter("propertyName","firstVisit");
                            existTimeCondition.setParameter("comparisonOperator","lessThanOrEqualTo");
                            existTimeCondition.setParameter("propertyValueDateExpr","now-"+purgeProfileExistTime+"d");
                            subConditions.add(existTimeCondition);
                        }

                        purgeProfileQuery.setParameter("subConditions", subConditions);
                    }

                    persistenceService.removeByQuery(purgeProfileQuery, Profile.class);

                    if (purgeSessionsAndEventsTime > 0) {
                        persistenceService.purge(getMonth(-purgeSessionsAndEventsTime).getTime());
                    }

                    logger.debug("Profile purge: purge executed in {} ms", System.currentTimeMillis() - t);
                }
            };
            purgeProfileTimer.scheduleAtFixedRate(task, getDay(1).getTime(), purgeProfileInterval);
            logger.info("Profile purge: purge scheduled with an interval of {} days", purgeProfileInterval);
        } else {
            logger.info("Profile purge: No purge scheduled");
        }
    }

    private void cancelPurge() {
        if(purgeProfileTimer != null) {
            purgeProfileTimer.cancel();
        }
        logger.info("Profile purge: Purge unscheduled");
    }

    private GregorianCalendar getDay(int offset) {
        GregorianCalendar gc = new GregorianCalendar();
        gc = new GregorianCalendar(gc.get(Calendar.YEAR), gc.get(Calendar.MONTH), gc.get(Calendar.DAY_OF_MONTH));
        gc.add(Calendar.DAY_OF_MONTH, offset);
        return gc;
    }

    private GregorianCalendar getMonth(int offset) {
        GregorianCalendar gc = new GregorianCalendar();
        gc = new GregorianCalendar(gc.get(Calendar.YEAR), gc.get(Calendar.MONTH), 1);
        gc.add(Calendar.MONTH, offset);
        return gc;
    }

    public long getAllProfilesCount() {
        return persistenceService.getAllItemsCount(Profile.ITEM_TYPE);
    }

    public <T extends Profile> PartialList<T> search(Query query, final Class<T> clazz) {
        if (query.getCondition() != null && definitionsService.resolveConditionType(query.getCondition())) {
            if (StringUtils.isNotBlank(query.getText())) {
                return persistenceService.queryFullText(query.getText(), query.getCondition(), query.getSortby(), clazz, query.getOffset(), query.getLimit());
            } else {
                return persistenceService.query(query.getCondition(), query.getSortby(), clazz, query.getOffset(), query.getLimit());
            }
        } else {
            if (StringUtils.isNotBlank(query.getText())) {
                return persistenceService.queryFullText(query.getText(), query.getSortby(), clazz, query.getOffset(), query.getLimit());
            } else {
                return persistenceService.getAllItems(clazz, query.getOffset(), query.getLimit(), query.getSortby());
            }
        }
    }

    @Override
    public String exportProfilesPropertiesToCsv(Query query) {
        Set<PropertyType> propertyTypes = queryService.getExistingProperties("profileProperties", Profile.ITEM_TYPE);
        PartialList<Profile> profiles = search(query, Profile.class);

        return "";
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

        Condition propertyCondition = new Condition(definitionsService.getConditionType("eventPropertyCondition"));
        propertyCondition.setParameter("comparisonOperator", "equals");
        propertyCondition.setParameter("propertyName", propertyName);
        propertyCondition.setParameter("propertyValue", propertyValue);

        Condition excludeMergedProfilesCondition = new Condition(definitionsService.getConditionType("eventPropertyCondition"));
        excludeMergedProfilesCondition.setParameter("comparisonOperator", "missing");
        excludeMergedProfilesCondition.setParameter("propertyName", "mergedWith");

        Condition c = new Condition(definitionsService.getConditionType("booleanCondition"));
        c.setParameter("operator", "and");
        c.setParameter("subConditions", Arrays.asList(propertyCondition, excludeMergedProfilesCondition));

        List<Profile> profilesToMerge = persistenceService.query(c, "properties.firstVisit", Profile.class);

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
            persistenceService.save(masterProfile);
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

    public void batchProfilesUpdate(BatchUpdate update) {
        ParserHelper.resolveConditionType(definitionsService, update.getCondition());
        List<Profile> profiles = persistenceService.query(update.getCondition(), null, Profile.class);

        for (Profile profile : profiles) {
            if (PropertyHelper.setProperty(profile, update.getPropertyName(), update.getPropertyValue(), update.getStrategy())) {
//                Event profileUpdated = new Event("profileUpdated", null, profile, null, null, profile, new Date());
//                profileUpdated.setPersistent(false);
//                eventService.send(profileUpdated);
                save(profile);
            }
        }
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
