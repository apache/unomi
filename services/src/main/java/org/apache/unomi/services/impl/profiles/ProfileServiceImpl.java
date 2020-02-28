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

package org.apache.unomi.services.impl.profiles;

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
import org.apache.unomi.api.services.SchedulerService;
import org.apache.unomi.api.services.SegmentService;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.persistence.spi.PropertyHelper;
import org.apache.unomi.services.impl.ParserHelper;
import org.osgi.framework.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.apache.unomi.persistence.spi.CustomObjectMapper.getObjectMapper;

public class ProfileServiceImpl implements ProfileService, SynchronousBundleListener {

    /**
     * This class is responsible for storing property types and permits optimized access to them.
     * In order to assure data consistency, thread-safety and performance, this class is immutable and every operation on
     * property types requires creating a new instance (copy-on-write).
     */
    private static class PropertyTypes {
        private List<PropertyType> allPropertyTypes;
        private Map<String, PropertyType> propertyTypesById = new HashMap<>();
        private Map<String, List<PropertyType>> propertyTypesByTags = new HashMap<>();
        private Map<String, List<PropertyType>> propertyTypesBySystemTags = new HashMap<>();
        private Map<String, List<PropertyType>> propertyTypesByTarget = new HashMap<>();

        public PropertyTypes(List<PropertyType> allPropertyTypes) {
            this.allPropertyTypes = new ArrayList<>(allPropertyTypes);
            propertyTypesById = new HashMap<>();
            propertyTypesByTags = new HashMap<>();
            propertyTypesBySystemTags = new HashMap<>();
            propertyTypesByTarget = new HashMap<>();
            for (PropertyType propertyType : allPropertyTypes) {
                propertyTypesById.put(propertyType.getItemId(), propertyType);
                for (String propertyTypeTag : propertyType.getMetadata().getTags()) {
                    updateListMap(propertyTypesByTags, propertyType, propertyTypeTag);
                }
                for (String propertyTypeSystemTag : propertyType.getMetadata().getSystemTags()) {
                    updateListMap(propertyTypesBySystemTags, propertyType, propertyTypeSystemTag);
                }
                updateListMap(propertyTypesByTarget, propertyType, propertyType.getTarget());
            }
        }

        public List<PropertyType> getAll() {
            return allPropertyTypes;
        }

        public PropertyType get(String propertyId) {
            return propertyTypesById.get(propertyId);
        }

        public Map<String, List<PropertyType>> getAllByTarget() {
            return propertyTypesByTarget;
        }

        public List<PropertyType> getByTag(String tag) {
            return propertyTypesByTags.get(tag);
        }

        public List<PropertyType> getBySystemTag(String systemTag) {
            return propertyTypesBySystemTags.get(systemTag);
        }

        public List<PropertyType> getByTarget(String target) {
            return propertyTypesByTarget.get(target);
        }

        public PropertyTypes with(PropertyType newProperty) {
            return with(Collections.singletonList(newProperty));
        }

        /**
         * Creates a new instance of this class containing given property types.
         * If property types with the same ID existed before, they will be replaced by the new ones.
         * @param newProperties list of property types to change
         * @return new instance
         */
        public PropertyTypes with(List<PropertyType> newProperties) {
            Map<String, PropertyType> updatedProperties = new HashMap<>();
            for (PropertyType property : newProperties) {
                if (propertyTypesById.containsKey(property.getItemId())) {
                    updatedProperties.put(property.getItemId(), property);
                }
            }

            List<PropertyType> newPropertyTypes = Stream.concat(
                    allPropertyTypes.stream().map(property -> updatedProperties.getOrDefault(property.getItemId(), property)),
                    newProperties.stream().filter(property -> !propertyTypesById.containsKey(property.getItemId()))
            ).collect(Collectors.toList());

            return new PropertyTypes(newPropertyTypes);
        }

        /**
         * Creates a new instance of this class containing all property types except the one with given ID.
         * @param propertyId ID of the property to delete
         * @return new instance
         */
        public PropertyTypes without(String propertyId) {
            List<PropertyType> newPropertyTypes = allPropertyTypes.stream()
                .filter(property -> property.getItemId().equals(propertyId))
                .collect(Collectors.toList());

            return new PropertyTypes(newPropertyTypes);
        }

