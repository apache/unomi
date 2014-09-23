package org.oasis_open.wemi.context.server.impl.actions;

import org.apache.commons.lang3.StringUtils;
import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.actions.Action;
import org.oasis_open.wemi.context.server.api.actions.ActionExecutor;

import java.util.Map;

/**
 * Created by toto on 29/08/14.
 */
public class IncrementInterestsValuesAction implements ActionExecutor {

    @Override
    public boolean execute(Action action, Event event) {
        boolean modified = false;
        Map<String, Object> userProps = event.getUser().getProperties();

        for (String s : event.getProperties().keySet()) {
            if (s.startsWith("page.interests.")) {
                String interestName = StringUtils.substringAfter(s, "page.");
                int value = (Integer) event.getProperty(s);
                int oldValue = (userProps.containsKey(interestName)) ? (Integer) userProps.get(interestName) : 0;
                event.getUser().setProperty(interestName, value + oldValue);
                modified = true;
            }
        }
        return modified;
    }
}
