/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.unomi.plugins.baseplugin.conditions;

import ognl.*;
import ognl.enhance.ExpressionAccessor;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.unomi.api.*;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.persistence.elasticsearch.conditions.ConditionContextHelper;
import org.apache.unomi.persistence.elasticsearch.conditions.ConditionEvaluator;
import org.apache.unomi.persistence.elasticsearch.conditions.ConditionEvaluatorDispatcher;
import org.apache.unomi.persistence.spi.PropertyHelper;
import org.elasticsearch.ElasticsearchParseException;
import org.elasticsearch.common.joda.Joda;
import org.elasticsearch.common.joda.JodaDateMathParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Member;
import java.lang.reflect.Modifier;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Evaluator for property comparison conditions
 */
public class PropertyConditionEvaluator implements ConditionEvaluator {

    private static final Logger logger = LoggerFactory.getLogger(PropertyConditionEvaluator.class.getName());

    private static final SimpleDateFormat yearMonthDayDateFormat = new SimpleDateFormat("yyyyMMdd");
    public static final String NOT_OPTIMIZED_MARKER = "$$$###NOT_OPTIMIZED###$$$";

    private Map<String, Map<String, ExpressionAccessor>> expressionCache = new HashMap<>(64);
    private boolean usePropertyConditionOptimizations = true;

    public void setUsePropertyConditionOptimizations(boolean usePropertyConditionOptimizations) {
        this.usePropertyConditionOptimizations = usePropertyConditionOptimizations;
    }

    private int compare(Object actualValue, String expectedValue, Object expectedValueDate, Object expectedValueInteger, Object expectedValueDateExpr) {
        if (expectedValue == null && expectedValueDate == null && expectedValueInteger == null && getDate(expectedValueDateExpr) == null) {
            return actualValue == null ? 0 : 1;
        } else if (actualValue == null) {
            return -1;
        }

        if (expectedValueInteger != null) {
            return PropertyHelper.getInteger(actualValue).compareTo(PropertyHelper.getInteger(expectedValueInteger));
        } else if (expectedValueDate != null) {
            return getDate(actualValue).compareTo(getDate(expectedValueDate));
        } else if (expectedValueDateExpr != null) {
            return getDate(actualValue).compareTo(getDate(expectedValueDateExpr));
        } else {
            return actualValue.toString().compareTo(expectedValue);
        }
    }