        private void updateListMap(Map<String, List<PropertyType>> listMap, PropertyType propertyType, String key) {
            List<PropertyType> propertyTypes = listMap.get(key);
            if (propertyTypes == null) {
                propertyTypes = new ArrayList<>();
            }
            propertyTypes.add(propertyType);
            listMap.put(key, propertyTypes);
        }

    }

    private static final Logger logger = LoggerFactory.getLogger(ProfileServiceImpl.class.getName());

    private BundleContext bundleContext;

    private PersistenceService persistenceService;

    private DefinitionsService definitionsService;

    private SchedulerService schedulerService;

    private SegmentService segmentService;

    private Condition purgeProfileQuery;
    private Integer purgeProfileExistTime = 0;
    private Integer purgeProfileInactiveTime = 0;
    private Integer purgeSessionsAndEventsTime = 0;
    private Integer purgeProfileInterval = 0;
    private long propertiesRefreshInterval = 10000;

    private PropertyTypes propertyTypes;

    private boolean forceRefreshOnSave = false;

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

    public void setSchedulerService(SchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }

    public void setSegmentService(SegmentService segmentService) {
        this.segmentService = segmentService;
    }

    public void setForceRefreshOnSave(boolean forceRefreshOnSave) {
        this.forceRefreshOnSave = forceRefreshOnSave;
    }

    public void setPropertiesRefreshInterval(long propertiesRefreshInterval) {
        this.propertiesRefreshInterval = propertiesRefreshInterval;
    }

    public void postConstruct() {
        logger.debug("postConstruct {" + bundleContext.getBundle() + "}");

        loadPropertyTypesFromPersistence();
        processBundleStartup(bundleContext);
        for (Bundle bundle : bundleContext.getBundles()) {
            if (bundle.getBundleContext() != null && bundle.getBundleId() != bundleContext.getBundle().getBundleId()) {
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
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                reloadPropertyTypes(false);
            }
        };
        schedulerService.getScheduleExecutorService().scheduleAtFixedRate(task, 10000, propertiesRefreshInterval, TimeUnit.MILLISECONDS);
        logger.info("Scheduled task for property type loading each 10s");
    }

    public void reloadPropertyTypes(boolean refresh) {
        try {
            if (refresh) {
                persistenceService.refresh();
            }
            loadPropertyTypesFromPersistence();
        } catch (Throwable t) {
            logger.error("Error loading property types from persistence back-end", t);
        }
    }

    private void loadPropertyTypesFromPersistence() {
        try {
            this.propertyTypes = new PropertyTypes(persistenceService.getAllItems(PropertyType.class, 0, -1, "rank").getList());
        } catch (Exception e) {
            logger.error("Error loading property types from persistence service", e);
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

            TimerTask task = new TimerTask() {
                @Override
                public void run() {
                    try {
                        long purgeStartTime = System.currentTimeMillis();
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

                        logger.info("Profile purge: purge executed in {} ms", System.currentTimeMillis() - purgeStartTime);
                    } catch (Throwable t) {
                        logger.error("Error while purging profiles", t);
                    }
                }
            };
            schedulerService.getScheduleExecutorService().scheduleAtFixedRate(task, 1, purgeProfileInterval, TimeUnit.DAYS);

            logger.info("Profile purge: purge scheduled with an interval of {} days", purgeProfileInterval);
        } else {
            logger.info("Profile purge: No purge scheduled");
        }
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
        boolean result = false;
        if (previousProperty == null) {
            result = persistenceService.save(property);
            propertyTypes = propertyTypes.with(property);
        } else if (merge(previousProperty, property)) {
            result = persistenceService.save(previousProperty);
            propertyTypes = propertyTypes.with(previousProperty);
        }
        return result;
    }

    @Override
    public boolean deletePropertyType(String propertyId) {
        boolean result = persistenceService.remove(propertyId, PropertyType.class);
        propertyTypes = propertyTypes.without(propertyId);
        return result;
    }

    @Override
    public Set<PropertyType> getExistingProperties(String tag, String itemType) {
        return getExistingProperties(tag, itemType, false);
    }

