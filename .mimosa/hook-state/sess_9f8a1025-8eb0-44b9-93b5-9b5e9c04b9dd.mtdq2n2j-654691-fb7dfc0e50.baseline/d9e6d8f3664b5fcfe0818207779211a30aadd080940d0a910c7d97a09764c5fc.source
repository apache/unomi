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
 * Creates or updates a Salesforce lead from the corresponding Apache Unomi profile (using a common identifier field,
 * usually the email address)
 */
public class CreateOrUpdateLeadAction implements ActionExecutor {

    private SFDCService sfdcService;

    public void setSfdcService(SFDCService sfdcService) {
        this.sfdcService = sfdcService;
    }

    @Override
    public int execute(Action action, Event event) {
        sfdcService.createOrUpdateLead(event.getProfile());
        return EventService.NO_CHANGE;
    }
}
