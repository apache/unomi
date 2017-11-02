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
package org.apache.unomi.plugins.baseplugin.actions;

import org.apache.unomi.api.Consent;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionExecutor;
import org.apache.unomi.api.services.EventService;

import java.util.List;

/**
 * This class will process consent modification actions and update the profile's consents accordingly.
 */
public class ModifyConsentsAction implements ActionExecutor {

    public static final String GRANTED_CONSENTS = "grantedConsents";
    public static final String DENIED_CONSENTS = "deniedConsents";
    public static final String REVOKED_CONSENTS = "revokedConsents";

    @Override
    public int execute(Action action, Event event) {

        Profile profile = event.getProfile();
        boolean isProfileUpdated = false;

        List<Consent> grantedConsents = (List<Consent>) event.getProperties().get(GRANTED_CONSENTS);
        if (grantedConsents != null) {
            for (Consent consent : grantedConsents) {
                profile.grantConsent(consent.getTypeId(), consent.getGrantDate(), consent.getRevokeDate());
            }
            isProfileUpdated = true;
        }
        List<Consent> deniedConsents = (List<Consent>) event.getProperties().get(DENIED_CONSENTS);
        if (deniedConsents != null) {
            for (Consent consent : deniedConsents) {
                profile.denyConsent(consent.getTypeId(), consent.getGrantDate(), consent.getRevokeDate());
            }
            isProfileUpdated = true;
        }
        List<Consent> revokedConsents = (List<Consent>) event.getProperties().get(REVOKED_CONSENTS);
        if (revokedConsents != null) {
            for (Consent consent : revokedConsents) {
                profile.revokeConsent(consent.getTypeId());
            }
            isProfileUpdated = true;
        }

        return isProfileUpdated ? EventService.PROFILE_UPDATED : EventService.NO_CHANGE;
    }
}
