package org.oasis_open.wemi.context.server.impl.services;

import org.oasis_open.wemi.context.server.api.conditions.Condition;
import org.oasis_open.wemi.context.server.api.consequences.Consequence;
import org.oasis_open.wemi.context.server.impl.consequences.SetPropertyConsequence;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
* Created by toto on 26/06/14.
*/
class Rule {

    Rule() {
    }

    private Condition rootCondition;
    private Set<Consequence> consequences;

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
