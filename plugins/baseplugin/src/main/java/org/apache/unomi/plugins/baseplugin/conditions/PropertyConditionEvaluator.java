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
import org.apache.unomi.plugins.baseplugin.conditions.accessors.HardcodedPropertyAccessor;
import org.apache.unomi.scripting.ExpressionFilterFactory;
import org.apache.unomi.scripting.SecureFilteringClassLoader;
import org.apache.unomi.persistence.spi.conditions.ConditionContextHelper;
import org.apache.unomi.persistence.spi.conditions.ConditionEvaluator;
import org.apache.unomi.persistence.spi.conditions.ConditionEvaluatorDispatcher;
import org.apache.unomi.persistence.spi.PropertyHelper;
import org.elasticsearch.ElasticsearchParseException;
import org.elasticsearch.common.joda.Joda;
import org.elasticsearch.common.joda.JodaDateMathParser;
import org.elasticsearch.common.unit.DistanceUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Member;
import java.lang.reflect.Modifier;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Evaluator for property comparison conditions
 */
public class PropertyConditionEvaluator implements ConditionEvaluator {

    private static final Logger LOGGER = LoggerFactory.getLogger(PropertyConditionEvaluator.class.getName());

    private static final SimpleDateFormat yearMonthDayDateFormat = new SimpleDateFormat("yyyyMMdd");

    private final Map<String, Map<String, ExpressionAccessor>> expressionCache = new HashMap<>(64);
    private boolean usePropertyConditionOptimizations = true;
    private static final ClassLoader secureFilteringClassLoader = new SecureFilteringClassLoader(PropertyConditionEvaluator.class.getClassLoader());
    private static final HardcodedPropertyAccessorRegistry hardcodedPropertyAccessorRegistry = new HardcodedPropertyAccessorRegistry();
    private ExpressionFilterFactory expressionFilterFactory;

    private final boolean useOGNLScripting = Boolean.parseBoolean(System.getProperty("org.apache.unomi.security.properties.useOGNLScripting", "false"));

    public void setUsePropertyConditionOptimizations(boolean usePropertyConditionOptimizations) {
        this.usePropertyConditionOptimizations = usePropertyConditionOptimizations;
    }

    public void setExpressionFilterFactory(ExpressionFilterFactory expressionFilterFactory) {
        this.expressionFilterFactory = expressionFilterFactory;
    }

    public void init() {
        if (!useOGNLScripting) {
            LOGGER.info("OGNL Script disabled, properties using OGNL won't be evaluated");
        }
    }

    private int compare(Object actualValue, String expectedValue, Object expectedValueDate, Object expectedValueInteger, Object expectedValueDateExpr, Object expectedValueDouble) {
        if (expectedValue == null && expectedValueDate == null && expectedValueInteger == null && getDate(expectedValueDateExpr) == null && expectedValueDouble == null) {
            return actualValue == null ? 0 : 1;
        } else if (actualValue == null) {
            return -1;
        }

        if (expectedValueInteger != null) {
            return PropertyHelper.getInteger(actualValue).compareTo(PropertyHelper.getInteger(expectedValueInteger));
        } else if (expectedValueDouble != null) {
            return PropertyHelper.getDouble(actualValue).compareTo(PropertyHelper.getDouble(expectedValueDouble));
        } else if (expectedValueDate != null) {
            return getDate(actualValue).compareTo(getDate(expectedValueDate));
        } else if (expectedValueDateExpr != null) {
            return getDate(actualValue).compareTo(getDate(expectedValueDateExpr));
        } else {
            // We use toLowerCase here to match the behavior of the analyzer configuration in the persistence configuration
            return actualValue.toString().toLowerCase().compareTo(expectedValue);
        }
    }

