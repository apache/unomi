package org.oasis_open.wemi.context.server.persistence.elasticsearch.conditions;

import org.apache.commons.beanutils.BeanUtils;
import org.oasis_open.wemi.context.server.api.Item;
import org.oasis_open.wemi.context.server.api.conditions.Condition;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Created by toto on 14/08/14.
 */
public class MultipleValuesPropertyMatchConditionEvaluator implements ConditionEvaluator {

    public MultipleValuesPropertyMatchConditionEvaluator() {
    }


    @Override
    public boolean eval(Condition condition, Item item, Map<String, Object> context, ConditionEvaluatorDispatcher dispatcher) {
        String name = (String) condition.getParameterValues().get("propertyName");
        String matchType = (String) condition.getParameterValues().get("matchType");
        List<String> expectedValues = (List<String>) condition.getParameterValues().get("propertyValues");
        try {
            List<String> actualValue = Arrays.asList(BeanUtils.getArrayProperty(item, name));

            if (matchType != null) {
                if (matchType.equals("some")) {
                    for (String expectedValue : expectedValues) {
                        if (actualValue.contains(expectedValue)) {
                            return true;
                        }
                    }
                } else if (matchType.equals("none")) {
                    for (String expectedValue : expectedValues) {
                        if (actualValue.contains(expectedValue)) {
                            return false;
                        }
                    }
                }
            }
            return actualValue.containsAll(expectedValues);
        } catch (UnsupportedOperationException e) {
            throw e;
        } catch (Exception e) {
            throw new UnsupportedOperationException(e);
        }
    }

}
