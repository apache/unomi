package org.oasis_open.contextserver.itests;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.oasis_open.contextserver.api.conditions.Condition;
import org.oasis_open.contextserver.api.services.DefinitionsService;

/**
 * Utility class for building conditions
 * 
 * @author Sergiy Shyrkov
 */
public class ConditionBuilder {

    public abstract class ComparisonCondition extends ConditionItem {

        ComparisonCondition(String conditionTypeId, DefinitionsService definitionsService) {
            super(conditionTypeId, definitionsService);
        }

        public ComparisonCondition all(List<?> values) {
            return op("all").values(values);
        }

        public ComparisonCondition all(Object... values) {
            return op("all").values(values);
        }

        public ComparisonCondition contains(String value) {
            return op("contains").value(value);
        }

        public ComparisonCondition endsWith(String value) {
            return op("endsWith").value(value);
        }

        public ComparisonCondition equalTo(Object value) {
            return op("equals").value(value);
        }

        public ComparisonCondition exists() {
            return op("exists");
        }

        public ComparisonCondition greaterThan(Date value) {
            return op("greaterThan").value(value);
        }

        public ComparisonCondition greaterThan(Double value) {
            return op("greaterThan").value(value);
        }

        public ComparisonCondition greaterThan(Integer value) {
            return op("greaterThan").value(value);
        }

        public ComparisonCondition greaterThan(Long value) {
            return op("greaterThan").value(value);
        }

        public ComparisonCondition greaterThanOrEqualTo(Date value) {
            return op("greaterThanOrEqualTo").value(value);
        }

        public ComparisonCondition greaterThanOrEqualTo(Double value) {
            return op("greaterThanOrEqualTo").value(value);
        }

        public ComparisonCondition greaterThanOrEqualTo(Integer value) {
            return op("greaterThanOrEqualTo").value(value);
        }

        public ComparisonCondition greaterThanOrEqualTo(Long value) {
            return op("greaterThanOrEqualTo").value(value);
        }

        public ComparisonCondition in(List<?> values) {
            return op("in").values(values);
        }

        public ComparisonCondition in(Object... values) {
            return op("in").values(values);
        }

        public ComparisonCondition lessThan(Date value) {
            return op("lessThan").value(value);
        }

        public ComparisonCondition lessThan(Double value) {
            return op("lessThan").value(value);
        }

        public ComparisonCondition lessThan(Integer value) {
            return op("lessThan").value(value);
        }

        public ComparisonCondition lessThan(Long value) {
            return op("lessThan").value(value);
        }

        public ComparisonCondition lessThanOrEqualTo(Date value) {
            return op("lessThanOrEqualTo").value(value);
        }

        public ComparisonCondition lessThanOrEqualTo(Double value) {
            return op("lessThanOrEqualTo").value(value);
        }

        public ComparisonCondition lessThanOrEqualTo(Integer value) {
            return op("lessThanOrEqualTo").value(value);
        }

        public ComparisonCondition lessThanOrEqualTo(Long value) {
            return op("lessThanOrEqualTo").value(value);
        }

        public ComparisonCondition matchesRegex(String value) {
            return op("matchesRegex").value(value);
        }

        public ComparisonCondition missing() {
            return op("missing");
        }

        public ComparisonCondition notEqualTo(Object value) {
            return op("notEquals").value(value);
        }

        public ComparisonCondition notIn(List<?> values) {
            return op("notIn").values(values);
        }

        public ComparisonCondition notIn(Object... values) {
            return op("notIn").values(values);
        }

        private ComparisonCondition op(String op) {
            return parameter("comparisonOperator", op);
        }

        @Override
        public ComparisonCondition parameter(String name, Object value) {
            return (ComparisonCondition) super.parameter(name, value);
        }

        public ComparisonCondition parameter(String name, Object... values) {
            return (ComparisonCondition) super.parameter(name, values);
        }

        public ComparisonCondition startsWith(String value) {
            return op("startsWith").value(value);
        }

        private ComparisonCondition value(Object value) {
            return parameter("propertyValue", value);
        }

        private ComparisonCondition values(List<?> values) {
            return parameter("propertyValues", values);
        }

        private ComparisonCondition values(Object... values) {
            return parameter("propertyValues", values != null ? Arrays.asList(values) : null);
        }
    }

    public class CompoundCondition extends ConditionItem {

        CompoundCondition(ConditionItem condition1, ConditionItem condition2, String operator) {
            super("booleanCondition", condition1.definitionsService);
            parameter("operator", operator);
            ArrayList<Condition> subConditions = new ArrayList<Condition>(2);
            subConditions.add(condition1.build());
            subConditions.add(condition2.build());
            parameter("subConditions", subConditions);
        }
    }

    public abstract class ConditionItem {

        protected Condition condition;

        private DefinitionsService definitionsService;

        ConditionItem(String conditionTypeId, DefinitionsService definitionsService) {
            this.definitionsService = definitionsService;
            condition = new org.oasis_open.contextserver.api.conditions.Condition(
                    this.definitionsService.getConditionType(conditionTypeId));
        }

        public Condition build() {
            return condition;
        }

        public ConditionItem parameter(String name, Object value) {
            condition.setParameter(name, value);
            return this;
        }
        
        public ConditionItem parameter(String name, Object... values) {
            condition.setParameter(name, values != null ? Arrays.asList(values) : null);
            return this;
        }

    }

    public class NotCondition extends ConditionItem {

        NotCondition(ConditionItem subCondition) {
            super("notCondition", subCondition.definitionsService);
            parameter("subCondition", subCondition.build());
        }
    }

    public class PropertyCondition extends ComparisonCondition {

        PropertyCondition(String conditionTypeId, String propertyName, DefinitionsService definitionsService) {
            super(conditionTypeId, definitionsService);
            condition.setParameter("propertyName", propertyName);
        }

    }

    private DefinitionsService definitionsService;

    /**
     * Initializes an instance of this class.
     * 
     * @param definitionsService
     *            an instance of the {@link DefinitionsService}
     */
    public ConditionBuilder(DefinitionsService definitionsService) {
        super();
        this.definitionsService = definitionsService;
    }

    public CompoundCondition and(ConditionItem condition1, ConditionItem condition2) {
        return new CompoundCondition(condition1, condition2, "and");
    }

    public NotCondition not(ConditionItem subCondition) {
        return new NotCondition(subCondition);
    }

    public CompoundCondition or(ConditionItem condition1, ConditionItem condition2) {
        return new CompoundCondition(condition1, condition2, "or");
    }

    public PropertyCondition profileProperty(String propertyName) {
        return new PropertyCondition("profilePropertyCondition", propertyName, definitionsService);
    }

    public PropertyCondition property(String conditionTypeId, String propertyName) {
        return new PropertyCondition(conditionTypeId, propertyName, definitionsService);
    }
}
