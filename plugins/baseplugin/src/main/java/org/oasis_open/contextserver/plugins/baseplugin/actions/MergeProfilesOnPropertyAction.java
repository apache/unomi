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
import org.oasis_open.contextserver.api.Session;
import org.oasis_open.contextserver.api.actions.Action;
import org.oasis_open.contextserver.api.actions.ActionExecutor;
import org.oasis_open.contextserver.api.actions.ActionPostExecutor;
import org.oasis_open.contextserver.api.conditions.Condition;
import org.oasis_open.contextserver.api.services.DefinitionsService;
import org.oasis_open.contextserver.api.services.EventService;
import org.oasis_open.contextserver.api.services.ProfileService;
import org.oasis_open.contextserver.persistence.spi.PersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;

public class MergeProfilesOnPropertyAction implements ActionExecutor {

    private static final Logger logger = LoggerFactory.getLogger(MergeProfilesOnPropertyAction.class.getName());

    private final int MAX_COOKIE_AGE_IN_SECONDS = 60 * 60 * 24 * 365 * 10; // 10-years
    private int cookieAgeInSeconds = MAX_COOKIE_AGE_IN_SECONDS;
    private String profileIdCookieName = "context-profile-id";

    private ProfileService profileService;

    private PersistenceService persistenceService;

    private EventService eventService;

    private DefinitionsService definitionsService;

    public void setCookieAgeInSeconds(int cookieAgeInSeconds) {
        this.cookieAgeInSeconds = cookieAgeInSeconds;
    }

    public void setProfileIdCookieName(String profileIdCookieName) {
        this.profileIdCookieName = profileIdCookieName;
    }

    public void setProfileService(ProfileService profileService) {
        this.profileService = profileService;
    }

    public PersistenceService getPersistenceService() {
        return persistenceService;
    }

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public EventService getEventService() {
        return eventService;
    }

    public void setEventService(EventService eventService) {
        this.eventService = eventService;
    }

    public DefinitionsService getDefinitionsService() {
        return definitionsService;
    }

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    public int execute(Action action, Event event) {

        final Profile profile = event.getProfile();

        final String mergeProfilePropertyName = (String) action.getParameterValues().get("mergeProfilePropertyName");
        final String mergeProfilePropertyValue = profile.getProperty(mergeProfilePropertyName) != null ? profile.getProperty(mergeProfilePropertyName).toString() : "";
        final Session currentSession = event.getSession();

        // store the profile id in case the merge change it to a previous one

        if (profile instanceof Persona) {
            return EventService.NO_CHANGE;
        }

        if (StringUtils.isEmpty(mergeProfilePropertyValue)) {
            return EventService.NO_CHANGE;
        }
        String profileId = profile.getItemId();

        Condition propertyCondition = new Condition(definitionsService.getConditionType("eventPropertyCondition"));
        propertyCondition.setParameter("comparisonOperator", "equals");
        propertyCondition.setParameter("propertyName", mergeProfilePropertyName);
        propertyCondition.setParameter("propertyValue", mergeProfilePropertyValue);

        Condition excludeMergedProfilesCondition = new Condition(definitionsService.getConditionType("eventPropertyCondition"));
        excludeMergedProfilesCondition.setParameter("comparisonOperator", "missing");
        excludeMergedProfilesCondition.setParameter("propertyName", "mergedWith");

        Condition c = new Condition(definitionsService.getConditionType("booleanCondition"));
        c.setParameter("operator", "and");
        c.setParameter("subConditions", Arrays.asList(propertyCondition, excludeMergedProfilesCondition));

        final List<Profile> profiles = persistenceService.query(c, "properties.firstVisit", Profile.class);

        // add current Profile to profiles to be merged
        boolean add = true;
        for (Profile p : profiles) {
            add = add && !StringUtils.equals(p.getItemId(), profile.getItemId());
        }
        if (add) {
            profiles.add(profile);
        }

        if (profiles.size() == 1) {
            return EventService.NO_CHANGE;
        }

        Profile masterProfile = profileService.mergeProfiles(profiles.get(0), profiles);

        if (!masterProfile.getItemId().equals(profileId)) {
            HttpServletResponse httpServletResponse = (HttpServletResponse) event.getAttributes().get(Event.HTTP_RESPONSE_ATTRIBUTE);
            sendProfileCookie(event.getSession().getProfile(), httpServletResponse);
            final String masterProfileId = masterProfile.getItemId();

            // At the end of the merge, we must set the merged profile as profile event to process other Actions
            event.setProfileId(masterProfileId);
            event.setProfile(masterProfile);

            event.getActionPostExecutors().add(new ActionPostExecutor() {
                @Override
                public boolean execute() {
                    try {
                        for (Profile profile : profiles) {
                            String profileId = profile.getItemId();
                            if (!StringUtils.equals(profileId, masterProfileId)) {
                                List<Session> sessions = persistenceService.query("profileId", profileId, null, Session.class);
                                if (currentSession.getProfileId().equals(profileId) && !sessions.contains(currentSession)) {
                                    sessions.add(currentSession);
                                }
                                for (Session session : sessions) {
                                    persistenceService.update(session.getItemId(), session.getTimeStamp(), Session.class, "profileId", masterProfileId);
                                }

                                List<Event> events = persistenceService.query("profileId", profileId, null, Event.class);
                                for (Event event : events) {
                                    persistenceService.update(event.getItemId(), event.getTimeStamp(), Event.class, "profileId", masterProfileId);
                                }
                                // we must mark all the profiles that we merged into the master as merged with the master, and they will
                                // be deleted upon next load
                                profile.setMergedWith(masterProfileId);
                                persistenceService.update(profile.getItemId(), null, Profile.class, "mergedWith", masterProfileId);
                            }
                        }
                    } catch (Exception e) {
                        logger.error("unable to execute callback action, profile and session will not be saved", e);
                        return false;
                    }
                    return true;
                }
            });
            return EventService.PROFILE_UPDATED;
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
