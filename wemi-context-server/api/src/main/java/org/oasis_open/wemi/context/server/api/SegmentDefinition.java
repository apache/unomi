package org.oasis_open.wemi.context.server.api;

import org.oasis_open.wemi.context.server.api.conditions.Condition;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Created by loom on 24.04.14.
 */
@XmlRootElement
public class SegmentDefinition {

    Metadata metadata;

    Condition rootCondition;

    public SegmentDefinition() {
    }

    public SegmentDefinition(Metadata metadata) {
        this.metadata = metadata;
    }

    @XmlElement(name="metadata")
    public Metadata getMetadata() {
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    @XmlElement(name="condition")
    public Condition getRootCondition() {
        return rootCondition;
    }

    public void setRootCondition(Condition rootCondition) {
        this.rootCondition = rootCondition;
    }

}
