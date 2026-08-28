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
package org.apache.unomi.sfdc.actions;

import org.apache.unomi.api.Event;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionExecutor;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.sfdc.services.SFDCService;

/**
 * Update an Apache Unomi profile from a corresponding Salesforce Lead with the same common identifier (usually the
 * email address)
 */
public class UpdateProfileFromLeadAction implements ActionExecutor {

    private SFDCService sfdcService;

    public void setSfdcService(SFDCService sfdcService) {
        this.sfdcService = sfdcService;
    }

    @Override
    public int execute(Action action, Event event) {
        boolean profileUpdated = sfdcService.updateProfileFromLead(event.getProfile());
        if (profileUpdated) return EventService.PROFILE_UPDATED;
        return EventService.NO_CHANGE;
    }
}