    private boolean compareMultivalue(Object actualValue, List<?> expectedValues, List<?> expectedValuesDate, List<?> expectedValuesNumber, List<?> expectedValuesDateExpr, String op) {
        @SuppressWarnings("unchecked")
        List<?> expected = ObjectUtils.firstNonNull(expectedValues, expectedValuesDate, expectedValuesNumber);
        if (actualValue == null) {
            return expected == null;
        } else if (expected == null) {
            return false;
        }

        List<Object> actual = ConditionContextHelper.foldToASCII(getValueSet(actualValue));

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
            case "inContains":
                result = false;
                for (Object a : actual) {
                    for (Object b : expected)
                        if (((String) a).contains((String) b)) {
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
            case "hasNoneOf":
                if (!Collections.disjoint(actual, expected)) {
                    return false;
                }
                break;
            case "hasSomeOf":
                if (Collections.disjoint(actual, expected)) {
                    return false;
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

        String expectedValue = ConditionContextHelper.foldToASCII((String) condition.getParameter("propertyValue"));
        Object expectedValueInteger = condition.getParameter("propertyValueInteger");
        Object expectedValueDate = condition.getParameter("propertyValueDate");
        Object expectedValueDateExpr = condition.getParameter("propertyValueDateExpr");

        Object actualValue;
        if (item instanceof Event && "eventType".equals(name)) {
            actualValue = ((Event) item).getEventType();
        } else {
            try {
                long time = System.nanoTime();
                //actualValue = beanUtilsBean.getPropertyUtils().getProperty(item, name);
                actualValue = getPropertyValue(item, name);
                time = System.nanoTime() - time;
                if (time > 5000000L) {
                    logger.info("eval took {} ms for {} {}", time / 1000000L, item.getClass().getName(), name);
                }
            } catch (NullPointerException e) {
                // property not found
                actualValue = null;
            } catch (Exception e) {
                if (!(e instanceof OgnlException)
                        || (!StringUtils.startsWith(e.getMessage(),
                        "source is null for getProperty(null"))) {
                    logger.warn("Error evaluating value for " + item.getClass().getName() + " " + name, e);
                }
                actualValue = null;
            }
        }
        if (actualValue instanceof String) {
            actualValue = ConditionContextHelper.foldToASCII((String) actualValue);
        }

        if (op == null) {
            return false;
        } else if (actualValue == null) {
            return op.equals("missing");
        } else if (op.equals("exists")) {
            return true;
        } else if (op.equals("equals")) {
            if (actualValue instanceof Collection) {
                for (Object o : ((Collection<?>) actualValue)) {
                    if (o instanceof String) {
                        o = ConditionContextHelper.foldToASCII((String) o);
                    }
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
            return compare(actualValue, expectedValue, expectedValueDate, expectedValueInteger, expectedValueDateExpr) <= 0;
        } else if (op.equals("between")) {
            List<?> expectedValuesInteger = (List<?>) condition.getParameter("propertyValuesInteger");
            List<?> expectedValuesDate = (List<?>) condition.getParameter("propertyValuesDate");
            List<?> expectedValuesDateExpr = (List<?>) condition.getParameter("propertyValuesDateExpr");
            return compare(actualValue, null,
                    (expectedValuesDate != null && expectedValuesDate.size() >= 1) ? getDate(expectedValuesDate.get(0)) : null,
                    (expectedValuesInteger != null && expectedValuesInteger.size() >= 1) ? (Integer) expectedValuesInteger.get(0) : null,
                    (expectedValuesDateExpr != null && expectedValuesDateExpr.size() >= 1) ? (String) expectedValuesDateExpr.get(0) : null) >= 0
                    &&
                    compare(actualValue, null,
                            (expectedValuesDate != null && expectedValuesDate.size() >= 2) ? getDate(expectedValuesDate.get(1)) : null,
                            (expectedValuesInteger != null && expectedValuesInteger.size() >= 2) ? (Integer) expectedValuesInteger.get(1) : null,
                            (expectedValuesDateExpr != null && expectedValuesDateExpr.size() >= 2) ? (String) expectedValuesDateExpr.get(1) : null) <= 0;
        } else if (op.equals("contains")) {
            return actualValue.toString().contains(expectedValue);
        } else if (op.equals("notContains")) {
            return !actualValue.toString().contains(expectedValue);
        } else if (op.equals("startsWith")) {
            return actualValue.toString().startsWith(expectedValue);
        } else if (op.equals("endsWith")) {
            return actualValue.toString().endsWith(expectedValue);
        } else if (op.equals("matchesRegex")) {
            return expectedValue != null && Pattern.compile(expectedValue).matcher(actualValue.toString()).matches();
        } else if (op.equals("in") || op.equals("inContains") || op.equals("notIn") || op.equals("hasSomeOf") || op.equals("hasNoneOf") || op.equals("all")) {
            List<?> expectedValues = ConditionContextHelper.foldToASCII((List<?>) condition.getParameter("propertyValues"));
            List<?> expectedValuesInteger = (List<?>) condition.getParameter("propertyValuesInteger");
            List<?> expectedValuesDate = (List<?>) condition.getParameter("propertyValuesDate");
            List<?> expectedValuesDateExpr = (List<?>) condition.getParameter("propertyValuesDateExpr");

            return compareMultivalue(actualValue, expectedValues, expectedValuesDate, expectedValuesInteger, expectedValuesDateExpr, op);
        } else if (op.equals("isDay") && expectedValueDate != null) {
            return yearMonthDayDateFormat.format(getDate(actualValue)).equals(yearMonthDayDateFormat.format(getDate(expectedValueDate)));
        } else if (op.equals("isNotDay") && expectedValueDate != null) {
            return !yearMonthDayDateFormat.format(getDate(actualValue)).equals(yearMonthDayDateFormat.format(getDate(expectedValueDate)));
        }

        return false;
    }

    protected Object getPropertyValue(Item item, String expression) throws Exception {
        if (usePropertyConditionOptimizations) {
            Object result = getHardcodedPropertyValue(item, expression);
            if (!NOT_OPTIMIZED_MARKER.equals(result)) {
                return result;
            }
        }
        return getOGNLPropertyValue(item, expression);
    }

    protected Object getHardcodedPropertyValue(Item item, String expression) {
        // the following are optimizations to avoid using the expressions that are slower. The main objective here is
        // to avoid the most used expression that may also trigger calls to the Java Reflection API.
        if (item instanceof Event) {
            Event event = (Event) item;
            if (expression.startsWith("properties.")) {
                return getNestedPropertyValue(expression.substring("properties.".length()), event.getProperties());
            }
            if ("target.itemId".equals(expression)) {
                return event.getTarget().getItemId();
            }
            if (expression.startsWith("target.properties.")) {
                if (event.getTarget() instanceof CustomItem) {
                    CustomItem customItem = (CustomItem) event.getTarget();
                    String expressionPart = expression.substring("target.properties.".length());
                    return getNestedPropertyValue(expressionPart, customItem.getProperties());
                }
            }
            if ("target.scope".equals(expression)) {
                return event.getTarget().getScope();
            }
            if ("scope".equals(expression)) {
                return event.getScope();
            }
        } else if (item instanceof Session) {
            Session session = (Session) item;
            if ("timeStamp".equals(expression)) {
                return session.getTimeStamp();
            }
            if ("duration".equals(expression)) {
                return session.getDuration();
            }
            if ("size".equals(expression)) {
                return session.getSize();
            }
            if (expression.startsWith("properties.")) {
                return getNestedPropertyValue(expression.substring("properties.".length()), session.getProperties());
            }
            if (expression.startsWith("systemProperties.")) {
                return getNestedPropertyValue(expression.substring("systemProperties.".length()), session.getSystemProperties());
            }
        } else if (item instanceof Profile) {
            Profile profile = (Profile) item;
            if ("segments".equals(expression)) {
                return profile.getSegments();
            }
            if ("consents".equals(expression)) {
                return profile.getConsents();
            }
            if (expression.startsWith("scores.")) {
                return profile.getScores().get(expression.substring("scores.".length()));
            }
            if (expression.startsWith("properties.")) {
                return getNestedPropertyValue(expression.substring("properties.".length()), profile.getProperties());
            }
            if (expression.startsWith("systemProperties.")) {
                return getNestedPropertyValue(expression.substring("systemProperties.".length()), profile.getSystemProperties());
            }
        } else if (item instanceof CustomItem) {
            CustomItem customItem = (CustomItem) item;
            if (expression.startsWith("properties.")) {
                return getNestedPropertyValue(expression.substring("properties.".length()), customItem.getProperties());
            }
        }
        return NOT_OPTIMIZED_MARKER;
    }

    protected Object getOGNLPropertyValue(Item item, String expression) throws Exception {
        ExpressionAccessor accessor = getPropertyAccessor(item, expression);
        return accessor != null ? accessor.get(getOgnlContext(), item) : null;
    }

    private Object getNestedPropertyValue(String expressionPart, Map<String, Object> properties) {
        int nextDotPos = expressionPart.indexOf(".");
        if (nextDotPos > -1) {
            String mapKey = expressionPart.substring(0, nextDotPos);
            Object mapValue = properties.get(mapKey);
            if (mapValue == null) {
                return null;
            }
            String nextExpression = expressionPart.substring(nextDotPos+1);
            return getNestedPropertyValue(nextExpression, (Map<String,Object>) mapValue);
        } else {
            return properties.get(expressionPart);
        }
    }

    private OgnlContext getOgnlContext() {
        return (OgnlContext) Ognl.createDefaultContext(null, new MemberAccess() {
            @Override
            public Object setup(Map context, Object target, Member member, String propertyName) {
                return null;
            }

            @Override
            public void restore(Map context, Object target, Member member, String propertyName, Object state) {

            }

            @Override
            public boolean isAccessible(Map context, Object target, Member member, String propertyName) {
                int modifiers = member.getModifiers();
                boolean result = Modifier.isPublic(modifiers);
                return result;
            }
        });
    }

    private ExpressionAccessor getPropertyAccessor(Item item, String expression) throws Exception {
        ExpressionAccessor accessor = null;
        String clazz = item.getClass().getName();
        Map<String, ExpressionAccessor> expressions = expressionCache.get(clazz);
        if (expressions == null) {
            expressions = new HashMap<>();
            expressionCache.put(clazz, expressions);
        } else {
            accessor = expressions.get(expression);
        }
        if (accessor == null) {
            long time = System.nanoTime();
            Thread current = Thread.currentThread();
            ClassLoader contextCL = current.getContextClassLoader();
            try {
                current.setContextClassLoader(PropertyConditionEvaluator.class.getClassLoader());
                Node node = Ognl.compileExpression(getOgnlContext() , item, expression);
                accessor = node.getAccessor();
            } finally {
                current.setContextClassLoader(contextCL);
            }
            if (accessor != null) {
                expressions.put(expression, accessor);
            } else {
                logger.warn("Unable to compile expression for {} and {}", clazz, expression);
            }
            if (logger.isInfoEnabled()) {
                time = System.nanoTime() - time;
                logger.info("Expression compilation for item={} expression={} took {}", item.getClass().getName(), expression, time / 1000000L);
            }
        }

        return accessor;
    }

    private Date getDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Date) {
            return ((Date) value);
        } else {
            JodaDateMathParser parser = new JodaDateMathParser(Joda.forPattern("strictDateOptionalTime||epoch_millis"));
            try {
                return new Date(parser.parse(value.toString(), System::currentTimeMillis).getEpochSecond());
            } catch (ElasticsearchParseException e) {
                logger.warn("unable to parse date " + value.toString(), e);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Object> getValueSet(Object expectedValue) {
        if (expectedValue instanceof List) {
            return (List<Object>) expectedValue;
        } else if (expectedValue instanceof Collection) {
            return new ArrayList<Object>((Collection<?>) expectedValue);
        } else {
            return Collections.singletonList(expectedValue);
        }
    }
}
