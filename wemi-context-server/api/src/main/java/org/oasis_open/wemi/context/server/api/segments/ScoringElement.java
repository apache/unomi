package org.oasis_open.wemi.context.server.api.segments;

import org.oasis_open.wemi.context.server.api.conditions.Condition;

import javax.xml.bind.annotation.XmlRootElement;

/**
* Created by toto on 10/12/14.
*/
@XmlRootElement
public class ScoringElement {
    private Condition condition;
    private int value;

    public ScoringElement() {
    }

    public Condition getCondition() {
        return condition;
    }

    public void setCondition(Condition condition) {
        this.condition = condition;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }
}
