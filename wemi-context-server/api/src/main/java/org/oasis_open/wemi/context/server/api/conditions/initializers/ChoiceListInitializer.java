package org.oasis_open.wemi.context.server.api.conditions.initializers;

import java.util.List;

/**
 * Created by loom on 25.06.14.
 */
public abstract class ChoiceListInitializer {

    public abstract List<ChoiceListValue> getValues(Object context);
}
