package org.oasis_open.wemi.context.server.impl.actions;

import org.apache.commons.beanutils.PropertyUtils;
import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.actions.Action;
import org.oasis_open.wemi.context.server.api.actions.ActionExecutor;

import java.util.HashMap;
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
            Map<String, Object> interests = (Map<String, Object>) PropertyUtils.getProperty(event, "target.properties.interests");
            if (interests != null) {
                for (Map.Entry<String, Object> s : interests.entrySet()) {
                    int value = (Integer) s.getValue();

                    HashMap<String, Object> userInterests = (HashMap<String, Object>) event.getUser().getProperty("interests");
                    if(userInterests != null){
                        userInterests = new HashMap<String, Object>(userInterests);
                        int oldValue = (userInterests.containsKey(s.getKey())) ? (Integer) userInterests.get(s.getKey()) : 0;
                        userInterests.put(s.getKey(), value + oldValue);
                    }else {
                        userInterests = new HashMap<String, Object>();
                        userInterests.put(s.getKey(), value);
                    }
                    event.getUser().setProperty("interests", userInterests);
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
