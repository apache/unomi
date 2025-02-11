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
import org.apache.unomi.api.services.*;
import org.apache.unomi.api.tasks.ScheduledTask;
import org.apache.unomi.api.utils.ParserHelper;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.persistence.spi.PropertyHelper;
import org.apache.unomi.services.sorts.ControlGroupPersonalizationStrategy;
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
         *
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
         *
         * @param propertyId ID of the property to delete
         * @return new instance
         */
        public PropertyTypes without(String propertyId) {
            List<PropertyType> newPropertyTypes = allPropertyTypes.stream()
                    .filter(property -> !property.getItemId().equals(propertyId))
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

    private static final Logger LOGGER = LoggerFactory.getLogger(ProfileServiceImpl.class.getName());

    private BundleContext bundleContext;

    private PersistenceService persistenceService;

    private DefinitionsService definitionsService;

    private SchedulerService schedulerService;

    private SegmentService segmentService;

    private Integer purgeProfileExistTime = 0;
    private Integer purgeProfileInactiveTime = 0;

    /**
     * Use purgeSessionExistTime and purgeEventExistTime instead
     */
    @Deprecated
    private Integer purgeSessionsAndEventsTime = 0;
    private Integer purgeSessionExistTime = 0;
    private Integer purgeEventExistTime = 0;
    private Integer purgeProfileInterval = 0;
    private ScheduledTask propertyTypeLoadTask;
    private ScheduledTask purgeTask;
    private long propertiesRefreshInterval = 10000;

    private PropertyTypes propertyTypes;

    private boolean forceRefreshOnSave = false;

    private ExecutionContextManager contextManager;

    public ProfileServiceImpl() {
        LOGGER.info("Initializing profile service...");
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

    public void setContextManager(ExecutionContextManager contextManager) {
        this.contextManager = contextManager;
    }

    public void postConstruct() {
        LOGGER.debug("postConstruct {{}}", bundleContext.getBundle());

        contextManager.executeAsSystem(() -> {
            loadPropertyTypesFromPersistence();
            processBundleStartup(bundleContext);
            for (Bundle bundle : bundleContext.getBundles()) {
                if (bundle.getBundleContext() != null && bundle.getBundleId() != bundleContext.getBundle().getBundleId()) {
                    processBundleStartup(bundle.getBundleContext());
                }
            }
            bundleContext.addBundleListener(this);
            initializeDefaultPurgeValuesIfNecessary();
            initializePurge();
            schedulePropertyTypeLoad();
        });
        LOGGER.info("Profile service initialized.");
    }

    public void preDestroy() {
        if (propertyTypeLoadTask != null) {
            schedulerService.cancelTask(propertyTypeLoadTask.getItemId());
        }
        if (purgeTask != null) {
            schedulerService.cancelTask(purgeTask.getItemId());
        }
        bundleContext.removeBundleListener(this);
        LOGGER.info("Profile service shutdown.");
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

    /**
     * Fill purgeEventExistTime and purgeSessionExistTime with the old property purgeSessionsAndEventsTime
     * if there is no value set for these properties. This is done to allow the using of the old property.
     * This method should be removed once the purgeSessionsAndEventsTime property is deleted.
     */
    private void initializeDefaultPurgeValuesIfNecessary() {
        if (purgeSessionsAndEventsTime > 0) {
            if (purgeEventExistTime <= 0) {
                purgeEventExistTime = purgeSessionsAndEventsTime * 30;
            }
            if (purgeSessionExistTime <= 0) {
                purgeSessionExistTime = purgeSessionsAndEventsTime * 30;
            }
        }
    }

    public void setPurgeProfileExistTime(Integer purgeProfileExistTime) {
        this.purgeProfileExistTime = purgeProfileExistTime;
    }

    public void setPurgeProfileInactiveTime(Integer purgeProfileInactiveTime) {
        this.purgeProfileInactiveTime = purgeProfileInactiveTime;
    }

    @Deprecated
    public void setPurgeSessionsAndEventsTime(Integer purgeSessionsAndEventsTime) {
        this.purgeSessionsAndEventsTime = purgeSessionsAndEventsTime;
    }

    public void setPurgeProfileInterval(Integer purgeProfileInterval) {
        this.purgeProfileInterval = purgeProfileInterval;
    }

    public void setPurgeSessionExistTime(Integer purgeSessionExistTime) {
        this.purgeSessionExistTime = purgeSessionExistTime;
    }

    public void setPurgeEventExistTime(Integer purgeEventExistTime) {
        this.purgeEventExistTime = purgeEventExistTime;
    }

    private void schedulePropertyTypeLoad() {
        propertyTypeLoadTask = schedulerService.newTask("property-type-load")
            .nonPersistent()  // Cache-like refresh, should not be persisted
            .withPeriod(propertiesRefreshInterval, TimeUnit.MILLISECONDS)
            .withFixedDelay() // Sequential execution
            .withSimpleExecutor(() -> contextManager.executeAsSystem(() -> reloadPropertyTypes(true)))
            .schedule();
    }

    public void reloadPropertyTypes(boolean refresh) {
        try {
            if (refresh) {
                persistenceService.refreshIndex(PropertyType.class);
            }
            loadPropertyTypesFromPersistence();
        } catch (Throwable t) {
            LOGGER.error("Error loading property types from persistence back-end", t);
        }
    }

    private void loadPropertyTypesFromPersistence() {
        try {
            this.propertyTypes = new PropertyTypes(persistenceService.getAllItems(PropertyType.class, 0, -1, "rank").getList());
        } catch (Exception e) {
            LOGGER.error("Error loading property types from persistence service", e);
        }
    }

    @Override
    public void purgeProfiles(int inactiveNumberOfDays, int existsNumberOfDays) {
        if (inactiveNumberOfDays > 0 || existsNumberOfDays > 0) {
            ConditionType profilePropertyConditionType = definitionsService.getConditionType("profilePropertyCondition");
            ConditionType booleanCondition = definitionsService.getConditionType("booleanCondition");
            if (profilePropertyConditionType == null || booleanCondition == null) {
                // definition service not yet fully instantiate
                return;
            }

            Condition purgeProfileQuery = new Condition(booleanCondition);
            purgeProfileQuery.setParameter("operator", "or");
            List<Condition> subConditions = new ArrayList<>();

            if (inactiveNumberOfDays > 0) {
                LOGGER.info("Purging: Profile with no visits since {} days", inactiveNumberOfDays);
                Condition inactiveTimeCondition = new Condition(profilePropertyConditionType);
                inactiveTimeCondition.setParameter("propertyName", "properties.lastVisit");
                inactiveTimeCondition.setParameter("comparisonOperator", "lessThanOrEqualTo");
                inactiveTimeCondition.setParameter("propertyValueDateExpr", "now-" + inactiveNumberOfDays + "d");
                subConditions.add(inactiveTimeCondition);
            }

            if (existsNumberOfDays > 0) {
                Condition existTimeCondition = new Condition(profilePropertyConditionType);
                LOGGER.info("Purging: Profile created since more than {} days", existsNumberOfDays);
                existTimeCondition.setParameter("propertyName", "properties.firstVisit");
                existTimeCondition.setParameter("comparisonOperator", "lessThanOrEqualTo");
                existTimeCondition.setParameter("propertyValueDateExpr", "now-" + existsNumberOfDays + "d");
                subConditions.add(existTimeCondition);
            }

            purgeProfileQuery.setParameter("subConditions", subConditions);
            persistenceService.removeByQuery(purgeProfileQuery, Profile.class);
        }
    }

    @Override
    public void purgeSessionItems(int existsNumberOfDays) {
        if (existsNumberOfDays > 0) {
            LOGGER.info("Purging: Sessions created since more than {} days", existsNumberOfDays);
            persistenceService.purgeTimeBasedItems(existsNumberOfDays, Session.class);
        }
    }

    @Override
    public void purgeEventItems(int existsNumberOfDays) {
        if (existsNumberOfDays > 0) {
            LOGGER.info("Purging: Events created since more than {} days", existsNumberOfDays);
            persistenceService.purgeTimeBasedItems(existsNumberOfDays, Event.class);
        }
    }

    @Deprecated
    @Override
    public void purgeMonthlyItems(int existsNumberOfMonths) {

    }

    private void initializePurge() {
        if (purgeProfileExistTime <= 0 && purgeProfileInactiveTime <= 0 && purgeSessionExistTime <= 0 && purgeEventExistTime <= 0) {
            return;
        }

        purgeTask = schedulerService.newTask("profile-purge")
            .withPeriod(purgeProfileInterval, TimeUnit.DAYS)
            .withFixedRate()  // Run at fixed intervals
            // By default tasks run on a single node, no need to explicitly set it
            .withSimpleExecutor(() -> contextManager.executeAsSystem(() -> {
                purgeProfiles(purgeProfileInactiveTime, purgeProfileExistTime);
                if (purgeSessionExistTime > 0) {
                    purgeSessionItems(purgeSessionExistTime);
                }
                if (purgeEventExistTime > 0) {
                    purgeEventItems(purgeEventExistTime);
                }
            }))
            .schedule();
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
        if (query.getScrollIdentifier() != null) {
            return persistenceService.continueScrollQuery(clazz, query.getScrollIdentifier(), query.getScrollTimeValidity());
        }
        if (query.getCondition() != null && definitionsService.resolveConditionType(query.getCondition())) {
            if (StringUtils.isNotBlank(query.getText())) {
                return persistenceService.queryFullText(query.getText(), query.getCondition(), query.getSortby(), clazz, query.getOffset(), query.getLimit());
            } else {
                return persistenceService.query(query.getCondition(), query.getSortby(), clazz, query.getOffset(), query.getLimit(), query.getScrollTimeValidity());
            }
        } else {
            if (StringUtils.isNotBlank(query.getText())) {
                return persistenceService.queryFullText(query.getText(), query.getSortby(), clazz, query.getOffset(), query.getLimit());
            } else {
                return persistenceService.getAllItems(clazz, query.getOffset(), query.getLimit(), query.getSortby(), query.getScrollTimeValidity());
            }
        }
    }

    @Override
    public boolean setPropertyType(PropertyType property) {
        PropertyType previousProperty = persistenceService.load(property.getItemId(), PropertyType.class);
        boolean result = false;
        if (previousProperty == null) {
            persistenceService.setPropertyMapping(property, Profile.ITEM_TYPE);
            property.setTenantId(contextManager.getCurrentContext().getTenantId());
            result = persistenceService.save(property);
            propertyTypes = propertyTypes.with(property);
        } else if (merge(previousProperty, property)) {
            persistenceService.setPropertyMapping(previousProperty, Profile.ITEM_TYPE);
            previousProperty.setTenantId(contextManager.getCurrentContext().getTenantId());
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
        ProfileAlias profileAlias = persistenceService.load(profileId, ProfileAlias.class);
        if (profileAlias != null) {
            profileId = profileAlias.getProfileID();
        }
        return persistenceService.load(profileId, Profile.class);
    }

    public Profile save(Profile profile) {
        return save(profile, forceRefreshOnSave);
    }

    @Override
    public void addAliasToProfile(String profileID, String alias, String clientID) {
        if (Objects.equals(alias, profileID)) {
            throw new IllegalArgumentException("Alias cannot be created on itself, please use an alias different from the profile ID");
        }

        ProfileAlias profileAlias = persistenceService.load(alias, ProfileAlias.class);
        if (profileAlias != null && !Objects.equals(profileAlias.getProfileID(), profileID)) {
            throw new IllegalArgumentException("Alias \"" + alias + "\" already used by profile with ID = \"" + profileAlias.getProfileID() + "\"");
        }

        if (profileAlias == null) {
            profileAlias = new ProfileAlias();

            profileAlias.setItemId(alias);
            profileAlias.setItemType(ProfileAlias.ITEM_TYPE);
            profileAlias.setProfileID(profileID);
            profileAlias.setClientID(clientID);

            Date creationTime = new Date();
            profileAlias.setCreationTime(creationTime);
            profileAlias.setModifiedTime(creationTime);

            persistenceService.save(profileAlias);
        }
    }

    @Override
    public ProfileAlias removeAliasFromProfile(String profileID, String alias, String clientID) {
        Condition profileIDCondition = new Condition(definitionsService.getConditionType("profileAliasesPropertyCondition"));
        profileIDCondition.setParameter("propertyName", "profileID.keyword");
        profileIDCondition.setParameter("comparisonOperator", "equals");
        profileIDCondition.setParameter("propertyValue", profileID);

        Condition clientIDCondition = new Condition(definitionsService.getConditionType("profileAliasesPropertyCondition"));
        clientIDCondition.setParameter("propertyName", "clientID.keyword");
        clientIDCondition.setParameter("comparisonOperator", "equals");
        clientIDCondition.setParameter("propertyValue", clientID);

        Condition aliasCondition = new Condition(definitionsService.getConditionType("profileAliasesPropertyCondition"));
        aliasCondition.setParameter("propertyName", "itemId");
        aliasCondition.setParameter("comparisonOperator", "equals");
        aliasCondition.setParameter("propertyValue", alias);

        List<Condition> conditions = new ArrayList<>();
        conditions.add(profileIDCondition);
        conditions.add(clientIDCondition);
        conditions.add(aliasCondition);

        Condition condition = new Condition(definitionsService.getConditionType("booleanCondition"));
        condition.setParameter("operator", "and");
        condition.setParameter("subConditions", conditions);

        List<ProfileAlias> profileAliases = persistenceService.query(condition, null, ProfileAlias.class);

        if (profileAliases.size() == 1 && persistenceService.removeByQuery(condition, ProfileAlias.class)) {
            return profileAliases.get(0);
        }

        return null;
    }

    @Override
    public PartialList<ProfileAlias> findProfileAliases(String profileId, int offset, int size, String sortBy) {
        Condition condition = new Condition(definitionsService.getConditionType("profileAliasesPropertyCondition"));
        condition.setParameter("propertyName", "profileID.keyword");
        condition.setParameter("comparisonOperator", "equals");
        condition.setParameter("propertyValue", profileId);

        return persistenceService.query(condition, sortBy, ProfileAlias.class, offset, size);
    }

    private Profile save(Profile profile, boolean forceRefresh) {
        if (profile.getItemId() == null) {
            return null;
        }
        profile.setSystemProperty("lastUpdated", new Date());
        if (persistenceService.save(profile)) {
            if (forceRefresh) {
                persistenceService.refreshIndex(Profile.class, null);
            }
            return profile;
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
            Condition removeAliasesCondition = new Condition(definitionsService.getConditionType("profileAliasesPropertyCondition"));
            removeAliasesCondition.setParameter("propertyName", "profileID");
            removeAliasesCondition.setParameter("comparisonOperator", "equals");
            removeAliasesCondition.setParameter("propertyValue", profileId);
            persistenceService.removeByQuery(removeAliasesCondition, ProfileAlias.class);

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
            final Set<String> flatNestedPropertiesKeys = PropertyHelper.flatten(profile.getProperties()).keySet();
            allProfileProperties.addAll(flatNestedPropertiesKeys);
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
        LOGGER.info("Merging profiles {} into profile {}", profileIdsToMerge, masterProfile.getItemId());

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
                    LOGGER.warn("Couldn't resolve default strategy, ignoring property merge for property {}", profileProperty);
                    continue;
                } else {
                    // todo: improper algorithm… it is possible that the defaultMergeStrategy couldn't be resolved here
                    LOGGER.warn("Couldn't resolve strategy {} for property {}, using default strategy instead", propertyMergeStrategyId,
                            profileProperty);
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
                LOGGER.error("Error retrieving strategy implementation", e);
            }

        }

        // merge System properties
        for (Profile profile : profilesToMerge) {
            masterProfileChanged = mergeSystemProperties(masterProfile.getSystemProperties(), profile.getSystemProperties()) || masterProfileChanged;
        }

        // we now have to merge the profile's segments
        for (Profile profile : profilesToMerge) {
            if (profile.getSegments() != null && !profile.getSegments().isEmpty()) {
                masterProfile.getSegments().addAll(profile.getSegments());
                // TODO better segments diff calculation
                masterProfileChanged = true;
            }
        }

        // we now have to merge the profile's consents
        for (Profile profile : profilesToMerge) {
            if (profile.getConsents() != null && !profile.getConsents().isEmpty()) {
                for (String consentId : profile.getConsents().keySet()) {
                    if (masterProfile.getConsents().containsKey(consentId)) {
                        if (masterProfile.getConsents().get(consentId).getRevokeDate().before(new Date())) {
                            masterProfile.getConsents().remove(consentId);
                            masterProfileChanged = true;
                        } else if (masterProfile.getConsents().get(consentId).getStatusDate().before(profile.getConsents().get(consentId).getStatusDate())) {
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
        if (!types.isEmpty()) {
            return types.iterator().next().getMetadata().getId();
        }
        return null;
    }

    @Override
    public Session loadSession(String sessionId, Date dateHint) {
        return loadSession(sessionId);
    }

    @Override
    public Session loadSession(String sessionId) {
        return persistenceService.load(sessionId, Session.class);
    }

    public Session saveSession(Session session) {
        if (session.getItemId() == null) {
            return null;
        }
        if (session.getProfile() != null) {
            if (session.getProfile().getProperties() != null){
                session.getProfile().setProperties(removePersonalIdentifiersFromSessionProfile(session.getProfile().getProperties()));
            }
            if (session.getProfile().getSystemProperties() != null){
                session.getProfile().getSystemProperties().entrySet().removeIf(entry -> entry.getKey().equals("pastEvents"));
            }
        }
        return persistenceService.save(session) ? session : null;
    }

    private Map<String, Object> removePersonalIdentifiersFromSessionProfile(final Map<String, Object> profileProperties) {
        Set<PropertyType> personalIdsProps = getPropertyTypeBySystemTag(PERSONAL_IDENTIFIER_TAG_NAME);
        final List<String> personalIdsPropsNames = new ArrayList<>();
        personalIdsProps.forEach(propType -> personalIdsPropsNames.add(propType.getMetadata().getId()));
        Set<String> propsToRemove = new HashSet<>();
        profileProperties.keySet().forEach(propKey -> {
            if (personalIdsPropsNames.contains(propKey)) {
                propsToRemove.add(propKey);
            }
        });
        propsToRemove.forEach(profileProperties::remove);
        return profileProperties;
    }

    public PartialList<Session> findProfileSessions(String profileId) {
        return persistenceService.query("profileId", profileId, "timeStamp:desc", Session.class, 0, 50);
    }

    public void removeProfileSessions(String profileId) {
        Condition profileCondition = new Condition();
        profileCondition.setConditionType(definitionsService.getConditionType("sessionPropertyCondition"));
        profileCondition.setParameter("propertyName", "profileId");
        profileCondition.setParameter("comparisonOperator", "equals");
        profileCondition.setParameter("propertyValue", profileId);

        persistenceService.removeByQuery(profileCondition, Session.class);
    }

    @Override
    public boolean matchCondition(Condition condition, Profile profile, Session session) {
        ParserHelper.resolveConditionType(definitionsService, condition, "profile " + profile.getItemId() + " matching");

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
            return !subConditions.isEmpty() && isAnd;
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
        LOGGER.info("Starting batch profiles update");
        long startTime = System.currentTimeMillis();
        long updatedCount = 0;

        ParserHelper.resolveConditionType(definitionsService, update.getCondition(), "batch update on property " + update.getPropertyName());
        PartialList<Profile> profiles = persistenceService.query(update.getCondition(), null, Profile.class, 0,update.getScrollBatchSize(), update.getScrollTimeValidity());

        while (profiles != null && !profiles.getList().isEmpty()) {
            for (Profile profile : profiles.getList()) {
                if (PropertyHelper.setProperty(profile, update.getPropertyName(), update.getPropertyValue(), update.getStrategy())) {
                    save(profile);
                    updatedCount += 1;
                }
            }
            profiles = persistenceService.continueScrollQuery(Profile.class, profiles.getScrollIdentifier(), profiles.getScrollTimeValidity());
        }

        long totalTime = System.currentTimeMillis() - startTime;
        LOGGER.info("Batch profiles updated: {} profiles in {}ms", updatedCount, totalTime);
    }

    public Persona loadPersona(String personaId) {
        if (personaId == null) {
            return null;
        }

        // Try current tenant first
        Persona result = persistenceService.load(personaId, Persona.class);
        if (result != null) {
            return result;
        }

        // If not found and not in system tenant, try system tenant
        return contextManager.executeAsSystem(() -> {
            return persistenceService.load(personaId, Persona.class);
        });
    }

    public PersonaWithSessions loadPersonaWithSessions(String personaId) {
        Persona persona = loadPersona(personaId);
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


    @Override
    public Collection<PropertyType> getTargetPropertyTypes(String target) {
        if (target == null) {
            return null;
        }

        // Get system tenant results first
        Collection<PropertyType> systemResult = contextManager.executeAsSystem(() -> {
            return persistenceService.getAllItems(PropertyType.class).stream()
                    .filter(p -> target.equals(p.getTarget()))
                    .collect(Collectors.toList());
        });

        // Get current tenant results
        Collection<PropertyType> tenantResult = persistenceService.getAllItems(PropertyType.class).stream()
                .filter(p -> target.equals(p.getTarget()))
                .collect(Collectors.toList());

        // Merge results with tenant overriding system
        Map<String, PropertyType> mergedMap = new LinkedHashMap<>();
        if (systemResult != null) {
            for (PropertyType prop : systemResult) {
                mergedMap.put(prop.getItemId(), prop);
            }
        }
        if (tenantResult != null) {
            for (PropertyType prop : tenantResult) {
                mergedMap.put(prop.getItemId(), prop);
            }
        }

        return mergedMap.isEmpty() ? new ArrayList<>() : new ArrayList<>(mergedMap.values());
    }

    public Map<String, Collection<PropertyType>> getTargetPropertyTypes() {
        return new HashMap<>(propertyTypes.getAllByTarget());
    }

    @Override
    public Set<PropertyType> getPropertyTypeByTag(String tag) {
        if (tag == null) {
            return null;
        }

        // Get system tenant results first
        Set<PropertyType> systemResult = contextManager.executeAsSystem(() -> {
            return persistenceService.getAllItems(PropertyType.class).stream()
                    .filter(p -> p.getMetadata().getTags().contains(tag))
                    .collect(Collectors.toSet());
        });

        // Get current tenant results
        Set<PropertyType> tenantResult = persistenceService.getAllItems(PropertyType.class).stream()
                .filter(p -> p.getMetadata().getTags().contains(tag))
                .collect(Collectors.toSet());

        // Merge results with tenant overriding system
        Map<String, PropertyType> mergedMap = new LinkedHashMap<>();
        if (systemResult != null) {
            for (PropertyType prop : systemResult) {
                mergedMap.put(prop.getItemId(), prop);
            }
        }
        if (tenantResult != null) {
            for (PropertyType prop : tenantResult) {
                mergedMap.put(prop.getItemId(), prop);
            }
        }

        return mergedMap.isEmpty() ? new LinkedHashSet<>() : new LinkedHashSet<>(mergedMap.values());
    }

    @Override
    public Set<PropertyType> getPropertyTypeBySystemTag(String tag) {
        if (tag == null) {
            return null;
        }

        // Get system tenant results first
        Set<PropertyType> systemResult = contextManager.executeAsSystem(() -> {
            return persistenceService.getAllItems(PropertyType.class).stream()
                    .filter(p -> p.getMetadata().getSystemTags().contains(tag))
                    .collect(Collectors.toSet());
        });

        // Get current tenant results
        Set<PropertyType> tenantResult = persistenceService.getAllItems(PropertyType.class).stream()
                .filter(p -> p.getMetadata().getSystemTags().contains(tag))
                .collect(Collectors.toSet());

        // Merge results with tenant overriding system
        Map<String, PropertyType> mergedMap = new LinkedHashMap<>();
        if (systemResult != null) {
            for (PropertyType prop : systemResult) {
                mergedMap.put(prop.getItemId(), prop);
            }
        }
        if (tenantResult != null) {
            for (PropertyType prop : tenantResult) {
                mergedMap.put(prop.getItemId(), prop);
            }
        }

        return mergedMap.isEmpty() ? new LinkedHashSet<>() : new LinkedHashSet<>(mergedMap.values());
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

    @Override
    public PropertyType getPropertyType(String id) {
        if (id == null) {
            return null;
        }

        // Try current tenant first
        PropertyType result = persistenceService.load(id, PropertyType.class);
        if (result != null) {
            return result;
        }

        // If not found and not in system tenant, try system tenant
        contextManager.executeAsSystem(() -> {
            return persistenceService.load(id, PropertyType.class);
        });

        return null;
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
            LOGGER.debug("Found predefined persona at {}, loading... ", predefinedPersonaURL);

            try {
                PersonaWithSessions persona = getObjectMapper().readValue(predefinedPersonaURL, PersonaWithSessions.class);

                String itemId = persona.getPersona().getItemId();
                persistenceService.save(persona.getPersona());

                List<PersonaSession> sessions = persona.getSessions();
                for (PersonaSession session : sessions) {
                    session.setProfile(persona.getPersona());
                    persistenceService.save(session);
                }
                LOGGER.info("Predefined persona with id {} registered", itemId);
            } catch (IOException e) {
                LOGGER.error("Error while loading persona {}", predefinedPersonaURL, e);
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
            LOGGER.debug("Found predefined property type at {}, loading... ", predefinedPropertyTypeURL);

            try {
                PropertyType propertyType = CustomObjectMapper.getObjectMapper().readValue(predefinedPropertyTypeURL, PropertyType.class);

                setPropertyTypeTarget(predefinedPropertyTypeURL, propertyType);

                persistenceService.save(propertyType);
                bundlePropertyTypes.add(propertyType);
                LOGGER.info("Predefined property type with id {} registered", propertyType.getMetadata().getId());
            } catch (IOException e) {
                LOGGER.error("Error while loading properties {}", predefinedPropertyTypeURL, e);
            }
        }
        propertyTypes = propertyTypes.with(bundlePropertyTypes);
    }


    public void bundleChanged(BundleEvent event) {
        contextManager.executeAsSystem(() -> {
            switch (event.getType()) {
                case BundleEvent.STARTED:
                    processBundleStartup(event.getBundle().getBundleContext());
                    break;
                case BundleEvent.STOPPING:
                    processBundleStop(event.getBundle().getBundleContext());
                    break;
            }
        });
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
                LOGGER.error("Cannot merge properties", e);
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
                        if (ControlGroupPersonalizationStrategy.PERSONALIZATION_STRATEGY_STATUS.equals(sourceProperty.getKey())) {
                            // Special handling for personalization strategy statuses
                            // TODO UNOMI-719: move this in a dedicated extension point to handle this kind of merge strategy in a more generic way
                            List<Map<String, Object>> sourceStatuses = (List<Map<String, Object>>) sourceProperty.getValue();
                            List<Map<String, Object>> targetStatuses = (List<Map<String, Object>>) targetProperty;

                            for (Map<String, Object> sourceStatus : sourceStatuses) {
                                if (targetStatuses
                                        .stream()
                                        .noneMatch(targetStatus -> targetStatus.get(ControlGroupPersonalizationStrategy.PERSONALIZATION_STRATEGY_STATUS_ID)
                                                .equals(sourceStatus.get(ControlGroupPersonalizationStrategy.PERSONALIZATION_STRATEGY_STATUS_ID)))) {
                                    // there is no existing status for the status ID, we can safely add it to master
                                    targetStatuses.add(sourceStatus);
                                    changed = true;
                                }
                            }
                        } else {
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
        }

        return changed;
    }

    public void refresh() {
        reloadPropertyTypes(true);
    }

    @Override
    public void deleteSession(String sessionIdentifier) {
        persistenceService.remove(sessionIdentifier, Session.class);
    }

}
