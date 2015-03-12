package org.oasis_open.contextserver.persistence.elasticsearch.conditions;

/*
 * #%L
 * context-server-persistence-elasticsearch-core
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2014 - 2015 Jahia Solutions
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.apache.commons.beanutils.BeanUtilsBean;
import org.apache.commons.lang3.ObjectUtils;
import org.elasticsearch.ElasticsearchParseException;
import org.elasticsearch.common.joda.DateMathParser;
import org.elasticsearch.index.mapper.core.DateFieldMapper;
import org.oasis_open.contextserver.api.Item;
import org.oasis_open.contextserver.api.conditions.Condition;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Evaluator for property comparison conditions
 */
public class PropertyConditionEvaluator implements ConditionEvaluator {

    private int compare(Object actualValue, String expectedValue, Object expectedValueDate, Object expectedValueInteger, Object expectedValueDateExpr) {
        if (expectedValue == null && expectedValueDate == null && expectedValueInteger == null && getDate(expectedValueDateExpr) == null) {
            return actualValue == null ? 0 : 1;
        } else if (actualValue == null) {
            return -1;
        }

        if (expectedValueInteger != null) {
            return getInteger(actualValue).compareTo(getInteger(expectedValueInteger));
        } else if (expectedValueDate != null) {
            return getDate(actualValue).compareTo(getDate(expectedValueDate));
        } else if (expectedValueDateExpr != null) {
            return getDate(actualValue).compareTo(getDate(expectedValueDateExpr));
        } else {
            return actualValue.toString().compareTo(expectedValue);
        }
    }

    private boolean compareMultivalue(Object actualValue, List expectedValues, List expectedValuesDate, List expectedValuesNumber, List expectedValuesDateExpr, String op) {
        List expected = ObjectUtils.firstNonNull(expectedValues, expectedValuesDate, expectedValuesNumber);
        if (actualValue == null) {
            return expected == null;
        } else if (expected == null) {
            return false;
        }
        
        Set<Object> actual = getValueSet(actualValue);

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
                for (Object e : expected) {
                    if (!actual.contains(e)) {
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

        String expectedValue = (String) condition.getParameter("propertyValue");
        Object expectedValueInteger = condition.getParameter("propertyValueInteger");
        Object expectedValueDate = condition.getParameter("propertyValueDate");
        Object expectedValueDateExpr = condition.getParameter("propertyValueDateExpr");

        List expectedValues = (List) condition.getParameter("propertyValues");
        List expectedValuesInteger = (List) condition.getParameter("propertyValuesInteger");
        List expectedValuesDate = (List) condition.getParameter("propertyValuesDate");
        List expectedValuesDateExpr = (List) condition.getParameter("propertyValuesDateExpr");

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
            if (actualValue instanceof Collection) {
                for (Object o : ((Collection)actualValue)) {
                    if (compare(o, expectedValue, expectedValueDate, expectedValueInteger, expectedValueDateExpr) == 0) {
                        return true;
                    }
                }
                return false;
            }
            return compare(actualValue, expectedValue, expectedValueDate, expectedValueInteger, expectedValueDateExpr) == 0;
        } else if (op.equals("notEquals")) {
            return compare(actualValue, expectedValue, expectedValueDate, expectedValueInteger, expectedValueDateExpr) != 0;
        } else if (op.equals("greaterThan")) {
            return compare(actualValue, expectedValue, expectedValueDate, expectedValueInteger, expectedValueDateExpr) > 0;
        } else if (op.equals("greaterThanOrEqualTo")) {
            return compare(actualValue, expectedValue, expectedValueDate, expectedValueInteger, expectedValueDateExpr) >= 0;
        } else if (op.equals("lessThan")) {
            return compare(actualValue, expectedValue, expectedValueDate, expectedValueInteger, expectedValueDateExpr) < 0;
        } else if (op.equals("lessThanOrEqualTo")) {
            return compare(actualValue, expectedValue, expectedValueDate, expectedValueInteger, expectedValueDateExpr) >= 0;
        } else if (op.equals("between")) {
            return
                    compare(actualValue, null, expectedValuesDate != null ? (Date) expectedValuesDate.get(0) : null, expectedValuesInteger != null ? (Integer) expectedValuesInteger.get(0) : null, expectedValuesDateExpr != null ? (String) expectedValuesDateExpr.get(0) : null) >= 0
                            &&
                            compare(actualValue, null, expectedValuesDate != null ? (Date) expectedValuesDate.get(1) : null, expectedValuesInteger != null ? (Integer) expectedValuesInteger.get(1) : null, expectedValuesDateExpr != null ? (String) expectedValuesDateExpr.get(1) : null) < 0;
        } else if (op.equals("contains")) {
            return actualValue.toString().contains(expectedValue);
        } else if (op.equals("startsWith")) {
            return actualValue.toString().startsWith(expectedValue);
        } else if (op.equals("endsWith")) {
            return actualValue.toString().endsWith(expectedValue);
        } else if (op.equals("matchesRegex")) {
            return Pattern.compile(expectedValue).matcher(actualValue.toString()).matches();
        } else if (op.equals("in") || op.equals("notIn") || op.equals("all")) {
            return compareMultivalue(actualValue, expectedValues, expectedValuesDate, expectedValuesInteger, expectedValuesDateExpr, op);
        }
        
        return false;
    }

    private Date getDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Date) {
            return ((Date) value);
        } else {
            DateMathParser parser = new DateMathParser(DateFieldMapper.Defaults.DATE_TIME_FORMATTER, TimeUnit.MILLISECONDS);
            try {
                return new Date(parser.parse(value.toString(), System.currentTimeMillis()));
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

    private Integer getInteger(Object value) {
        if (value instanceof Number) {
            return ((Number)value).intValue();
        } else {
            try {
                return Integer.parseInt(value.toString());
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
