package org.oasis_open.contextserver.impl.conditions.initializers;

import java.util.ArrayList;
import java.util.List;

import org.oasis_open.contextserver.api.conditions.initializers.ChoiceListInitializer;
import org.oasis_open.contextserver.api.conditions.initializers.ChoiceListValue;

/**
 * Initializer for the set of available comparison operators.
 */
public class ComparisonOperatorChoiceListInitializer implements ChoiceListInitializer {

    private static final List<ChoiceListValue> OPERATORS;

    static {
        OPERATORS = new ArrayList<ChoiceListValue>(12);
        OPERATORS.add(new ComparisonOperatorChoiceListValue("equals", "EQUALS_LABEL"));
        OPERATORS.add(new ComparisonOperatorChoiceListValue("notEquals", "NOT_EQUALS_LABEL"));
        OPERATORS.add(new ComparisonOperatorChoiceListValue("lessThan", "LESS_THAN_LABEL", "integer", "date"));
        OPERATORS.add(new ComparisonOperatorChoiceListValue("greaterThan", "GREATER_THAN_LABEL", "integer", "date"));
        OPERATORS.add(new ComparisonOperatorChoiceListValue("lessThanOrEqualTo", "LESS_THAN_OR_EQUAL_TO_LABEL",
                "integer", "date"));
        OPERATORS.add(new ComparisonOperatorChoiceListValue("greaterThanOrEqualTo", "GREATER_THAN_OR_EQUAL_TO_LABEL",
                "integer", "date"));
        OPERATORS.add(new ComparisonOperatorChoiceListValue("startsWith", "STARTS_WITH_LABEL", "string", "email"));
        OPERATORS.add(new ComparisonOperatorChoiceListValue("endsWith", "ENDS_WITH_LABEL", "string", "email"));
        OPERATORS.add(new ComparisonOperatorChoiceListValue("matchesRegex", "MATCHES_REGULAR_EXPRESSION_LABEL",
                "string", "email"));
        OPERATORS.add(new ComparisonOperatorChoiceListValue("contains", "CONTAINS_LABEL", "string", "email"));
        OPERATORS.add(new ComparisonOperatorChoiceListValue("exists", "EXISTS_LABEL"));
        OPERATORS.add(new ComparisonOperatorChoiceListValue("missing", "MISSING_LABEL"));
        
        OPERATORS.add(new ComparisonOperatorChoiceListValue("in", "IN_LABEL"));
        OPERATORS.add(new ComparisonOperatorChoiceListValue("notIn", "NOT_IN_LABEL"));
        OPERATORS.add(new ComparisonOperatorChoiceListValue("all", "ALL_LABEL"));
    }

    @Override
    public List<ChoiceListValue> getValues(Object context) {
        return OPERATORS;
    }
}
