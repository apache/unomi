package org.oasis_open.wemi.context.server.api.rules;

import org.oasis_open.wemi.context.server.api.Item;
import org.oasis_open.wemi.context.server.api.Metadata;
import org.oasis_open.wemi.context.server.api.conditions.Condition;
import org.oasis_open.wemi.context.server.api.consequences.Consequence;

import javax.xml.bind.annotation.XmlElement;
import java.util.List;

/**
* Created by toto on 26/06/14.
*/
public class Rule extends Item {

    public static final String ITEM_TYPE = "rule";

    private Metadata metadata;

    private Condition condition;

    private List<Consequence> consequences;

    public Rule() {
    }

    public Rule(Metadata metadata) {
        super(metadata.getId());
        this.metadata = metadata;
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        this.itemId = metadata.getId();
        this.metadata = metadata;
    }

    public Condition getCondition() {
        return condition;
    }

    public void setCondition(Condition condition) {
        this.condition = condition;
    }

    public List<Consequence> getConsequences() {
        return consequences;
    }

    public void setConsequences(List<Consequence> consequences) {
        this.consequences = consequences;
    }

}
