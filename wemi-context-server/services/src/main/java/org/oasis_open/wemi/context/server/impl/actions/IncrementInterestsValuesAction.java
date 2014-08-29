package org.oasis_open.wemi.context.server.impl.actions;

import org.apache.commons.lang3.StringUtils;
import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.actions.Action;
import org.oasis_open.wemi.context.server.api.actions.ActionExecutor;

import java.util.Properties;

/**
 * Created by toto on 29/08/14.
 */
public class IncrementInterestsValuesAction implements ActionExecutor {

    @Override
    public boolean execute(Action action, Event event) {
        boolean modified = false;

        Properties userProps = event.getUser().getProperties();

        for (String s : event.getProperties().stringPropertyNames()) {
            if (s.startsWith("page.interests.")) {
                String interestName = StringUtils.substringAfter(s,"page.");
                int value = Integer.parseInt(event.getProperties().getProperty(s));
                int oldValue = (userProps.containsKey(interestName)) ? Integer.parseInt(userProps.getProperty(interestName)) : 0;
                userProps.setProperty(interestName, Integer.toString(value + oldValue));
                modified = true;
            }
        }
        return modified;
    }
}
