package org.oasis_open.contextserver.api.segments;

import org.oasis_open.contextserver.api.Metadata;
import org.oasis_open.contextserver.api.MetadataItem;
import org.oasis_open.contextserver.api.conditions.Condition;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class Segment extends MetadataItem {

    public static final String ITEM_TYPE = "segment";

    private Condition condition;

    public Segment() {
    }

    public Segment(Metadata metadata) {
        super(metadata);
    }

    public Condition getCondition() {
        return condition;
    }

    public void setCondition(Condition condition) {
        this.condition = condition;
    }

}
