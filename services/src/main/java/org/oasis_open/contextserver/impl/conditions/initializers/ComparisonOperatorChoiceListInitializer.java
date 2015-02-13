package org.oasis_open.contextserver.impl.conditions.initializers;

import java.util.ArrayList;
import java.util.List;

import org.oasis_open.contextserver.api.conditions.initializers.ChoiceListInitializer;
import org.oasis_open.contextserver.api.conditions.initializers.ChoiceListValue;
import org.oasis_open.contextserver.api.conditions.initializers.I18nSupport;

/**
 * Initializer for the set of available comparison operators.
 */
public class ComparisonOperatorChoiceListInitializer implements ChoiceListInitializer, I18nSupport {

    private static final List<ChoiceListValue> OPERATORS;

    static {
        OPERATORS = new ArrayList<ChoiceListValue>(12);
        OPERATORS.add(new ComparisonOperatorChoiceListValue("equals", "comparisonOperator.equals"));
        OPERATORS.add(new ComparisonOperatorChoiceListValue("notEquals", "comparisonOperator.notEquals"));
        OPERATORS.add(new ComparisonOperatorChoiceListValue("lessThan", "comparisonOperator.lessThan", "integer", "date"));
        OPERATORS.add(new ComparisonOperatorChoiceListValue("greaterThan", "comparisonOperator.greaterThan", "integer", "date"));
        OPERATORS.add(new ComparisonOperatorChoiceListValue("lessThanOrEqualTo", "comparisonOperator.lessThanOrEqualTo",
                "integer", "date"));
        OPERATORS.add(new ComparisonOperatorChoiceListValue("greaterThanOrEqualTo", "comparisonOperator.greaterThanOrEqualTo",
                "integer", "date"));
        OPERATORS.add(new ComparisonOperatorChoiceListValue("between", "comparisonOperator.between",
                "integer", "date"));
        OPERATORS.add(new ComparisonOperatorChoiceListValue("startsWith", "comparisonOperator.startsWith", "string", "email"));
        OPERATORS.add(new ComparisonOperatorChoiceListValue("endsWith", "comparisonOperator.endsWith", "string", "email"));
        OPERATORS.add(new ComparisonOperatorChoiceListValue("matchesRegex", "comparisonOperator.matchesRegularExpression",
                "string", "email"));
        OPERATORS.add(new ComparisonOperatorChoiceListValue("contains", "comparisonOperator.contains", "string", "email"));
        OPERATORS.add(new ComparisonOperatorChoiceListValue("exists", "comparisonOperator.exists"));
        OPERATORS.add(new ComparisonOperatorChoiceListValue("missing", "comparisonOperator.missing"));
        
        OPERATORS.add(new ComparisonOperatorChoiceListValue("in", "comparisonOperator.in"));
        OPERATORS.add(new ComparisonOperatorChoiceListValue("notIn", "comparisonOperator.notIn"));
        OPERATORS.add(new ComparisonOperatorChoiceListValue("all", "comparisonOperator.all"));
    }

    @Override
    public List<ChoiceListValue> getValues(Object context) {
        return OPERATORS;
    }
}
