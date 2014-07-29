package org.oasis_open.wemi.context.server.impl.conditions.initializers;

import org.oasis_open.wemi.context.server.api.conditions.initializers.ChoiceListInitializer;
import org.oasis_open.wemi.context.server.api.conditions.initializers.ChoiceListValue;
import org.ops4j.pax.cdi.api.OsgiServiceProvider;
import org.ops4j.pax.cdi.api.Properties;
import org.ops4j.pax.cdi.api.Property;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Default;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by loom on 25.06.14.
 */
@ApplicationScoped
@OsgiServiceProvider
@Properties({
    @Property(name = "initializerId", value = "comparisonOperator")
})
public class ComparisonOperatorChoiceListInitializer implements ChoiceListInitializer {

    public List<ChoiceListValue> getValues(Object context) {
        List<ChoiceListValue> values = new ArrayList<ChoiceListValue>();
        values.add(new ChoiceListValue("equals","equals"));
        values.add(new ChoiceListValue("notEquals", "not equals"));
        values.add(new ChoiceListValue("lessThan", "less than"));
        values.add(new ChoiceListValue("greaterThan", "greater than"));
        values.add(new ChoiceListValue("lessThanOrEqualTo", "less than or equal to"));
        values.add(new ChoiceListValue("greaterThanOrEqualTo","greater than or equal to"));
        values.add(new ChoiceListValue("startsWith", "starts with"));
        values.add(new ChoiceListValue("endsWith", "ends with"));
        values.add(new ChoiceListValue("matchesRegex", "matches regular expression"));
        values.add(new ChoiceListValue("contains", "contains"));
        return values;
    }
}
