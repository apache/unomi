package org.oasis_open.wemi.context.server.impl.consequences;

import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.consequences.Consequence;
import org.oasis_open.wemi.context.server.api.consequences.ConsequenceExecutor;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * Created by toto on 26/06/14.
 */
public class SetPropertyConsequence implements ConsequenceExecutor {
    public SetPropertyConsequence() {
    }

    public String getConsequenceId() {
        return "setPropertyConsequence";
    }

    public boolean execute(Consequence consequence, Event event) {
        String propertyValue = (String) consequence.getParameterValues().get("propertyValue");
        if (propertyValue.equals("now")) {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            propertyValue = format.format(new Date());
        }
        event.getUser().setProperty(
                (String) consequence.getParameterValues().get("propertyName"),
                propertyValue);
        return true;
    }

}