    @Override
    public Set<PropertyType> getExistingProperties(String tag, String itemType, boolean systemTag) {
        Set<PropertyType> filteredProperties = new LinkedHashSet<PropertyType>();
        // TODO: here we limit the result to the definition we have, but what if some properties haven't definition but exist in ES mapping ?
        Set<PropertyType> profileProperties = systemTag ? getPropertyTypeBySystemTag(tag) : getPropertyTypeByTag(tag);
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
        if (propertyValue instanceof Collection && propertyType != null && propertyType.isMultivalued() != null && propertyType.isMultivalued()) {
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
            return "\"" + input.replace("\"", "\"\"") + "\"";
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
        return save(profile, forceRefreshOnSave);
    }

    private Profile save(Profile profile, boolean forceRefresh) {
        if (profile.getItemId() == null) {
            return null;
        }
        profile.setSystemProperty("lastUpdated", new Date());
        if (persistenceService.save(profile)) {
            if (forceRefresh) {
                // triggering a load will force an in-place refresh, that may be expensive in performance but will make data immediately available.
                return persistenceService.load(profile.getItemId(), Profile.class);
            } else {
                return profile;
            }
        }
        return null;
    }

    public Profile saveOrMerge(Profile profile) {
        Profile previousProfile = persistenceService.load(profile.getItemId(), Profile.class);
        profile.setSystemProperty("lastUpdated", new Date());
        if (previousProfile == null) {
            if (persistenceService.save(profile)) {
                return profile;
            } else {
                return null;
            }
        } else if (merge(previousProfile, profile)) {
            if (persistenceService.save(previousProfile)) {
                return previousProfile;
            } else {
                return null;
            }
        }
        return null;
    }

    public Persona savePersona(Persona profile) {
        profile.setSystemProperty("lastUpdated", new Date());
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

        Collection<PropertyType> profilePropertyTypes = getTargetPropertyTypes("profiles");
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

        // merge System properties
        for (Profile profile : profilesToMerge) {
            masterProfileChanged = mergeSystemProperties(masterProfile.getSystemProperties(), profile.getSystemProperties()) || masterProfileChanged;
        }

        // we now have to merge the profile's segments
        for (Profile profile : profilesToMerge) {
            if (profile.getSegments() != null && profile.getSegments().size() > 0) {
                masterProfile.getSegments().addAll(profile.getSegments());
                // TODO better segments diff calculation
                masterProfileChanged = true;
            }
        }

        // we now have to merge the profile's consents
        for (Profile profile : profilesToMerge) {
            if (profile.getConsents() != null && profile.getConsents().size() > 0) {
                for(String consentId : profile.getConsents().keySet()) {
                    if(masterProfile.getConsents().containsKey(consentId)) {
                        if(masterProfile.getConsents().get(consentId).getRevokeDate().before(new Date())) {
                            masterProfile.getConsents().remove(consentId);
                            masterProfileChanged = true;
                        } else if(masterProfile.getConsents().get(consentId).getStatusDate().before(profile.getConsents().get(consentId).getStatusDate())) {
                            masterProfile.getConsents().replace(consentId, profile.getConsents().get(consentId));
                            masterProfileChanged = true;
                        }
                    } else {
                        masterProfile.getConsents().put(consentId, profile.getConsents().get(consentId));
                        masterProfileChanged = true;
                    }

                }
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
        if (session.getProfile() != null && session.getProfile().getProperties() != null) {
            session.getProfile().setProperties(removePersonalIdentifiersFromSessionProfile(session.getProfile().getProperties()));
        }
        return persistenceService.save(session) ? session : null;
    }

    private Map removePersonalIdentifiersFromSessionProfile(final Map<String, Object> profileProperties) {
        Set<PropertyType> personalIdsProps = getPropertyTypeBySystemTag(PERSONAL_IDENTIFIER_TAG_NAME);
        final List personalIdsPropsNames = new ArrayList<String>();
        personalIdsProps.forEach(propType -> personalIdsPropsNames.add(propType.getMetadata().getId()));
        Set propsToRemove = new HashSet<String>();
        profileProperties.keySet().forEach(propKey -> {
            if (personalIdsPropsNames.contains(propKey)) {
                propsToRemove.add(propKey);
            }
        });
        propsToRemove.forEach(propId -> profileProperties.remove(propId));
        return profileProperties;
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
            Condition profileCondition = definitionsService.extractConditionBySystemTag(condition, "profileCondition");
            Condition sessionCondition = definitionsService.extractConditionBySystemTag(condition, "sessionCondition");
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


    public Collection<PropertyType> getTargetPropertyTypes(String target) {
        if (target == null) {
            return null;
        }
        Collection<PropertyType> result = propertyTypes.getByTarget(target);
        if (result == null) {
            return new ArrayList<>();
        }
        return result;
    }

    public Map<String, Collection<PropertyType>> getTargetPropertyTypes() {
        return new HashMap<>(propertyTypes.getAllByTarget());
    }

    public Set<PropertyType> getPropertyTypeByTag(String tag) {
        if (tag == null) {
            return null;
        }
        List<PropertyType> result = propertyTypes.getByTag(tag);
        if (result == null) {
            return new LinkedHashSet<>();
        }
        return new LinkedHashSet<>(result);
    }

    public Set<PropertyType> getPropertyTypeBySystemTag(String tag) {
        if (tag == null) {
            return null;
        }
        List<PropertyType> result = propertyTypes.getBySystemTag(tag);
        if (result == null) {
            return new LinkedHashSet<>();
        }
        return new LinkedHashSet<>(result);
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

        for (PropertyType propertyType : propertyTypes.getAll()) {
            if (propertyType.getAutomaticMappingsFrom() != null && propertyType.getAutomaticMappingsFrom().contains(propertyName)) {
                l.add(propertyType);
            }
        }
        return l;
    }

    public PropertyType getPropertyType(String id) {
        return propertyTypes.get(id);
    }

    public PartialList<Session> getPersonaSessions(String personaId, int offset, int size, String sortBy) {
        return persistenceService.query("profileId", personaId, sortBy, Session.class, offset, size);
    }

    public PersonaWithSessions savePersonaWithSessions(PersonaWithSessions personaToSave) {
        if (personaToSave != null) {
            //Generate a UUID if no itemId is set on the persona
            if (personaToSave.getPersona().getItemId() == null) {
                personaToSave.getPersona().setItemId("persona-" + UUID.randomUUID().toString());
            }
            boolean savedPersona = persistenceService.save(personaToSave.getPersona());
            //Browse persona sessions
            List<PersonaSession> sessions = personaToSave.getSessions();
            for (PersonaSession session : sessions) {
                //Generate a UUID if no itemId is set on the session
                if (session.getItemId() == null) {
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

    public void setPropertyTypeTarget(URL predefinedPropertyTypeURL, PropertyType propertyType) {
        if (StringUtils.isBlank(propertyType.getTarget())) {
            String[] splitPath = predefinedPropertyTypeURL.getPath().split("/");
            String target = splitPath[4];
            if (StringUtils.isNotBlank(target)) {
                propertyType.setTarget(target);
            }
        }
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
                PersonaWithSessions persona = getObjectMapper().readValue(predefinedPersonaURL, PersonaWithSessions.class);

                String itemId = persona.getPersona().getItemId();
                // Register only if persona does not exist yet
                if (persistenceService.load(itemId, Persona.class) == null) {
                    persistenceService.save(persona.getPersona());

                    List<PersonaSession> sessions = persona.getSessions();
                    for (PersonaSession session : sessions) {
                        session.setProfile(persona.getPersona());
                        persistenceService.save(session);
                    }
                    logger.info("Predefined persona with id {} registered", itemId);
                } else {
                    logger.info("The predefined persona with id {} is already registered, this persona will be skipped", itemId);
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

        List<PropertyType> bundlePropertyTypes = new ArrayList<>();
        while (predefinedPropertyTypeEntries.hasMoreElements()) {
            URL predefinedPropertyTypeURL = predefinedPropertyTypeEntries.nextElement();
            logger.debug("Found predefined property type at " + predefinedPropertyTypeURL + ", loading... ");

            try {
                PropertyType propertyType = CustomObjectMapper.getObjectMapper().readValue(predefinedPropertyTypeURL, PropertyType.class);
                // Register only if property type does not exist yet
                if (getPropertyType(propertyType.getMetadata().getId()) == null) {

                    setPropertyTypeTarget(predefinedPropertyTypeURL, propertyType);

                    persistenceService.save(propertyType);
                    bundlePropertyTypes.add(propertyType);
                    logger.info("Predefined property type with id {} registered", propertyType.getMetadata().getId());
                } else {
                    logger.info("The predefined property type with id {} is already registered, this property type will be skipped", propertyType.getMetadata().getId());
                }
            } catch (IOException e) {
                logger.error("Error while loading properties " + predefinedPropertyTypeURL, e);
            }
        }
        propertyTypes = propertyTypes.with(bundlePropertyTypes);
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
                Map<String, Object> objectValues = PropertyUtils.describe(object);
                Map<String, Object> targetValues = PropertyUtils.describe(target);
                if (merge(targetValues, objectValues)) {
                    BeanUtils.populate(target, targetValues);
                    return true;
                }
            } catch (ReflectiveOperationException e) {
                logger.error("Cannot merge properties", e);
            }
        }
        return false;
    }

    private boolean merge(Map<String, Object> target, Map<String, Object> object) {
        boolean changed = false;
        for (Map.Entry<String, Object> newEntry : object.entrySet()) {
            if (newEntry.getValue() != null) {
                String packageName = newEntry.getValue().getClass().getPackage().getName();
                if (newEntry.getValue() instanceof Collection) {
                    target.put(newEntry.getKey(), newEntry.getValue());
                    changed = true;
                } else if (newEntry.getValue() instanceof Map) {
                    Map<String, Object> currentMap = (Map) target.get(newEntry.getKey());
                    if (currentMap == null) {
                        target.put(newEntry.getKey(), newEntry.getValue());
                        changed = true;
                    } else {
                        changed |= merge(currentMap, (Map) newEntry.getValue());
                    }
                } else if (StringUtils.equals(packageName, "java.lang")) {
                    if (newEntry.getValue() != null && !newEntry.getValue().equals(target.get(newEntry.getKey()))) {
                        target.put(newEntry.getKey(), newEntry.getValue());
                        changed = true;
                    }
                } else if (newEntry.getValue().getClass().isEnum()) {
	            	 target.put(newEntry.getKey(), newEntry.getValue());
	                 changed = true;
                } else {
                    if (target.get(newEntry.getKey()) != null) {
                        changed |= merge(target.get(newEntry.getKey()), newEntry.getValue());
                    } else {
                        target.put(newEntry.getKey(), newEntry.getValue());
                        changed = true;
                    }
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

    private boolean mergeSystemProperties(Map<String, Object> targetProperties, Map<String, Object> sourceProperties) {
        boolean changed = false;
        for (Map.Entry<String, Object> sourceProperty : sourceProperties.entrySet()) {
            if (sourceProperty.getValue() != null) {
                if (!targetProperties.containsKey(sourceProperty.getKey())) {
                    targetProperties.put(sourceProperty.getKey(), sourceProperty.getValue());
                    changed = true;
                } else {
                    Object targetProperty = targetProperties.get(sourceProperty.getKey());

                    if (targetProperty instanceof Map && sourceProperty.getValue() instanceof Map) {
                        // merge Maps like "goals", "campaigns"
                        @SuppressWarnings("unchecked")
                        Map<String, Object> mapSourceProp = (Map<String, Object>) sourceProperty.getValue();
                        @SuppressWarnings("unchecked")
                        Map<String, Object> mapTargetProp = (Map<String, Object>) targetProperty;

                        for (Map.Entry<String, ?> mapSourceEntry : mapSourceProp.entrySet()) {
                            if (!mapTargetProp.containsKey(mapSourceEntry.getKey())) {
                                mapTargetProp.put(mapSourceEntry.getKey(), mapSourceEntry.getValue());
                                changed = true;
                            }
                        }
                    } else if (targetProperty instanceof Collection && sourceProperty.getValue() instanceof Collection) {
                        // merge Collections like "lists"
                        Collection sourceCollection = (Collection) sourceProperty.getValue();
                        Collection targetCollection = (Collection) targetProperty;

                        for (Object sourceItem : sourceCollection) {
                            if (!targetCollection.contains(sourceItem)) {
                                try {
                                    targetCollection.add(sourceItem);
                                    changed = true;
                                } catch (Exception e) {
                                    // may be Collection type issue
                                }
                            }
                        }
                    }
                }
            }
        }

        return changed;
    }

    public void refresh() {
        reloadPropertyTypes(true);
    }
}
