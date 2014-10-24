package org.oasis_open.wemi.context.server.impl.actions;

import org.apache.commons.beanutils.PropertyUtils;
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

        try {
            Map<String, Object> interests = (Map<String, Object>) PropertyUtils.getProperty(event, "properties.page.interests");
            if (interests != null) {
                for (Map.Entry<String, Object> s : interests.entrySet()) {
                    int value = (Integer) s.getValue();
                    int oldValue = (userProps.containsKey(s.getKey())) ? (Integer) userProps.get(s.getKey()) : 0;
                    event.getUser().setProperty(s.getKey(), value + oldValue);
                    modified = true;
                }
            }
        } catch (UnsupportedOperationException e) {
            throw e;
        } catch (Exception e) {
            throw new UnsupportedOperationException(e);
        }

        return modified;
    }
}
