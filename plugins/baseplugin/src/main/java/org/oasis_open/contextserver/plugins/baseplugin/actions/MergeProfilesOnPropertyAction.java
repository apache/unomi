package org.oasis_open.contextserver.plugins.baseplugin.actions;

/*
 * #%L
 * context-server-services
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2014 - 2015 Jahia Solutions
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.apache.commons.lang3.StringUtils;
import org.oasis_open.contextserver.api.Event;
import org.oasis_open.contextserver.api.Persona;
import org.oasis_open.contextserver.api.Profile;
import org.oasis_open.contextserver.api.actions.Action;
import org.oasis_open.contextserver.api.actions.ActionExecutor;
import org.oasis_open.contextserver.api.services.EventService;
import org.oasis_open.contextserver.api.services.ProfileService;

import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

public class MergeProfilesOnPropertyAction implements ActionExecutor {

    private final int MAX_COOKIE_AGE_IN_SECONDS = 60 * 60 * 24 * 365 * 10; // 10-years
    private int cookieAgeInSeconds = MAX_COOKIE_AGE_IN_SECONDS;
    private String profileIdCookieName = "context-profile-id";

    private ProfileService profileService;

    public void setCookieAgeInSeconds(int cookieAgeInSeconds) {
        this.cookieAgeInSeconds = cookieAgeInSeconds;
    }

    public void setProfileIdCookieName(String profileIdCookieName) {
        this.profileIdCookieName = profileIdCookieName;
    }

    public void setProfileService(ProfileService profileService) {
        this.profileService = profileService;
    }

    public int execute(Action action, Event event) {
        String mergeProfilePropertyName = (String) action.getParameterValues().get("mergeProfilePropertyName");
        Profile profile = event.getProfile();

        if (profile instanceof Persona) {
            return EventService.NO_CHANGE;
        }

        Object currentMergePropertyValue = profile.getProperty(mergeProfilePropertyName);

        if (currentMergePropertyValue == null || StringUtils.isEmpty(currentMergePropertyValue.toString())) {
            return EventService.NO_CHANGE;
        }
        String profileId = profile.getItemId();
        boolean updated = profileService.mergeProfilesOnProperty(profile, event.getSession(), mergeProfilePropertyName, currentMergePropertyValue.toString());

        if (!event.getSession().getProfileId().equals(profileId)) {
            HttpServletResponse httpServletResponse = (HttpServletResponse) event.getAttributes().get(Event.HTTP_RESPONSE_ATTRIBUTE);
            sendProfileCookie(event.getSession().getProfile(), httpServletResponse);
        }

        return EventService.NO_CHANGE;
    }

    public void sendProfileCookie(Profile profile, ServletResponse response) {
        if (response instanceof HttpServletResponse) {
            HttpServletResponse httpServletResponse = (HttpServletResponse) response;
            Cookie profileIdCookie = new Cookie(profileIdCookieName, profile.getItemId());
            profileIdCookie.setPath("/");
            profileIdCookie.setMaxAge(cookieAgeInSeconds);
            httpServletResponse.addCookie(profileIdCookie);
        }
    }

}
