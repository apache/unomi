package org.oasis_open.wemi.context.server.api.rules;

import org.oasis_open.wemi.context.server.api.conditions.Condition;
import org.oasis_open.wemi.context.server.api.consequences.Consequence;

import java.util.Set;

/**
* Created by toto on 26/06/14.
*/
public class Rule {

    private Condition rootCondition;
    private Set<Consequence> consequences;

    public Rule() {
    }

    public Condition getRootCondition() {
        return rootCondition;
    }

    public void setRootCondition(Condition rootCondition) {
        this.rootCondition = rootCondition;
    }

    public Set<Consequence> getConsequences() {
        return consequences;
    }

    public void setConsequences(Set<Consequence> consequences) {
        this.consequences = consequences;
    }

}
