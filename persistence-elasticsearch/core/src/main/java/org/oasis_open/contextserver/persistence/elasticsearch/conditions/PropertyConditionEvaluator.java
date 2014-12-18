package org.oasis_open.contextserver.persistence.elasticsearch.conditions;

import org.apache.commons.beanutils.BeanUtils;
import org.elasticsearch.ElasticsearchParseException;
import org.elasticsearch.common.joda.DateMathParser;
import org.elasticsearch.index.mapper.core.DateFieldMapper;
import org.oasis_open.contextserver.api.Item;
import org.oasis_open.contextserver.api.conditions.Condition;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Evaluator for property comparison conditions
 */
public class PropertyConditionEvaluator implements ConditionEvaluator {

    @Override
    public boolean eval(Condition condition, Item item, Map<String, Object> context, ConditionEvaluatorDispatcher dispatcher) {
        String op = (String) condition.getParameterValues().get("comparisonOperator");
        String name = (String) condition.getParameterValues().get("propertyName");
        Object expectedValue = condition.getParameterValues().get("propertyValue");
        Object actualValue;
        try {
            actualValue = BeanUtils.getProperty(item, name);
        } catch (Exception e) {
            // property not found
            actualValue = null;
        }

        if(actualValue == null){
            return op.equals("missing");
        } else if (op.equals("equals")) {
            return expectedValue.equals(actualValue);
        } else if (op.equals("greaterThan")) {
            return compare(actualValue, expectedValue) > 0;
        } else if (op.equals("greaterThanOrEqualTo")) {
            return compare(actualValue, expectedValue) >= 0;
        } else if (op.equals("lessThan")) {
            return compare(actualValue, expectedValue) < 0;
        } else if (op.equals("lessThanOrEqualTo")) {
            return compare(actualValue, expectedValue) >= 0;
        } else if (op.equals("exists")) {
            return true;
        } else if (op.equals("contains")) {
            return actualValue.toString().contains(expectedValue.toString());
        } else if (op.equals("startsWith")) {
            return actualValue.toString().startsWith(expectedValue.toString());
        } else if (op.equals("endsWith")) {
            return actualValue.toString().endsWith(expectedValue.toString());
        } else if (op.equals("matchesRegex")) {
            return Pattern.compile(expectedValue.toString()).matcher(actualValue.toString()).matches();
        }

        return false;
    }


    private int compare(Object value1, Object value2) {
        Long date1 = getDate(value1);
        Long date2 = getDate(value2);
        if (date1 != null && date2 != null) {
            return date1.compareTo(date2);
        }
        Long long1 = getLong(value1);
        Long long2 = getLong(value2);
        if (long1 != null && long2 != null) {
            return long1.compareTo(long2);
        }
        Double double1 = getDouble(value1);
        Double double2 = getDouble(value2);
        if (double1 != null && double2 != null) {
            return double1.compareTo(double2);
        }
        throw new UnsupportedOperationException("Cannot compare " + value1 + " and " + value2);
    }

    private Double getDouble(Object value) {
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            // Not a number
        }
        return null;
    }

    private Long getLong(Object value) {
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            // Not a number
        }
        return null;
    }

    private Long getDate(Object value) {
        if (value instanceof Date) {
            return ((Date) value).getTime();
        } else {
            DateMathParser parser = new DateMathParser(DateFieldMapper.Defaults.DATE_TIME_FORMATTER, TimeUnit.MILLISECONDS);
            try {
                return parser.parse(value.toString(), System.currentTimeMillis());
            } catch (ElasticsearchParseException e) {
                // Not a date
            }
        }
        return null;
    }
}
