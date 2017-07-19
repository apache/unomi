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

package org.apache.unomi.sfdc.services;

import org.apache.http.HttpException;
import org.apache.unomi.api.Profile;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * Public interface for the Salesforce connector
 */
public interface SFDCService {

    /**
     * Load the configuration from the persistence service (if it exists)
     * @return an instance of the configuration if it was found, null otherwise
     */
    SFDCConfiguration loadConfiguration();

    /**
     * Save a Salesforce configuration into the persistence service
     * @param sfdcConfiguration the configuration to persist
     * @return
     */
    boolean saveConfiguration(SFDCConfiguration sfdcConfiguration);

    /**
     * Login into Salesforce using the configuration passed in the methods arguments.
     * @param sfdcConfiguration the configuration to use for the login
     * @return true if the login was successful, false otherwise
     * @throws HttpException
     * @throws IOException
     */
    boolean login(SFDCConfiguration sfdcConfiguration) throws HttpException, IOException;

    SFDCSession getSFDCSession();

    void logout();

    /**
     * Create or update a lead based on a Unomi profile.
     * @param profile
     * @return a String containing the identifier of the corresponding SFDC lead
     */
    String createOrUpdateLead(Profile profile);

    /**
     * Updates a Unomi profile from a Salesforce lead
     * @param profile
     * @return true if the profile was updated, false otherwise.
     */
    boolean updateProfileFromLead(Profile profile);

    Set<String> getRecentLeadIds();

    Map<String,Object> getLead(String leadId);

    Map<String,Object> query(String query);

    Set<String> findLeadIdsByIdentifierValue(String identifierFieldValue);

    Map<String,Object> getLimits();
}
