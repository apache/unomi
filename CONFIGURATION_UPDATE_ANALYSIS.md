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
//

# Configuration Update Analysis: ElasticSearch and OpenSearch Persistence

## Problem Statement

The current `updated()` method in both `ElasticSearchPersistenceServiceImpl` and `OpenSearchPersistenceServiceImpl` only updates in-memory configuration values. It does **not** update the corresponding infrastructure components (index templates, rollover policies, lifecycle policies, etc.) that were created during initialization based on these configuration values.

### Example: rollover.indices Change

When `rollover.indices` changes (e.g., from `"event,session"` to `"event,session,profile"`), the system needs to:
1. **Create Index Templates**: New rollover templates must be created for newly added item types (e.g., `profile`)
2. **Update OpenSearch ISM Policy**: The ISM policy's `ism_template.index_patterns` must be updated to include patterns for new item types
3. **Handle Removed Item Types**: Templates for removed item types may need cleanup (though existing indices will continue to work)

However, the current `updated()` method only calls `setRolloverIndices()`, which updates the in-memory value but does not:
- Create rollover templates for newly added item types
- Update the OpenSearch ISM policy template patterns
- Remove templates for item types no longer in the list (optional)

## Configuration Properties Analysis

### Infrastructure-Affecting Properties

These properties affect infrastructure components that need to be updated when changed:

#### 1. `indexPrefix`
**Used in:**
- Rollover alias: `indexPrefix + "-" + itemName`
- Index names: `indexPrefix + "-" + itemType`
- Rollover index patterns: `indexPrefix + "-" + itemType + "-*"`
- Lifecycle policy name: `indexPrefix + "-" + ROLLOVER_LIFECYCLE_NAME` (ES) or `indexPrefix + "-rollover-lifecycle-policy"` (OS)
- Index template names: `rolloverAlias + "-rollover-template"`

**Infrastructure Impact:**
- ✅ **Index Templates**: Template names and index patterns depend on `indexPrefix`
- ✅ **Rollover Lifecycle Policies**: Policy names depend on `indexPrefix`
- ✅ **Existing Indices**: Index names depend on `indexPrefix` (but existing indices cannot be renamed)
- ⚠️ **Note**: Changing `indexPrefix` effectively creates a new namespace. Existing indices won't be affected, but new operations will use the new prefix.

**Update Required:**
- Re-register rollover lifecycle policy (with new name)
- Recreate index templates for rollover indices (with new names/patterns)
- Update rollover alias references in template settings

#### 2. `rolloverIndexNumberOfShards`
**Used in:**
- Index template settings for rollover indices
- New rollover index creation

**Infrastructure Impact:**
- ✅ **Index Templates**: Settings contain `number_of_shards`
- ⚠️ **Existing Indices**: Cannot be changed on existing indices (only affects new indices created from template)

**Update Required:**
- Update index template settings for all rollover item types

#### 3. `rolloverIndexNumberOfReplicas`
**Used in:**
- Index template settings for rollover indices
- New rollover index creation

**Infrastructure Impact:**
- ✅ **Index Templates**: Settings contain `number_of_replicas`
- ⚠️ **Existing Indices**: Can be updated via index settings API (affects existing indices)

**Update Required:**
- Update index template settings for all rollover item types
- Optionally update existing rollover indices (if desired)

#### 4. `rolloverIndexMappingTotalFieldsLimit`
**Used in:**
- Index template settings for rollover indices

**Infrastructure Impact:**
- ✅ **Index Templates**: Settings contain `mapping.total_fields.limit`
- ⚠️ **Existing Indices**: Cannot be changed on existing indices (only affects new indices)

**Update Required:**
- Update index template settings for all rollover item types

#### 5. `rolloverIndexMaxDocValueFieldsSearch`
**Used in:**
- Index template settings for rollover indices

**Infrastructure Impact:**
- ✅ **Index Templates**: Settings contain `max_docvalue_fields_search`
- ⚠️ **Existing Indices**: Cannot be changed on existing indices (only affects new indices)

