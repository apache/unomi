package org.oasis_open.wemi.context.server.api;

import org.oasis_open.wemi.context.server.api.conditions.Condition;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Created by loom on 24.04.14.
 */
@XmlRootElement
public class SegmentDefinition {

    SegmentID segmentID;

    Condition rootCondition;

    public SegmentDefinition() {
    }

    public SegmentDefinition(SegmentID segmentID) {
        this.segmentID = segmentID;
    }

    @XmlElement(name="metadata")
    public SegmentID getSegmentID() {
        return segmentID;
    }

    public void setSegmentID(SegmentID segmentID) {
        this.segmentID = segmentID;
    }

    @XmlElement(name="condition")
    public Condition getRootCondition() {
        return rootCondition;
    }

    public void setRootCondition(Condition rootCondition) {
        this.rootCondition = rootCondition;
    }

}
