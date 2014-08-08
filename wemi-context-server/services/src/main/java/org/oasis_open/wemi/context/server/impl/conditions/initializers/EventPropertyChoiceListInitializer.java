package org.oasis_open.wemi.context.server.impl.conditions.initializers;

import org.oasis_open.wemi.context.server.api.conditions.initializers.ChoiceListInitializer;
import org.oasis_open.wemi.context.server.api.conditions.initializers.ChoiceListValue;
import org.oasis_open.wemi.context.server.api.services.EventService;
import org.ops4j.pax.cdi.api.OsgiServiceProvider;
import org.ops4j.pax.cdi.api.Properties;
import org.ops4j.pax.cdi.api.Property;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

/**
 * @todo we should integrate resource bundles for property names (and possibly add descriptions to choice list values)
 */
@ApplicationScoped
@OsgiServiceProvider
@Properties({
    @Property(name = "initializerId", value = "eventProperty")
})
public class EventPropertyChoiceListInitializer implements ChoiceListInitializer {

    @Inject
    EventService eventService;

    public List<ChoiceListValue> getValues(Object context) {
        List<ChoiceListValue> choiceListValues = new ArrayList<ChoiceListValue>();
        List<String> userProperties = eventService.getEventProperties();
        for (String userProperty : userProperties) {
            choiceListValues.add(new ChoiceListValue(userProperty, userProperty));
        }
        return choiceListValues;
    }
}
