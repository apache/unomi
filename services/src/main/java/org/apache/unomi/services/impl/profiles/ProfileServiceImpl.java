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
import org.apache.unomi.api.tasks.TaskExecutor;
import org.apache.unomi.api.utils.ParserHelper;
import org.apache.unomi.persistence.spi.PropertyHelper;
import org.apache.unomi.services.common.cache.AbstractMultiTypeCachingService;
import org.apache.unomi.services.sorts.ControlGroupPersonalizationStrategy;
import org.apache.unomi.api.services.cache.CacheableTypeConfig;
import org.osgi.framework.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.apache.unomi.persistence.spi.CustomObjectMapper.getObjectMapper;

public class ProfileServiceImpl extends AbstractMultiTypeCachingService implements ProfileService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProfileServiceImpl.class.getName());

    private DefinitionsService definitionsService;

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
    private ScheduledTask purgeTask;
    private long propertiesRefreshInterval = 10000;

    private boolean forceRefreshOnSave = false;

    public ProfileServiceImpl() {
        super();
    }

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
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
        super.postConstruct();
        LOGGER.debug("postConstruct {{}}", bundleContext.getBundle());

        contextManager.executeAsSystem(() -> {
            processBundleStartup(bundleContext);
            for (Bundle bundle : bundleContext.getBundles()) {
                if (bundle.getBundleContext() != null && bundle.getBundleId() != bundleContext.getBundle().getBundleId()) {
                    processBundleStartup(bundle.getBundleContext());
                }
            }
            bundleContext.addBundleListener(this);
            initializeDefaultPurgeValuesIfNecessary();
            initializePurge();
        });
        LOGGER.info("Profile service initialized.");
    }

    public void preDestroy() {
        super.preDestroy();
        if (purgeTask != null) {
            schedulerService.cancelTask(purgeTask.getItemId());
        }
        bundleContext.removeBundleListener(this);
        LOGGER.info("Profile service shutdown.");
    }

    protected void processBundleStartup(BundleContext bundleContext) {
        super.processBundleStartup(bundleContext);
        if (bundleContext == null) {
            return;
        }
        loadPredefinedPersonas(bundleContext);
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

        // Register the task executor for profile purge
        TaskExecutor profilePurgeExecutor = new TaskExecutor() {
            @Override
            public String getTaskType() {
                return "profile-purge";
            }

            @Override
            public void execute(ScheduledTask task, TaskExecutor.TaskStatusCallback callback) {
                contextManager.executeAsSystem(() -> {
                    try {
                        purgeProfiles(purgeProfileInactiveTime, purgeProfileExistTime);
                        if (purgeSessionExistTime > 0) {
                            purgeSessionItems(purgeSessionExistTime);
                        }
                        if (purgeEventExistTime > 0) {
                            purgeEventItems(purgeEventExistTime);
                        }
                        callback.complete();
                    } catch (Throwable t) {
                        LOGGER.error("Error while purging profiles, sessions, or events", t);
                        callback.fail(t.getMessage());
                    }
                });
            }
        };

        schedulerService.registerTaskExecutor(profilePurgeExecutor);

        // Check if a purge task already exists
        List<ScheduledTask> existingTasks = schedulerService.getTasksByType("profile-purge", 0, 1, null).getList();
        if (!existingTasks.isEmpty() && existingTasks.get(0).isSystemTask()) {
            // Reuse the existing task if it's a system task
            purgeTask = existingTasks.get(0);
            // Update task configuration if needed
            purgeTask.setPeriod(purgeProfileInterval);
            purgeTask.setTimeUnit(TimeUnit.DAYS);
            purgeTask.setFixedRate(true);
            purgeTask.setEnabled(true);
            schedulerService.saveTask(purgeTask);
            LOGGER.info("Reusing existing system purge task: {}", purgeTask.getItemId());
        } else {
            // Create a new task if none exists or existing one isn't a system task
            purgeTask = schedulerService.newTask("profile-purge")
                .withPeriod(purgeProfileInterval, TimeUnit.DAYS)
                .withFixedRate()  // Run at fixed intervals
                // By default tasks run on a single node, no need to explicitly set it
                .asSystemTask() // Mark as a system task
                .schedule();
            LOGGER.info("Created new system purge task: {}", purgeTask.getItemId());
        }
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
            saveItem(property, PropertyType::getItemId, PropertyType.ITEM_TYPE);
            result = true;
        } else if (merge(previousProperty, property)) {
            persistenceService.setPropertyMapping(previousProperty, Profile.ITEM_TYPE);
            previousProperty.setTenantId(contextManager.getCurrentContext().getTenantId());
            saveItem(previousProperty, PropertyType::getItemId, PropertyType.ITEM_TYPE);
            result = true;
        }

        return result;
    }

    @Override
    public boolean deletePropertyType(String propertyId) {
        removeItem(propertyId, PropertyType.class, PropertyType.ITEM_TYPE);
        return true;
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
        return getTargetPropertyTypes().get(target);
    }

    @Override
    public Map<String, Collection<PropertyType>> getTargetPropertyTypes() {
        List<PropertyType> allPropertyTypes = new ArrayList<>(getAllItems(PropertyType.class, true));

        // Separate PropertyTypes with null targets from those with non-null targets
        List<PropertyType> nullTargetProperties = allPropertyTypes.stream()
                .filter(propertyType -> propertyType.getTarget() == null)
                .collect(Collectors.toList());

        // Group PropertyTypes with non-null targets
        Map<String, List<PropertyType>> groupedMap = allPropertyTypes.stream()
                .filter(propertyType -> propertyType.getTarget() != null)
                .collect(Collectors.groupingBy(PropertyType::getTarget));

        // Convert from Map<String, List<PropertyType>> to Map<String, Collection<PropertyType>>
        Map<String, Collection<PropertyType>> result = new HashMap<>();
        groupedMap.forEach((key, value) -> result.put(key, value));

        // Add PropertyTypes with null targets under the "undefined" key
        if (!nullTargetProperties.isEmpty()) {
            result.put("undefined", nullTargetProperties);
        }

        return result;
    }

    @Override
    public Set<PropertyType> getPropertyTypeByTag(String tag) {
        if (tag == null) {
            return null;
        }
        return getItemsByTag(PropertyType.class, tag);
    }

    @Override
    public Set<PropertyType> getPropertyTypeBySystemTag(String tag) {
        if (tag == null) {
            return null;
        }
        return getItemsBySystemTag(PropertyType.class, tag);
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

        for (PropertyType propertyType : getAllItems(PropertyType.class, true)) {
            if (propertyType.getAutomaticMappingsFrom() != null && propertyType.getAutomaticMappingsFrom().contains(propertyName)) {
                l.add(propertyType);
            }
        }
        return l;
    }

    @Override
    public PropertyType getPropertyType(String id) {
        return getItem(id, PropertyType.class);
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

    public void bundleChanged(BundleEvent event) {
        contextManager.executeAsSystem(() -> {
            switch (event.getType()) {
                case BundleEvent.STARTED:
                    processBundleStartup(event.getBundle().getBundleContext());
                    break;
                case BundleEvent.STOPPING:
                    // process bundle stopping event to unregister predefined items
                    processBundleStop(event.getBundle());
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


    @Override
    public void deleteSession(String sessionIdentifier) {
        persistenceService.remove(sessionIdentifier, Session.class);
    }

    @Override
    protected Set<CacheableTypeConfig<?>> getTypeConfigs() {
        Set<CacheableTypeConfig<?>> configs = new HashSet<>();

        // Property Type configuration
        configs.add(CacheableTypeConfig.<PropertyType>builder(PropertyType.class,
                        PropertyType.ITEM_TYPE,
                        "properties")
                .withInheritFromSystemTenant(true)
                .withPredefinedItems(true)
                .withRequiresRefresh(true)
                .withRefreshInterval(propertiesRefreshInterval)
                .withIdExtractor(PropertyType::getItemId)
                .withBundleItemProcessor((bundleContext, propertyType) -> {
                    setPropertyType(propertyType);
                })
                .build());

        return configs;
    }

    @Override
    public void refresh() {
        // Refresh the cache for all registered types
        for (CacheableTypeConfig<?> config : getTypeConfigs()) {
            refreshTypeCache(config);
        }
    }

}