**Update Required:**
- Update index template settings for all rollover item types

#### 6. `rolloverMaxSize`
**Used in:**
- Rollover lifecycle policy rollover action

**Infrastructure Impact:**
- ✅ **Rollover Lifecycle Policy**: Policy contains rollover action with `maxSize`

**Update Required:**
- Re-register rollover lifecycle policy

#### 7. `rolloverMaxAge`
**Used in:**
- Rollover lifecycle policy rollover action

**Infrastructure Impact:**
- ✅ **Rollover Lifecycle Policy**: Policy contains rollover action with `maxAge`

**Update Required:**
- Re-register rollover lifecycle policy

#### 8. `rolloverMaxDocs`
**Used in:**
- Rollover lifecycle policy rollover action

**Infrastructure Impact:**
- ✅ **Rollover Lifecycle Policy**: Policy contains rollover action with `maxDocs`

**Update Required:**
- Re-register rollover lifecycle policy

#### 9. `rolloverIndices` (Configuration: `rollover.indices`)
**Used in:**
- Determines which item types use rollover (checked via `isItemTypeRollingOver(itemType)`)
- Controls whether `internalCreateRolloverTemplate()` is called during index creation
- **OpenSearch only**: ISM policy's `ism_template.index_patterns` contains patterns for each item type in the list

**Infrastructure Impact:**
- ✅ **Index Templates**: Templates are only created for item types in `rolloverIndices` list
  - When an item type is added: A rollover template must be created for that item type
  - When an item type is removed: The template may remain but won't be used for new indices
- ✅ **OpenSearch ISM Policy**: The `ism_template.index_patterns` array must match the current `rolloverIndices` list
  - Each item type in the list should have a corresponding pattern: `indexPrefix + "-" + itemType + "-*"`
- ⚠️ **Existing Indices**: Existing indices are not affected, but new indices will follow the new configuration

**Update Required:**
- **For newly added item types:**
  - Create rollover index template (call `internalCreateRolloverTemplate(itemType)`)
  - Ensure mapping exists for the item type
- **For OpenSearch:**
  - Re-register rollover lifecycle policy to update `ism_template.index_patterns`
