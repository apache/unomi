package org.oasis_open.contextserver.api.conditions.initializers;

import java.util.List;

/**
 * Defines an initializer for a list of options.
 */
public interface ChoiceListInitializer {

    /**
     * Returns a list of options for this choice list.
     * 
     * @param context
     *            a context object containing supporting information
     * @return a list of options for this choice list
     */
    List<ChoiceListValue> getValues(Object context);
}
