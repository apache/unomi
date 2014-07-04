package org.oasis_open.wemi.context.server.api;

import org.oasis_open.wemi.context.server.api.conditions.Condition;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Created by loom on 24.04.14.
 */
@XmlRootElement
public class SegmentDefinition {

    Condition rootCondition;

    public SegmentDefinition() {
    }

    @XmlElement(name="condition")
    public Condition getRootCondition() {
        return rootCondition;
    }

    public void setRootCondition(Condition rootCondition) {
        this.rootCondition = rootCondition;
    }

}