- **For removed item types (optional):**
  - Optionally remove rollover templates (though keeping them doesn't cause issues)

**Example Scenario:**
- **Before**: `rollover.indices = "event,session"`
- **After**: `rollover.indices = "event,session,profile"`
- **Required Actions:**
  1. Create rollover template for `profile` item type
  2. Update OpenSearch ISM policy to include pattern for `profile` indices
  3. When `profile` index is first created, it will use rollover instead of regular index creation

#### 10. `numberOfShards` (non-rollover)
**Used in:**
- Non-rollover index creation

**Infrastructure Impact:**
- ⚠️ **Existing Indices**: Cannot be changed on existing indices (only affects new indices)

**Update Required:**
- None (only affects new indices created after change)

#### 11. `numberOfReplicas` (non-rollover)
**Used in:**
- Non-rollover index creation

**Infrastructure Impact:**
- ✅ **Existing Indices**: Can be updated via index settings API

**Update Required:**
- Optionally update existing non-rollover indices (if desired)

#### 12. `indexMappingTotalFieldsLimit` (non-rollover)
**Used in:**
- Non-rollover index creation

**Infrastructure Impact:**
- ⚠️ **Existing Indices**: Cannot be changed on existing indices (only affects new indices)

**Update Required:**
- None (only affects new indices created after change)

#### 13. `indexMaxDocValueFieldsSearch` (non-rollover)
**Used in:**
- Non-rollover index creation

**Infrastructure Impact:**
- ⚠️ **Existing Indices**: Cannot be changed on existing indices (only affects new indices)

**Update Required:**
- None (only affects new indices created after change)

### Non-Infrastructure Properties

These properties only affect runtime behavior and don't require infrastructure updates:
- `throwExceptions`
- `alwaysOverwrite`
- `useBatchingForSave`
- `useBatchingForUpdate`
- `aggQueryThrowOnMissingDocs`
- `logLevelRestClient`
- `clientSocketTimeout`
- `taskWaitingTimeout`
- `taskWaitingPollingInterval`
- `aggQueryMaxResponseSizeHttp`
- `aggregateQueryBucketSize`
- `itemTypeToRefreshPolicy`

## Current Initialization Flow

### ElasticSearch
1. `start()` method calls:
   - `registerRolloverLifecyclePolicy()` - Creates ILM policy with name `indexPrefix + "-" + ROLLOVER_LIFECYCLE_NAME`
   - `loadPredefinedMappings()` - For each mapping:
     - If item type is in `rolloverIndices`: calls `internalCreateRolloverTemplate()` and `internalCreateRolloverIndex()`
     - Otherwise: calls `internalCreateIndex()`

### OpenSearch
1. `start()` method calls:
   - `registerRolloverLifecyclePolicy()` - Creates ISM policy with name `indexPrefix + "-rollover-lifecycle-policy"`
   - `loadPredefinedMappings()` - For each mapping:
     - If item type is in `rolloverIndices`: calls `internalCreateRolloverTemplate()` and `internalCreateRolloverIndex()`
     - Otherwise: calls `internalCreateIndex()`

## Required Updates for Configuration Changes

### Minimal Change Solution Approach

The solution should:
1. **Track previous configuration values** to detect what changed
2. **Identify affected infrastructure** based on changed properties
3. **Update infrastructure components** only when necessary
4. **Minimize disruption** by only updating what's needed

### Update Strategy by Property

#### For `indexPrefix`:
- **Action**: Re-register lifecycle policy, recreate all rollover templates
- **Reason**: Policy and template names depend on prefix
- **Note**: Existing indices keep old prefix (cannot rename indices)

#### For `rolloverIndexNumberOfShards`, `rolloverIndexNumberOfReplicas`, `rolloverIndexMappingTotalFieldsLimit`, `rolloverIndexMaxDocValueFieldsSearch`:
- **Action**: Update index template settings for all rollover item types
- **Method**: Use `PutIndexTemplateRequest` with updated settings
- **Note**: Only affects new indices created from template

#### For `rolloverMaxSize`, `rolloverMaxAge`, `rolloverMaxDocs`:
- **Action**: Re-register rollover lifecycle policy
- **Method**: Call `registerRolloverLifecyclePolicy()` again
- **Note**: Policy is updated in-place (same name)

#### For `rolloverIndices`:
- **Action**: 
  - **Detect changes**: Compare old and new lists to find added/removed item types
  - **For newly added item types**:
    - Verify mapping exists for the item type
    - Create rollover index template (call `internalCreateRolloverTemplate(itemType)`)
  - **For OpenSearch**: Re-register lifecycle policy to update `ism_template.index_patterns`
  - **For removed item types** (optional):
    - Optionally remove rollover templates (though keeping them doesn't cause issues)
- **Method**: 
  - Parse comma-separated list and compare with previous list
  - For each new item type: `internalCreateRolloverTemplate(itemType)` (if mapping exists)
  - For OpenSearch: `registerRolloverLifecyclePolicy()` (updates ISM template patterns)
  - Optionally: `removeIndexTemplate()` for removed types
- **Note**: This is one of the most complex updates because it requires:
  - List comparison (added/removed items)
  - Access to mappings to verify item types are valid
  - Different handling for ElasticSearch vs OpenSearch (ISM policy update)

#### For `numberOfReplicas` (non-rollover):
- **Action**: Optionally update existing non-rollover indices
- **Method**: Use index settings update API
- **Note**: This is optional - only affects existing indices, not templates

## Implementation Requirements

### 1. Configuration Change Detection
- Store previous configuration values (or use OSGi Configuration Admin to detect changes)
- Compare old vs new values to determine what changed

### 2. Infrastructure Update Methods
- **Update index template**: The existing `internalCreateRolloverTemplate()` method can be reused - `putIndexTemplate`/`putTemplate` operations are idempotent and will update existing templates
- **Re-register lifecycle policy**: Already exists (`registerRolloverLifecyclePolicy()`) - policy updates are also idempotent
- **Update existing indices**: Method to update index settings (for `numberOfReplicas`) - requires separate index settings update API call

### 3. Rollover Item Type Discovery
- Need to know which item types use rollover (from `rolloverIndices`)
- Need access to mappings for those item types

### 4. Error Handling
- Handle cases where templates/policies don't exist
- Handle partial failures gracefully
- Log warnings for properties that can't be updated on existing indices

## Minimal Change Implementation Strategy

### Option 1: Extend `updated()` Method (Recommended)
1. Add property change detection
2. For each changed infrastructure-affecting property:
   - Determine affected components
   - Call appropriate update methods
3. Keep existing simple property updates for non-infrastructure properties

**Pros:**
- Minimal code changes
- Reuses existing methods
- Clear separation of concerns

**Cons:**
- `updated()` method becomes more complex
- Need to track previous values

### Option 2: Separate Update Handler
1. Create `InfrastructureUpdateHandler` class
2. `updated()` method delegates infrastructure updates to handler
3. Handler manages change detection and updates

**Pros:**
- Better separation of concerns
- Easier to test
- More maintainable

**Cons:**
- More code changes
- Additional class to maintain

### Recommended Approach: Option 1 with Helper Methods

1. **Add configuration tracking**: Store previous values in instance variables
2. **Create helper methods**:
   - `updateRolloverTemplatesForAllItemTypes()` - Updates all rollover templates
   - `updateRolloverTemplate(String itemType)` - Updates single template
   - `updateExistingRolloverIndices(String itemType, String setting, Object value)` - Updates existing indices (optional)
3. **Extend `updated()` method**:
   - Detect changes by comparing old vs new values
   - For each changed property, call appropriate update methods
   - Update stored previous values after successful updates

## Code Structure Changes Required

### ElasticSearchPersistenceServiceImpl
```java
// Add fields to track previous values
private String previousIndexPrefix;
private String previousRolloverIndexNumberOfShards;
private List<String> previousRolloverIndices;
// ... etc for all infrastructure-affecting properties

@Override
public void updated(Dictionary<String, ?> properties) {
    // Existing simple property mappings
    Map<String, ConfigurationUpdateHelper.PropertyMapping> propertyMappings = new HashMap<>();
    // ... existing mappings ...
    
    // Detect infrastructure-affecting changes
    String newIndexPrefix = (String) properties.get("indexPrefix");
    if (newIndexPrefix != null && !newIndexPrefix.equals(previousIndexPrefix)) {
        updateInfrastructureForIndexPrefixChange(newIndexPrefix);
        previousIndexPrefix = newIndexPrefix;
    }
    
    // Handle rolloverIndices change (most complex)
    String newRolloverIndicesStr = (String) properties.get("rolloverIndices");
    if (newRolloverIndicesStr != null) {
        List<String> newRolloverIndices = StringUtils.isNotEmpty(newRolloverIndicesStr) 
            ? Arrays.asList(newRolloverIndicesStr.split(",")) 
            : null;
        if (!Objects.equals(newRolloverIndices, previousRolloverIndices)) {
            updateInfrastructureForRolloverIndicesChange(newRolloverIndices);
            previousRolloverIndices = newRolloverIndices;
        }
    }
    
    // Similar for other infrastructure properties...
    
    // Process simple property updates
    ConfigurationUpdateHelper.processConfigurationUpdates(properties, LOGGER, "ElasticSearch persistence", propertyMappings);
}

private void updateInfrastructureForIndexPrefixChange(String newIndexPrefix) {
    // Re-register lifecycle policy
    registerRolloverLifecyclePolicy();
    
    // Recreate all rollover templates
    if (rolloverIndices != null) {
        for (String itemType : rolloverIndices) {
            if (mappings.containsKey(itemType)) {
                internalCreateRolloverTemplate(itemType);
            }
        }
    }
}

private void updateRolloverTemplatesForSettingsChange() {
    if (rolloverIndices != null) {
        for (String itemType : rolloverIndices) {
            if (mappings.containsKey(itemType)) {
                updateRolloverTemplate(itemType);
            }
        }
    }
}

private void updateInfrastructureForRolloverIndicesChange(List<String> newRolloverIndices) {
    // Find added and removed item types
    Set<String> previousSet = previousRolloverIndices != null 
        ? new HashSet<>(previousRolloverIndices) 
        : Collections.emptySet();
    Set<String> newSet = newRolloverIndices != null 
        ? new HashSet<>(newRolloverIndices) 
        : Collections.emptySet();
    
    // Find newly added item types
    Set<String> added = new HashSet<>(newSet);
    added.removeAll(previousSet);
    
    // Find removed item types
    Set<String> removed = new HashSet<>(previousSet);
    removed.removeAll(newSet);
    
    // Create templates for newly added item types
    for (String itemType : added) {
        if (mappings.containsKey(itemType)) {
            LOGGER.info("Creating rollover template for newly added item type: {}", itemType);
            internalCreateRolloverTemplate(itemType);
        } else {
            LOGGER.warn("Cannot create rollover template for item type {}: mapping not found", itemType);
        }
    }
    
    // Optionally remove templates for removed item types
    // (Keeping them doesn't cause issues, so this is optional)
    for (String itemType : removed) {
        String templateName = buildRolloverAlias(itemType) + "-rollover-template";
        LOGGER.info("Removing rollover template for item type no longer in rollover list: {}", itemType);
        // Note: This would require a removeIndexTemplate method or similar
    }
    
    // For OpenSearch, also need to update ISM policy template patterns
    // This is handled in OpenSearchPersistenceServiceImpl
}
```

### OpenSearchPersistenceServiceImpl
Similar structure, but with OpenSearch-specific API calls.

## Testing Considerations

1. **Unit Tests**: Test change detection logic
2. **Integration Tests**: Test infrastructure updates with real ES/OS clusters
3. **Edge Cases**:
   - Template doesn't exist
   - Policy doesn't exist
   - Multiple properties change simultaneously
   - Rollover indices list changes

## Migration Notes

- Existing deployments: First update will detect all properties as "changed" (no previous values)
- Consider adding a flag to skip infrastructure updates on first update (only update in-memory values)
- Or, always perform infrastructure updates (idempotent operations should be safe)

## Summary

The current `updated()` method is incomplete for infrastructure-affecting configuration properties. A minimal-change solution would:

1. Track previous configuration values
2. Detect changes in infrastructure-affecting properties
3. Update corresponding infrastructure components:
   - Index templates (for rollover settings)
   - Lifecycle policies (for rollover parameters)
   - Existing indices (optionally, for `numberOfReplicas`)
4. Maintain backward compatibility with existing simple property updates

The most critical properties requiring infrastructure updates are:
- `rolloverIndices` (affects which templates exist, requires list comparison and template creation/removal)
- `indexPrefix` (affects policy names, template names, aliases)
- Rollover settings (`rolloverIndexNumberOfShards`, `rolloverIndexNumberOfReplicas`, etc.)
- Rollover policy parameters (`rolloverMaxSize`, `rolloverMaxAge`, `rolloverMaxDocs`)

**Note**: The `rolloverIndices` property is particularly important because:
- It requires comparing old and new lists to detect added/removed item types
- Newly added item types need rollover templates created immediately
- OpenSearch requires ISM policy template patterns to be updated
- This is a common configuration change scenario (e.g., adding `profile` to rollover list)

