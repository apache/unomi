package org.oasis_open.wemi.context.server.api.segments;

import org.oasis_open.wemi.context.server.api.Item;
import org.oasis_open.wemi.context.server.api.Metadata;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;

/**
 * Created by toto on 10/12/14.
 */
@XmlRootElement
public class Scoring extends Item {
    public static final String ITEM_TYPE = "scoring";

    private Metadata metadata;

    private List<ScoringElement> elements;

    public Scoring() {
    }

    public Scoring(Metadata metadata) {
        super(metadata.getIdWithScope());
        this.metadata = metadata;
    }

    @XmlElement(name="metadata")
    public Metadata getMetadata() {
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        this.itemId = metadata.getIdWithScope();
        this.metadata = metadata;
    }

    public List<ScoringElement> getElements() {
        return elements;
    }

    public void setElements(List<ScoringElement> elements) {
        this.elements = elements;
    }

}
