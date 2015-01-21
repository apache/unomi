package org.oasis_open.contextserver.persistence.elasticsearch.conditions;

import org.apache.commons.beanutils.BeanUtilsBean;
import org.elasticsearch.ElasticsearchParseException;
import org.elasticsearch.common.joda.DateMathParser;
import org.elasticsearch.index.mapper.core.DateFieldMapper;
import org.oasis_open.contextserver.api.Item;
import org.oasis_open.contextserver.api.conditions.Condition;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Evaluator for property comparison conditions
 */
public class PropertyConditionEvaluator implements ConditionEvaluator {

    private int compare(Object value1, Object value2) {
        if (value2 == null) {
            return value1 == null ? 0 : 1;
        } else if (value1 == null) {
            return -1;
        }
        if (value2 instanceof Float || value2 instanceof Double) {
            return getDouble(value1).compareTo(getDouble(value2));
        } else if (value2 instanceof Integer || value2 instanceof Long) {
            return getLong(value1).compareTo(getLong(value2));
        } else if (value2 instanceof Date) {
            return getDate(value1).compareTo(getDate(value2));
        }

        throw new UnsupportedOperationException("Cannot compare " + value1 + " and " + value2);
    }

    private boolean compareMultivalue(Object actualValue, Object expectedValue, String op) {
        if (actualValue == null) {
            return expectedValue == null;
        } else if (expectedValue == null) {
            return false;
        }
        
        Set<Object> actual = getValueSet(actualValue);
        Set<Object> expected = getValueSet(expectedValue);
        
        boolean result = true;
        
        switch (op) {
            case "in":
                result = false;
                for (Object a : actual) {
                    if (expected.contains(a)) {
                        result = true;
                        break;
                    }
                }
                break;
            case "notIn":
                for (Object a : actual) {
                    if (expected.contains(a)) {
                        result = false;
                        break;
                    }
                }
                break;
            case "all":
                for (Object a : actual) {
                    if (!expected.contains(a)) {
                        result = false;
                        break;
                    }
                }
                break;
                
            default:
                throw new IllegalArgumentException("Unknown comparison operator " + op);
        }
        
        return result;
    }

    @Override
    public boolean eval(Condition condition, Item item, Map<String, Object> context, ConditionEvaluatorDispatcher dispatcher) {
        String op = (String) condition.getParameter("comparisonOperator");
        String name = (String) condition.getParameter("propertyName");
        Object expectedValue = condition.getParameter("propertyValue");
        Object expectedValues = condition.getParameter("propertyValues");
        Object actualValue;
        try {
            actualValue = BeanUtilsBean.getInstance().getPropertyUtils().getProperty(item, name);
        } catch (Exception e) {
            // property not found
            actualValue = null;
        }

        if(actualValue == null){
            return op.equals("missing");
        } else if (op.equals("exists")) {
            return true;
        } else if (op.equals("equals")) {
            return expectedValue.equals(actualValue);
        } else if (op.equals("notEquals")) {
            return !expectedValue.equals(actualValue);
        } else if (op.equals("greaterThan")) {
            return compare(actualValue, expectedValue) > 0;
        } else if (op.equals("greaterThanOrEqualTo")) {
            return compare(actualValue, expectedValue) >= 0;
        } else if (op.equals("lessThan")) {
            return compare(actualValue, expectedValue) < 0;
        } else if (op.equals("lessThanOrEqualTo")) {
            return compare(actualValue, expectedValue) >= 0;
        } else if (op.equals("contains")) {
            return actualValue.toString().contains(expectedValue.toString());
        } else if (op.equals("startsWith")) {
            return actualValue.toString().startsWith(expectedValue.toString());
        } else if (op.equals("endsWith")) {
            return actualValue.toString().endsWith(expectedValue.toString());
        } else if (op.equals("matchesRegex")) {
            return Pattern.compile(expectedValue.toString()).matcher(actualValue.toString()).matches();
        } else if (op.equals("in") || op.equals("notIn") || op.equals("all")) {
            return compareMultivalue(actualValue, expectedValues, op);
        }
        
        return false;
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

    private Double getDouble(Object value) {
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            // Not a number
        }
        return null;
    }

    private Long getLong(Object value) {
        if (value instanceof Number) {
            return ((Number)value).longValue();
        } else {
            try {
                return Long.parseLong(value.toString());
            } catch (NumberFormatException e) {
                // Not a number
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Set<Object> getValueSet(Object expectedValue) {
        if (expectedValue instanceof Set) {
            return (Set<Object>) expectedValue;
        } else if (expectedValue instanceof Collection) {
            return new HashSet<Object>((Collection<?>) expectedValue);
        } else {
            return Collections.singleton(expectedValue);
        }
    }
}
