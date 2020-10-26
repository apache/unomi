package org.apache.unomi.api.conditions;


public interface ConditionHook {

    void executeHook(Condition condition);
}
