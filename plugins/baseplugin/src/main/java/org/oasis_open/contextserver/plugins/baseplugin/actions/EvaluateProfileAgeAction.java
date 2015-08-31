package org.oasis_open.contextserver.plugins.baseplugin.actions;

import org.joda.time.DateTime;
import org.joda.time.Years;
import org.oasis_open.contextserver.api.Event;
import org.oasis_open.contextserver.api.actions.Action;
import org.oasis_open.contextserver.api.actions.ActionExecutor;
import org.oasis_open.contextserver.api.services.EventService;

/**
 * Created by kevan on 11/08/15.
 */
public class EvaluateProfileAgeAction implements ActionExecutor {

    @Override
    public int execute(Action action, Event event) {
        boolean updated = false;
        if(event.getProfile().getProperty("birthDate") != null) {
            Integer y = Years.yearsBetween(new DateTime(event.getProfile().getProperty("birthDate")), new DateTime()).getYears();
            if(event.getProfile().getProperty("age") == null || event.getProfile().getProperty("age") != y){
                updated = true;
                event.getProfile().setProperty("age", y);
            }
        }
        return updated ? EventService.PROFILE_UPDATED : EventService.NO_CHANGE;
    }
}
