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

import org.apache.unomi.router.api.ImportConfiguration;

import java.util.List;

/**
 * A service to access and operate on {@link ImportConfiguration}s.
 */
public interface ImportConfigurationService {

    /**
     * Retrieves all the import configurations.
     *
     * @return the list of import configurations
     */
    public List<ImportConfiguration> getImportConfigurations();

    /**
     * Retrieves the import configuration identified by the specified identifier.
     *
     * @param configId the identifier of the profile to retrieve
     * @return the import configuration identified by the specified identifier or
     *  {@code null} if no such import configuration exists
     */
    public ImportConfiguration load(String configId);

    /**
     * Saves the specified import configuration in the context server.
     *
     * @param profile the import configuration to be saved
     * @return the newly saved import configuration
     */
    public ImportConfiguration save(ImportConfiguration profile);

    /**
     * Deletes the import configuration identified by the specified identifier.
     *
     * @param configId the identifier of the import configuration to delete
     */
    public void delete(String configId);


}