    private boolean compareValues(Object actualValue, Collection<?> expectedValues, Collection<?> expectedValuesInteger,  Collection<?> expectedValuesDouble,  Collection<?> expectedValuesDate, Collection<?> expectedValuesDateExpr, String op) {
        Collection<Object> expectedDateExpr = null;
        if (expectedValuesDateExpr != null) {
            expectedDateExpr = expectedValuesDateExpr.stream().map(PropertyConditionEvaluator::getDate).collect(Collectors.toList());
        }
        @SuppressWarnings("unchecked")
        Collection<?> expected = ObjectUtils.firstNonNull(expectedValues, expectedValuesDate, expectedValuesInteger, expectedValuesDouble, expectedDateExpr);
        if (actualValue == null) {
            return expected == null;
        } else if (expected == null) {
            return false;
        }

        Collection<Object> actual = ConditionContextHelper.foldToASCII(getValueSet(actualValue));

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
        Object expectedValueDouble = condition.getParameter("propertyValueDouble");
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
                    LOGGER.info("eval took {} ms for {} {}", time / 1000000L, item.getClass().getName(), name);
                }
            } catch (NullPointerException e) {
                // property not found
                actualValue = null;
            } catch (Exception e) {
                if (!(e instanceof OgnlException)
                        || (!StringUtils.startsWith(e.getMessage(),
                        "source is null for getProperty(null"))) {
                    LOGGER.warn("Error evaluating value for {} {}. See debug level for more information", item.getClass().getName(), name);
                    if (LOGGER.isDebugEnabled()) LOGGER.debug("Error evaluating value for {} {}", item.getClass().getName(), name, e);
                }
                actualValue = null;
            }
        }
        if (actualValue instanceof String) {
            actualValue = ConditionContextHelper.foldToASCII((String) actualValue);
        }

        return isMatch(op, actualValue, expectedValue, expectedValueInteger, expectedValueDouble, expectedValueDate,
                expectedValueDateExpr, condition);
    }

    protected boolean isMatch(String op, Object actualValue, String expectedValue, Object expectedValueInteger, Object expectedValueDouble,
                              Object expectedValueDate, Object expectedValueDateExpr, Condition condition) {
        if (op == null) {
            return false;
        } else if (actualValue == null) {
            return op.equals("missing") || op.equals("notIn") || op.equals("notEquals") || op.equals("hasNoneOf");
        } else if (op.equals("exists")) {
            if (actualValue instanceof List) {
                return ((List) actualValue).size() > 0;
            }
            return true;
        } else if (op.equals("equals")) {
            if (actualValue instanceof Collection) {
                for (Object o : ((Collection<?>) actualValue)) {
                    if (o instanceof String) {
                        o = ConditionContextHelper.foldToASCII((String) o);
                    }
                    if (compare(o, expectedValue, expectedValueDate, expectedValueInteger, expectedValueDateExpr, expectedValueDouble) == 0) {
                        return true;
                    }
                }
                return false;
            }
            return compare(actualValue, expectedValue, expectedValueDate, expectedValueInteger, expectedValueDateExpr, expectedValueDouble) == 0;
        } else if (op.equals("notEquals")) {
            return compare(actualValue, expectedValue, expectedValueDate, expectedValueInteger, expectedValueDateExpr, expectedValueDouble) != 0;
        } else if (op.equals("greaterThan")) {
            return compare(actualValue, expectedValue, expectedValueDate, expectedValueInteger, expectedValueDateExpr, expectedValueDouble) > 0;
        } else if (op.equals("greaterThanOrEqualTo")) {
            return compare(actualValue, expectedValue, expectedValueDate, expectedValueInteger, expectedValueDateExpr, expectedValueDouble) >= 0;
        } else if (op.equals("lessThan")) {
            return compare(actualValue, expectedValue, expectedValueDate, expectedValueInteger, expectedValueDateExpr, expectedValueDouble) < 0;
        } else if (op.equals("lessThanOrEqualTo")) {
            return compare(actualValue, expectedValue, expectedValueDate, expectedValueInteger, expectedValueDateExpr, expectedValueDouble) <= 0;
        } else if (op.equals("between")) {
            Collection<?> expectedValuesInteger = (Collection<?>) condition.getParameter("propertyValuesInteger");
            Collection<?> expectedValuesDouble = (Collection<?>) condition.getParameter("propertyValuesDouble");
            Collection<?> expectedValuesDate = (Collection<?>) condition.getParameter("propertyValuesDate");
            Collection<?> expectedValuesDateExpr = (Collection<?>) condition.getParameter("propertyValuesDateExpr");
            return compare(actualValue, null,
                    getDate(getFirst(expectedValuesDate)),
                    getFirst(expectedValuesInteger),
                    getFirst(expectedValuesDateExpr),
                    getFirst(expectedValuesDouble)) >= 0
                    &&
                    compare(actualValue, null,
                            getDate(getSecond(expectedValuesDate)),
                            getSecond(expectedValuesInteger),
                            getSecond(expectedValuesDateExpr),
                            getSecond(expectedValuesDouble)) <= 0;
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
            Collection<?> expectedValues = ConditionContextHelper.foldToASCII((Collection<?>) condition.getParameter("propertyValues"));
            Collection<?> expectedValuesInteger = (Collection<?>) condition.getParameter("propertyValuesInteger");
            Collection<?> expectedValuesDate = (Collection<?>) condition.getParameter("propertyValuesDate");
            Collection<?> expectedValuesDateExpr = (Collection<?>) condition.getParameter("propertyValuesDateExpr");
            Collection<?> expectedValuesDouble = (Collection<?>) condition.getParameter("propertyValuesDouble");

            return compareValues(actualValue, expectedValues, expectedValuesInteger, expectedValuesDouble, expectedValuesDate, expectedValuesDateExpr, op);
        } else if (op.equals("isDay") && (expectedValueDate != null || expectedValueDateExpr != null)) {
            Object expectedDate = expectedValueDate == null ? expectedValueDateExpr : expectedValueDate;
            return yearMonthDayDateFormat.format(getDate(actualValue)).equals(yearMonthDayDateFormat.format(getDate(expectedDate)));
        } else if (op.equals("isNotDay") && (expectedValueDate != null || expectedValueDateExpr != null)) {
            Object expectedDate = expectedValueDate == null ? expectedValueDateExpr : expectedValueDate;
            return !yearMonthDayDateFormat.format(getDate(actualValue)).equals(yearMonthDayDateFormat.format(getDate(expectedDate)));
        } else if (op.equals("distance")) {
            GeoPoint actualCenter = null;
            if (actualValue instanceof GeoPoint) {
                actualCenter = (GeoPoint) actualValue;
            } else if (actualValue instanceof Map) {
                actualCenter = GeoPoint.fromMap((Map<String, Double>) actualValue);
            } else if (actualValue instanceof String) {
                actualCenter = GeoPoint.fromString((String) actualValue);
            }
            if (actualCenter == null) {
                return false;
            }

            final String unitString = (String) condition.getParameter("unit");
            final String centerString = (String) condition.getParameter("center");
            final Double distance = (Double) condition.getParameter("distance");
            if (centerString == null || distance == null) {
                return false;
            }

            final GeoPoint expectedCenter = GeoPoint.fromString(centerString);
            final DistanceUnit expectedUnit = unitString != null ? DistanceUnit.fromString(unitString) : DistanceUnit.DEFAULT;
            final double distanceInMeters = expectedUnit.convert(distance, DistanceUnit.METERS);

            return expectedCenter.distanceTo(actualCenter) <= distanceInMeters;
        }
        return false;
    }

    protected Object getPropertyValue(Item item, String expression) throws Exception {
        if (usePropertyConditionOptimizations) {
            Object result = getHardcodedPropertyValue(item, expression);
            if (!HardcodedPropertyAccessor.PROPERTY_NOT_FOUND_MARKER.equals(result)) {
                return result;
            }
        }
        if (useOGNLScripting) {
            return getOGNLPropertyValue(item, expression);
        }
        return null;
    }

    protected Object getHardcodedPropertyValue(Item item, String expression) {
        // the following are optimizations to avoid using the expressions that are slower. The main objective here is
        // to avoid the most used expression that may also trigger calls to the Java Reflection API.
        return hardcodedPropertyAccessorRegistry.getProperty(item, expression);
    }

    protected Object getOGNLPropertyValue(Item item, String expression) throws Exception {
        if (expressionFilterFactory.getExpressionFilter("ognl").filter(expression) == null) {
            LOGGER.warn("OGNL expression filtered because not allowed on item: {}. See debug log level for more information", item.getClass().getName());
            LOGGER.debug("OGNL expression filtered because not allowed: {}", expression);
            return null;
        }
        OgnlContext ognlContext = getOgnlContext(secureFilteringClassLoader);
        ExpressionAccessor accessor = getPropertyAccessor(item, expression, ognlContext, secureFilteringClassLoader);
        if (accessor != null) {
            try {
                return accessor.get(ognlContext, item);
            } catch (Throwable t) {
                LOGGER.error("Error evaluating expression on item {}. See debug level for more information", item.getClass().getName());
                LOGGER.debug("Error evaluating expression {} on item {}.", expression, item.getClass().getName(), t);
                return null;
            }
        }
        return null;
    }

    private class ClassLoaderClassResolver extends DefaultClassResolver {
        private ClassLoader classLoader;

        public ClassLoaderClassResolver(ClassLoader classLoader) {
            this.classLoader = classLoader;
        }

        @Override
        protected Class toClassForName(String className) throws ClassNotFoundException {
            return Class.forName(className, true, classLoader);
        }
    }

    private OgnlContext getOgnlContext(ClassLoader classLoader) {
        return (OgnlContext) Ognl.createDefaultContext(null, new MemberAccess() {
                    @Override
                    public Object setup(OgnlContext ognlContext, Object target, Member member, String propertyName) {
                        return null;
                    }

                    @Override
                    public void restore(OgnlContext ognlContext, Object target, Member member, String propertyName, Object state) {
                    }

                    @Override
                    public boolean isAccessible(OgnlContext ognlContext, Object target, Member member, String propertyName) {
                        int modifiers = member.getModifiers();
                        boolean accessible = false;
                        if (target instanceof Item && !"getClass".equals(member.getName())) {
                            accessible = Modifier.isPublic(modifiers);
                        }
                        if (!accessible) {
                            LOGGER.warn("OGNL security filtered target, member for property. See debug log level for more information");
                            LOGGER.debug("OGNL security filtered: Target {} and member {} for property {}. Not allowed", target, member, propertyName);
                        }
                        return accessible;
                    }
                }, new ClassLoaderClassResolver(classLoader),
                null);
    }

    private ExpressionAccessor getPropertyAccessor(Item item, String expression, OgnlContext ognlContext, ClassLoader classLoader) throws Exception {
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
                current.setContextClassLoader(classLoader);
                Node node = Ognl.compileExpression(ognlContext, item, expression);
                accessor = node.getAccessor();
            } finally {
                current.setContextClassLoader(contextCL);
            }
            if (accessor != null) {
                expressions.put(expression, accessor);
            } else {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Unable to compile expression: {} for: {}", expression, clazz);
                } else {
                    LOGGER.warn("Unable to compile expression for {}. See debug log level for more information", clazz);
                }
            }
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Expression compilation for item={} expression={} took {}", item.getClass().getName(), expression, (System.nanoTime() - time) / 1000000L);
            } else {
                LOGGER.info("Expression compilation for item={} took {}. See debug log level for more information", item.getClass().getName(), (System.nanoTime() - time) / 1000000L);
            }
        }

        return accessor;
    }

    public static Date getDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Date) {
            return ((Date) value);
        } else {
            JodaDateMathParser parser = new JodaDateMathParser(Joda.forPattern("strictDateOptionalTime||epoch_millis"));
            try {
                return Date.from(parser.parse(value.toString(), System::currentTimeMillis));
            } catch (ElasticsearchParseException e) {
                LOGGER.warn("unable to parse date. See debug log level for full stacktrace");
                LOGGER.debug("unable to parse date {}", value, e);
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

    private Object getFirst(Collection<?> collection) {
        if (collection == null) {
            return null;
        }
        if (collection.isEmpty()) {
            return null;
        }
        Iterator<?> iterator = collection.iterator();
        if (iterator.hasNext()) {
            return iterator.next();
        }
        return null;
    }

    private Object getSecond(Collection<?> collection) {
        if (collection == null) {
            return null;
        }
        if (collection.isEmpty()) {
            return null;
        }
        Iterator<?> iterator = collection.iterator();
        if (iterator.hasNext()) {
            iterator.next();
            if (iterator.hasNext()) {
                return iterator.next();
            }
        }
        return null;
    }

}
