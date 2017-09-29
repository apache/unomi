/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.unomi.services.services;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.unomi.api.*;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.segments.Segment;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.api.services.QueryService;
import org.apache.unomi.api.services.SegmentService;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.persistence.spi.PropertyHelper;
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

    private SegmentService segmentService;

    private QueryService queryService;

    private Condition purgeProfileQuery;
    private Integer purgeProfileExistTime = 0;
    private Integer purgeProfileInactiveTime = 0;
    private Integer purgeSessionsAndEventsTime = 0;
    private Integer purgeProfileInterval = 0;

    private Timer allPropertyTypesTimer;

    private Timer purgeProfileTimer;

    private List<PropertyType> allPropertyTypes;

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

    public void setSegmentService(SegmentService segmentService) {
        this.segmentService = segmentService;
    }

    public void postConstruct() {
        logger.debug("postConstruct {" + bundleContext.getBundle() + "}");

        processBundleStartup(bundleContext);
        for (Bundle bundle : bundleContext.getBundles()) {
            if (bundle.getBundleContext() != null) {
                processBundleStartup(bundle.getBundleContext());
            }
        }
        bundleContext.addBundleListener(this);
        initializePurge();
        schedulePropertyTypeLoad();
        logger.info("Profile service initialized.");
    }

    public void preDestroy() {
        bundleContext.removeBundleListener(this);
        cancelPurge();
        cancelPropertyTypeLoad();
        logger.info("Profile service shutdown.");
    }

    private void processBundleStartup(BundleContext bundleContext) {
        if (bundleContext == null) {
            return;
        }
        loadPredefinedPersonas(bundleContext);
        loadPredefinedPropertyTypes(bundleContext);
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

    private void schedulePropertyTypeLoad() {
        allPropertyTypesTimer = new Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                try {
                    allPropertyTypes = persistenceService.getAllItems(PropertyType.class);
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                }
            }
        };
        allPropertyTypesTimer.scheduleAtFixedRate(task, 0, 5000);
        logger.info("Scheduled task for property type loading each 5s");
    }

    private void cancelPropertyTypeLoad() {
        if (allPropertyTypesTimer != null) {
            allPropertyTypesTimer.cancel();
            logger.info("Cancelled task for property type loading");
        }
    }

    private void initializePurge() {
        logger.info("Profile purge: Initializing");

        if (purgeProfileInactiveTime > 0 || purgeProfileExistTime > 0 || purgeSessionsAndEventsTime > 0) {
            if (purgeProfileInactiveTime > 0) {
                logger.info("Profile purge: Profile with no visits since {} days, will be purged", purgeProfileInactiveTime);
            }
            if (purgeProfileExistTime > 0) {
                logger.info("Profile purge: Profile created since {} days, will be purged", purgeProfileExistTime);
            }

            purgeProfileTimer = new Timer();
            TimerTask task = new TimerTask() {
                @Override
                public void run() {
                    long t = System.currentTimeMillis();
                    logger.debug("Profile purge: Purge triggered");

                    if (purgeProfileQuery == null) {
                        ConditionType profilePropertyConditionType = definitionsService.getConditionType("profilePropertyCondition");
                        ConditionType booleanCondition = definitionsService.getConditionType("booleanCondition");
                        if (profilePropertyConditionType == null || booleanCondition == null) {
                            // definition service not yet fully instantiate
                            return;
                        }

                        purgeProfileQuery = new Condition(booleanCondition);
                        purgeProfileQuery.setParameter("operator", "or");
                        List<Condition> subConditions = new ArrayList<>();

                        if (purgeProfileInactiveTime > 0) {
                            Condition inactiveTimeCondition = new Condition(profilePropertyConditionType);
                            inactiveTimeCondition.setParameter("propertyName", "lastVisit");
                            inactiveTimeCondition.setParameter("comparisonOperator", "lessThanOrEqualTo");
                            inactiveTimeCondition.setParameter("propertyValueDateExpr", "now-" + purgeProfileInactiveTime + "d");
                            subConditions.add(inactiveTimeCondition);
                        }

                        if (purgeProfileExistTime > 0) {
                            Condition existTimeCondition = new Condition(profilePropertyConditionType);
                            existTimeCondition.setParameter("propertyName", "firstVisit");
                            existTimeCondition.setParameter("comparisonOperator", "lessThanOrEqualTo");
                            existTimeCondition.setParameter("propertyValueDateExpr", "now-" + purgeProfileExistTime + "d");
                            subConditions.add(existTimeCondition);
                        }

                        purgeProfileQuery.setParameter("subConditions", subConditions);
                    }

                    persistenceService.removeByQuery(purgeProfileQuery, Profile.class);

                    if (purgeSessionsAndEventsTime > 0) {
                        persistenceService.purge(getMonth(-purgeSessionsAndEventsTime).getTime());
                    }

                    logger.info("Profile purge: purge executed in {} ms", System.currentTimeMillis() - t);
                }
            };
            purgeProfileTimer.scheduleAtFixedRate(task, getDay(1).getTime(), purgeProfileInterval * 24L * 60L * 60L * 1000L);

            logger.info("Profile purge: purge scheduled with an interval of {} days", purgeProfileInterval);
        } else {
            logger.info("Profile purge: No purge scheduled");
        }
    }

    private void cancelPurge() {
        if (purgeProfileTimer != null) {
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
        return doSearch(query, clazz);
    }

    public PartialList<Session> searchSessions(Query query) {
        return doSearch(query, Session.class);
    }

    private <T extends Item> PartialList<T> doSearch(Query query, Class<T> clazz) {
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
    public boolean setPropertyType(PropertyType property) {
        PropertyType previousProperty = persistenceService.load(property.getItemId(), PropertyType.class);
        if (previousProperty == null) {
            return persistenceService.save(property);
        } else if (merge(previousProperty, property)) {
            return persistenceService.save(previousProperty);
        }
        return false;
    }

    @Override
    public boolean deletePropertyType(String propertyId) {
        return persistenceService.remove(propertyId, PropertyType.class);
    }

    @Override
    public Set<PropertyType> getExistingProperties(String tag, String itemType) {
        Set<PropertyType> filteredProperties = new LinkedHashSet<PropertyType>();
        // TODO: here we limit the result to the definition we have, but what if some properties haven't definition but exist in ES mapping ?
        Set<PropertyType> profileProperties = getPropertyTypeByTag(tag);
        Map<String, Map<String, Object>> itemMapping = persistenceService.getPropertiesMapping(itemType);

        if (itemMapping == null || itemMapping.isEmpty() || itemMapping.get("properties") == null || itemMapping.get("properties").get("properties") == null) {
            return filteredProperties;
        }

        Map<String, Map<String, String>> propMapping = (Map<String, Map<String, String>>) itemMapping.get("properties").get("properties");
        for (PropertyType propertyType : profileProperties) {
            if (propMapping.containsKey(propertyType.getMetadata().getId())) {
                filteredProperties.add(propertyType);
            }
        }
        return filteredProperties;
    }

    public String exportProfilesPropertiesToCsv(Query query) {
        StringBuilder sb = new StringBuilder();
        Set<PropertyType> propertyTypes = getExistingProperties("profileProperties", Profile.ITEM_TYPE);
        PartialList<Profile> profiles = search(query, Profile.class);

        HashMap<String, PropertyType> propertyTypesById = new LinkedHashMap<>();
        for (PropertyType propertyType : propertyTypes) {
            propertyTypesById.put(propertyType.getMetadata().getId(), propertyType);
        }
        for (Profile profile : profiles.getList()) {
            for (String key : profile.getProperties().keySet()) {
                if (!propertyTypesById.containsKey(key)) {
                    propertyTypesById.put(key, null);
                }
            }
        }

        sb.append("profileId;");
        // headers
        for (String propertyId : propertyTypesById.keySet()) {
            sb.append(propertyId);
            sb.append(";");
        }
        sb.append("segments\n");

        // rows
        for (Profile profile : profiles.getList()) {
            sb.append(profile.getItemId());
            sb.append(";");
            for (Map.Entry<String, PropertyType> propertyIdAndType : propertyTypesById.entrySet()) {
                String propertyId = propertyIdAndType.getKey();
                if (profile.getProperties().get(propertyId) != null) {
                    handleExportProperty(sb, profile.getProperties().get(propertyId), propertyIdAndType.getValue());
                } else {
                    sb.append("");
                }
                sb.append(";");
            }
            List<String> segmentNames = new ArrayList<String>();
            for (String segment : profile.getSegments()) {
                Segment s = segmentService.getSegmentDefinition(segment);
                segmentNames.add(csvEncode(s.getMetadata().getName()));
            }
            sb.append(csvEncode(StringUtils.join(segmentNames, ",")));
            sb.append('\n');
        }
        return sb.toString();
    }

    // TODO may be moved this in a specific Export Utils Class and improve it to handle date format, ...
    private void handleExportProperty(StringBuilder sb, Object propertyValue, PropertyType propertyType) {
        if (propertyValue instanceof Collection && propertyType != null && propertyType.isMultivalued() != null && propertyType.isMultivalued() ) {
            Collection propertyValues = (Collection) propertyValue;
            Collection encodedValues = new ArrayList(propertyValues.size());
            for (Object value : propertyValues) {
                encodedValues.add(csvEncode(value.toString()));
            }
            sb.append(csvEncode(StringUtils.join(encodedValues, ",")));
        } else {
            sb.append(csvEncode(propertyValue.toString()));
        }
    }

    private String csvEncode(String input) {
        if (StringUtils.containsAny(input, '\n', '"', ',')) {
            return "\"" + input.replace("\"","\"\"") + "\"";
        }
        return input;
    }

    public PartialList<Profile> findProfilesByPropertyValue(String propertyName, String propertyValue, int offset, int size, String sortBy) {
        return persistenceService.query(propertyName, propertyValue, sortBy, Profile.class, offset, size);
    }

    public Profile load(String profileId) {
        return persistenceService.load(profileId, Profile.class);
    }

    public Profile save(Profile profile) {
        if (profile.getItemId() == null) {
            return null;
        }
        persistenceService.save(profile);
        return persistenceService.load(profile.getItemId(), Profile.class);
    }

    public boolean saveOrMerge(Profile profile) {
        Profile previousProfile = persistenceService.load(profile.getItemId(), Profile.class);
        if (previousProfile == null) {
            return persistenceService.save(profile);
        } else if (merge(previousProfile, profile)) {
            return persistenceService.save(previousProfile);
        }

        return false;
    }

    public Persona savePersona(Persona profile) {
        if (persistenceService.load(profile.getItemId(), Persona.class) == null) {
            Session session = new PersonaSession(UUID.randomUUID().toString(), profile, new Date());
            persistenceService.save(profile);
            persistenceService.save(session);
        } else {
            persistenceService.save(profile);
        }

        return persistenceService.load(profile.getItemId(), Persona.class);
    }

    public void delete(String profileId, boolean persona) {
        if (persona) {
            persistenceService.remove(profileId, Persona.class);
        } else {
            Condition mergeCondition = new Condition(definitionsService.getConditionType("profilePropertyCondition"));
            mergeCondition.setParameter("propertyName", "mergedWith");
            mergeCondition.setParameter("comparisonOperator", "equals");
            mergeCondition.setParameter("propertyValue", profileId);
            persistenceService.removeByQuery(mergeCondition, Profile.class);

            persistenceService.remove(profileId, Profile.class);
        }
    }

    public Profile mergeProfiles(Profile masterProfile, List<Profile> profilesToMerge) {

        // now let's remove all the already merged profiles from the list.
        List<Profile> filteredProfilesToMerge = new ArrayList<>();

        for (Profile filteredProfile : profilesToMerge) {
            if (!filteredProfile.getItemId().equals(masterProfile.getItemId())) {
                filteredProfilesToMerge.add(filteredProfile);
            }
        }

        if (filteredProfilesToMerge.isEmpty()) {
            return masterProfile;
        }

        profilesToMerge = filteredProfilesToMerge;

        Set<String> allProfileProperties = new LinkedHashSet<>();
        for (Profile profile : profilesToMerge) {
            allProfileProperties.addAll(profile.getProperties().keySet());
        }

        Collection<PropertyType> profilePropertyTypes = getAllPropertyTypes("profiles");
        Map<String, PropertyType> profilePropertyTypeById = new HashMap<>();
        for (PropertyType propertyType : profilePropertyTypes) {
            profilePropertyTypeById.put(propertyType.getMetadata().getId(), propertyType);
        }
        Set<String> profileIdsToMerge = new TreeSet<>();
        for (Profile profileToMerge : profilesToMerge) {
            profileIdsToMerge.add(profileToMerge.getItemId());
        }
        logger.info("Merging profiles " + profileIdsToMerge + " into profile " + masterProfile.getItemId());
        boolean masterProfileChanged = false;

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
                    // todo: improper algorithm… it is possible that the defaultMergeStrategy couldn't be resolved here
                    logger.warn("Couldn't resolve strategy " + propertyMergeStrategyId + " for property " + profileProperty + ", using default strategy instead");
                    propertyMergeStrategyId = "defaultMergeStrategy";
                    propertyMergeStrategyType = definitionsService.getPropertyMergeStrategyType(propertyMergeStrategyId);
                }
            }

            // todo: find a way to avoid resolving PropertyMergeStrategyExecutor every time?
            Collection<ServiceReference<PropertyMergeStrategyExecutor>> matchingPropertyMergeStrategyExecutors;
            try {
                matchingPropertyMergeStrategyExecutors = bundleContext.getServiceReferences(PropertyMergeStrategyExecutor.class, propertyMergeStrategyType.getFilter());
                for (ServiceReference<PropertyMergeStrategyExecutor> propertyMergeStrategyExecutorReference : matchingPropertyMergeStrategyExecutors) {
                    PropertyMergeStrategyExecutor propertyMergeStrategyExecutor = bundleContext.getService(propertyMergeStrategyExecutorReference);
                    masterProfileChanged |= propertyMergeStrategyExecutor.mergeProperty(profileProperty, propertyType, profilesToMerge, masterProfile);
                }
            } catch (InvalidSyntaxException e) {
                logger.error("Error retrieving strategy implementation", e);
            }

        }

        // we now have to merge the profile's segments
        for (Profile profile : profilesToMerge) {
            if (profile.getSegments() != null && profile.getSegments().size() > 0) {
                masterProfile.getSegments().addAll(profile.getSegments());
                masterProfileChanged = true;
            }
        }

        if (masterProfileChanged) {
            persistenceService.save(masterProfile);
        }

        return masterProfile;
    }

    public PartialList<Session> getProfileSessions(String profileId, String query, int offset, int size, String sortBy) {
        if (StringUtils.isNotBlank(query)) {
            return persistenceService.queryFullText("profileId", profileId, query, sortBy, Session.class, offset, size);
        } else {
            return persistenceService.query("profileId", profileId, sortBy, Session.class, offset, size);
        }
    }

    public String getPropertyTypeMapping(String fromPropertyTypeId) {
        Collection<PropertyType> types = getPropertyTypeByMapping(fromPropertyTypeId);
        if (types.size() > 0) {
            return types.iterator().next().getMetadata().getId();
        }
        return null;
    }

    public Session loadSession(String sessionId, Date dateHint) {
        Session s = persistenceService.load(sessionId, dateHint, Session.class);
        if (s == null && dateHint != null) {
            GregorianCalendar gc = new GregorianCalendar();
            gc.setTime(dateHint);
            if (gc.get(Calendar.DAY_OF_MONTH) == 1) {
                gc.add(Calendar.DAY_OF_MONTH, -1);
                s = persistenceService.load(sessionId, gc.getTime(), Session.class);
            }
        }
        return s;
    }

    public Session saveSession(Session session) {
        if (session.getItemId() == null) {
            return null;
        }
        return persistenceService.save(session) ? session : null;
    }

    public PartialList<Session> findProfileSessions(String profileId) {
        return persistenceService.query("profileId", profileId, "timeStamp:desc", Session.class, 0, 50);
    }

    @Override
    public boolean matchCondition(Condition condition, Profile profile, Session session) {
        ParserHelper.resolveConditionType(definitionsService, condition);

        if (condition.getConditionTypeId().equals("booleanCondition")) {
            List<Condition> subConditions = (List<Condition>) condition.getParameter("subConditions");
            boolean isAnd = "and".equals(condition.getParameter("operator"));
            for (Condition subCondition : subConditions) {
                if (isAnd && !matchCondition(subCondition, profile, session)) {
                    return false;
                }
                if (!isAnd && matchCondition(subCondition, profile, session)) {
                    return true;
                }
            }
            return subConditions.size() > 0 && isAnd;
        } else {
            Condition profileCondition = definitionsService.extractConditionByTag(condition, "profileCondition");
            Condition sessionCondition = definitionsService.extractConditionByTag(condition, "sessionCondition");
            if (profileCondition != null && !persistenceService.testMatch(profileCondition, profile)) {
                return false;
            }
            return !(sessionCondition != null && !persistenceService.testMatch(sessionCondition, session));
        }
    }

    public void batchProfilesUpdate(BatchUpdate update) {
        ParserHelper.resolveConditionType(definitionsService, update.getCondition());
        List<Profile> profiles = persistenceService.query(update.getCondition(), null, Profile.class);

        for (Profile profile : profiles) {
            if (PropertyHelper.setProperty(profile, update.getPropertyName(), update.getPropertyValue(), update.getStrategy())) {
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


    public Collection<PropertyType> getAllPropertyTypes(String target) {
        return persistenceService.query("target", target, null, PropertyType.class);
    }

    public Map<String, Collection<PropertyType>> getAllPropertyTypes() {
        Collection<PropertyType> props = persistenceService.getAllItems(PropertyType.class, 0, -1, "rank").getList();

        HashMap<String, Collection<PropertyType>> propertyTypes = new HashMap<>();
        for (PropertyType prop : props) {
            if (!propertyTypes.containsKey(prop.getTarget())) {
                propertyTypes.put(prop.getTarget(), new LinkedHashSet<PropertyType>());
            }
            propertyTypes.get(prop.getTarget()).add(prop);
        }
        return propertyTypes;
    }

    public Set<PropertyType> getPropertyTypeByTag(String tag) {
        return getPropertyTypesBy("metadata.tags", tag);
    }

    public Set<PropertyType> getPropertyTypeBySystemTag(String tag) {
        return getPropertyTypesBy("metadata.systemTags", tag);
    }

    private Set<PropertyType> getPropertyTypesBy( String fieldName, String fieldValue) {
        Set<PropertyType> propertyTypes = new LinkedHashSet<PropertyType>();
        Collection<PropertyType> directPropertyTypes = persistenceService.query(fieldName, fieldValue, "rank", PropertyType.class);

        if (directPropertyTypes != null) {
            propertyTypes.addAll(directPropertyTypes);
        }

        return propertyTypes;
    }

    public Collection<PropertyType> getPropertyTypeByMapping(String propertyName) {
        Collection<PropertyType> l = new TreeSet<PropertyType>(new Comparator<PropertyType>() {
            @Override
            public int compare(PropertyType o1, PropertyType o2) {
                if (o1.getRank() == o2.getRank()) {
                    return o1.getMetadata().getName().compareTo(o1.getMetadata().getName());
                } else if (o1.getRank() < o2.getRank()) {
                    return -1;
                } else {
                    return 1;
                }
            }
        });

        for (PropertyType propertyType : allPropertyTypes) {
            if (propertyType.getAutomaticMappingsFrom() != null && propertyType.getAutomaticMappingsFrom().contains(propertyName)) {
                l.add(propertyType);
            }
        }
        return l;
    }

    public PropertyType getPropertyType(String id) {
        return persistenceService.load(id, PropertyType.class);
    }

    public PartialList<Session> getPersonaSessions(String personaId, int offset, int size, String sortBy) {
        return persistenceService.query("profileId", personaId, sortBy, Session.class, offset, size);
    }

    public PersonaWithSessions savePersonaWithSessions(PersonaWithSessions personaToSave){
        if(personaToSave !=null){
            //Generate a UUID if no itemId is set on the persona
            if(personaToSave.getPersona().getItemId() == null){
                personaToSave.getPersona().setItemId("persona-"+UUID.randomUUID().toString());
            }
            boolean savedPersona = persistenceService.save(personaToSave.getPersona());
            //Browse persona sessions
            List<PersonaSession> sessions = personaToSave.getSessions();
            for (PersonaSession session : sessions) {
                //Generate a UUID if no itemId is set on the session
                if(session.getItemId() == null){
                    session.setItemId(UUID.randomUUID().toString());
                }
                //link the session to the persona
                session.setProfile(personaToSave.getPersona());
                persistenceService.save(session);
            }
            return personaToSave;
        }
        return null;
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

                String itemId = persona.getPersona().getItemId();
                if (persistenceService.load(itemId, Persona.class) != null) {
                    persistenceService.save(persona.getPersona());
                }

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

    private void loadPredefinedPropertyTypes(BundleContext bundleContext) {
        Enumeration<URL> predefinedPropertyTypeEntries = bundleContext.getBundle().findEntries("META-INF/cxs/properties", "*.json", true);
        if (predefinedPropertyTypeEntries == null) {
            return;
        }

        while (predefinedPropertyTypeEntries.hasMoreElements()) {
            URL predefinedPropertyTypeURL = predefinedPropertyTypeEntries.nextElement();
            logger.debug("Found predefined property type at " + predefinedPropertyTypeURL + ", loading... ");

            try {
                PropertyType propertyType = CustomObjectMapper.getObjectMapper().readValue(predefinedPropertyTypeURL, PropertyType.class);
                if (getPropertyType(propertyType.getMetadata().getId()) == null) {
                    String[] splitPath = predefinedPropertyTypeURL.getPath().split("/");
                    String target = splitPath[4];
                    propertyType.setTarget(target);

                    persistenceService.save(propertyType);
                }
            } catch (IOException e) {
                logger.error("Error while loading properties " + predefinedPropertyTypeURL, e);
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

    private <T> boolean merge(T target, T object) {
        if (object != null) {
            try {
                Map<String,Object> objectValues = PropertyUtils.describe(object);
                Map<String,Object> targetValues = PropertyUtils.describe(target);
                if (merge(targetValues, objectValues)) {
                    BeanUtils.populate(target, targetValues);
                    return true;
                }
            } catch (ReflectiveOperationException e) {
                logger.error("Cannot merge properties",e);
            }
        }
        return false;
    }

    private boolean merge(Map<String, Object> target, Map<String, Object> object) {
        boolean changed = false;
        for (Map.Entry<String, Object> newEntry : object.entrySet()) {
            if (newEntry.getValue() != null) {
                if (newEntry.getValue() instanceof Collection) {
                    target.put(newEntry.getKey(), newEntry.getValue());
                    changed = true;
                } else if (newEntry.getValue() instanceof Map) {
                    Map<String,Object> currentMap = (Map) target.get(newEntry.getKey());
                    if (currentMap == null) {
                        target.put(newEntry.getKey(), newEntry.getValue());
                        changed = true;
                    } else {
                        changed |= merge(currentMap, (Map) newEntry.getValue());
                    }
                } else if (newEntry.getValue().getClass().getPackage().getName().equals("java.lang")) {
                    if (newEntry.getValue() != null && !newEntry.getValue().equals(target.get(newEntry.getKey()))) {
                        target.put(newEntry.getKey(), newEntry.getValue());
                        changed = true;
                    }
                } else {
                    changed |= merge(target.get(newEntry.getKey()), newEntry.getValue());
                }
            } else {
                if (target.containsKey(newEntry.getKey())) {
                    target.remove(newEntry.getKey());
                    changed = true;
                }
            }
        }
        return changed;
    }

}
