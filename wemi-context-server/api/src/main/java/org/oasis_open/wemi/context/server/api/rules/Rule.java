package org.oasis_open.wemi.context.server.api.rules;

import org.oasis_open.wemi.context.server.api.Metadata;
import org.oasis_open.wemi.context.server.api.conditions.Condition;
import org.oasis_open.wemi.context.server.api.consequences.Consequence;

import javax.xml.bind.annotation.XmlElement;
import java.util.List;

/**
* Created by toto on 26/06/14.
*/
public class Rule {

    private Metadata metadata;

    @XmlElement(name="condition")
    private Condition rootCondition;

    private List<Consequence> consequences;

    public Rule() {
    }

    public Rule(Metadata metadata) {
        this.metadata = metadata;
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    public Condition getRootCondition() {
        return rootCondition;
    }

    public void setRootCondition(Condition rootCondition) {
        this.rootCondition = rootCondition;
    }

    public List<Consequence> getConsequences() {
        return consequences;
    }

    public void setConsequences(List<Consequence> consequences) {
        this.consequences = consequences;
    }

}
