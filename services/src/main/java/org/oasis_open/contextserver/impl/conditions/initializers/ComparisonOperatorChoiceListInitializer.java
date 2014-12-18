package org.oasis_open.contextserver.impl.conditions.initializers;

import org.oasis_open.contextserver.api.conditions.initializers.ChoiceListInitializer;
import org.oasis_open.contextserver.api.conditions.initializers.ChoiceListValue;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by loom on 25.06.14.
 */
public class ComparisonOperatorChoiceListInitializer implements ChoiceListInitializer {

    public List<ChoiceListValue> getValues(Object context) {
        List<ChoiceListValue> values = new ArrayList<ChoiceListValue>();
        values.add(new ChoiceListValue("equals", "EQUALS_LABEL"));
        values.add(new ChoiceListValue("notEquals", "NOT_EQUALS_LABEL"));
        values.add(new ChoiceListValue("lessThan", "LESS_THAN_LABEL"));
        values.add(new ChoiceListValue("greaterThan", "GREATER_THAN_LABEL"));
        values.add(new ChoiceListValue("lessThanOrEqualTo", "LESS_THAN_OR_EQUAL_TO_LABEL"));
        values.add(new ChoiceListValue("greaterThanOrEqualTo", "GREATER_THAN_OR_EQUAL_TO_LABEL"));
        values.add(new ChoiceListValue("startsWith", "STARTS_WITH_LABEL"));
        values.add(new ChoiceListValue("endsWith", "ENDS_WITH_LABEL"));
        values.add(new ChoiceListValue("matchesRegex", "MATCHES_REGULAR_EXPRESSION_LABEL"));
        values.add(new ChoiceListValue("contains", "CONTAINS_LABEL"));
        values.add(new ChoiceListValue("exists", "EXISTS_LABEL"));
        values.add(new ChoiceListValue("missing", "MISSING_LABEL"));
        return values;
    }
}
