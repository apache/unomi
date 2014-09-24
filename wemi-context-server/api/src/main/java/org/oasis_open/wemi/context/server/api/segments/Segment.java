package org.oasis_open.wemi.context.server.api.segments;

import org.oasis_open.wemi.context.server.api.Item;
import org.oasis_open.wemi.context.server.api.Metadata;
import org.oasis_open.wemi.context.server.api.conditions.Condition;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Created by loom on 24.04.14.
 */
@XmlRootElement
public class Segment extends Item {

    public static final String ITEM_TYPE = "segment";

    private Metadata metadata;

    private Condition condition;

    public Segment() {
    }

    public Segment(Metadata metadata) {
        super(metadata.getId());
        this.metadata = metadata;
    }

    @XmlElement(name="metadata")
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

}
