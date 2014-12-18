package org.oasis_open.contextserver.api.conditions.initializers;

import java.util.List;

/**
 * Created by loom on 25.06.14.
 */
public interface ChoiceListInitializer {

    public List<ChoiceListValue> getValues(Object context);
}
