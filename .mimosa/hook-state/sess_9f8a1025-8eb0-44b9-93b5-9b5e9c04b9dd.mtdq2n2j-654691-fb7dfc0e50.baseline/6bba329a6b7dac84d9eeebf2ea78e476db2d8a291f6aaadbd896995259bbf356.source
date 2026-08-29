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
package org.apache.unomi.api.tenants;

import org.apache.unomi.api.Item;

/**
 * Interface for item-specific data transformations that can be implemented by Unomi extensions.
 * Transformations can include data masking, format conversion, or other data modifications.
 * Multiple listeners can be registered and will be called in order of priority.
 */
public interface TenantTransformationListener {

    /**
     * Gets the priority of this listener. Listeners with higher priority values will be executed first.
     * @return the priority value (default is 0)
     */
    default int getPriority() {
        return 0;
    }

    /**
     * Applies forward transformation to data in an item for a specific tenant
     * @param item The item containing data to transform
     * @param tenantId The ID of the tenant
     * @return transformed item if transformation was successful, null otherwise
     */
    Item transformItem(Item item, String tenantId);

    /**
     * Checks if transformation is available and enabled
     * @return true if transformation is available and enabled
     */
    boolean isTransformationEnabled();

    /**
     * Reverses the transformation of data in an item for a specific tenant
     * @param item The item containing data to reverse transform
     * @param tenantId The ID of the tenant
     * @return transformed item if reverse transformation was successful, null otherwise
     */
    Item reverseTransformItem(Item item, String tenantId);

    /**
     * Checks if an item contains transformed data
     * @param item The item to check
     * @return true if the item contains transformed data
     */
    default boolean isItemTransformed(Item item) {
        return item != null && Boolean.TRUE.equals(item.getSystemMetadata("transformed"));
    }

    /**
     * Gets the transformation type identifier
     * @return String identifying the type of transformation
     */
    String getTransformationType();
} 