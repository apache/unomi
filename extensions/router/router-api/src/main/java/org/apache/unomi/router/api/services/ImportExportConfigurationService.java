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
import org.apache.unomi.router.api.IRouterCamelContext;
import org.apache.unomi.router.api.ImportConfiguration;

import java.util.List;

/**
 * A service to access and operate on {@link ImportConfiguration}s / {@link ExportConfiguration}s.
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
     * @return the newly saved import/export configuration
     */
    T save(T configuration);

    /**
     * Deletes the import/export configuration identified by the specified identifier.
     *
     * @param configId the identifier of the import/export configuration to delete
     */
    void delete(String configId);

    /**
     * Set the router camel context to share
     *
     * @param routerCamelContext
     */
    void setRouterCamelContext(IRouterCamelContext routerCamelContext);


}
