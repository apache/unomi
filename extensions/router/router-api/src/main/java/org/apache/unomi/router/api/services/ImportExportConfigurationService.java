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
package org.apache.unomi.router.api.services;

import org.apache.unomi.router.api.ExportConfiguration;
import org.apache.unomi.router.api.ImportConfiguration;
import org.apache.unomi.router.api.RouterConstants;

import java.util.List;
import java.util.Map;

/**
 * Service interface for managing import and export configurations in Apache Unomi.
 * This service provides CRUD operations for {@link ImportConfiguration}s and {@link ExportConfiguration}s,
 * as well as functionality to manage the lifecycle of data transfer configurations.
 *
 * <p>Key responsibilities:
 * <ul>
 *   <li>Managing the lifecycle of import/export configurations</li>
 *   <li>Providing CRUD operations for configurations</li>
 *   <li>Coordinating with Camel routes for configuration updates</li>
 *   <li>Tracking configuration changes that need route updates</li>
 * </ul>
 * </p>
 *
 * <p>Usage in Unomi:
 * <ul>
 *   <li>Used by REST endpoints to manage import/export configurations</li>
 *   <li>Consumed by Camel routes to get configuration updates</li>
 *   <li>Utilized by admin interfaces for configuration management</li>
 * </ul>
 * </p>
 *
 * <p>Implementation considerations:
 * <ul>
 *   <li>Implementations should handle configuration persistence</li>
 *   <li>Thread safety should be considered for concurrent operations</li>
 *   <li>Configuration changes should be properly propagated to running routes</li>
 * </ul>
 * </p>
 *
 * @param <T> The type of configuration (ImportConfiguration or ExportConfiguration)
 * @see ImportConfiguration
 * @see ExportConfiguration
 * @see RouterConstants.CONFIG_CAMEL_REFRESH
 * @since 1.0
 */
public interface ImportExportConfigurationService<T> {

    /**
     * Retrieves all the import/export configurations.
     *
     * @return the list of import/export configurations
     */
    List<T> getAll();

    /**
     * Retrieves the import/export configuration identified by the specified identifier.
     *
     * @param configId the identifier of the profile to retrieve
     * @return the import/export configuration identified by the specified identifier or
     * {@code null} if no such import/export configuration exists
     */
    T load(String configId);

    /**
     * Saves the specified import/export configuration in the context server.
     *
     * @param configuration the import/export configuration to be saved
     * @param updateRunningRoute set to true if running routes should be updated too
     * @return the newly saved import/export configuration
     */
    T save(T configuration, boolean updateRunningRoute);

    /**
     * Deletes the import/export configuration identified by the specified identifier.
     *
     * @param configId the identifier of the import/export configuration to delete
     */
    void delete(String configId);

    /**
     * Used by camel route system to get the latest changes on configs and reflect changes on camel routes if necessary
     * @return map of tenantId to map of configId per operation to be done in camel
     */
    Map<String, Map<String, RouterConstants.CONFIG_CAMEL_REFRESH>> consumeConfigsToBeRefresh();
}
