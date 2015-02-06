package org.oasis_open.contextserver.api.segments;

import org.oasis_open.contextserver.api.Metadata;
import org.oasis_open.contextserver.api.MetadataItem;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;

/**
 * Created by toto on 10/12/14.
 */
@XmlRootElement
public class Scoring extends MetadataItem {
    public static final String ITEM_TYPE = "scoring";

    private List<ScoringElement> elements;

    public Scoring() {
    }

    public Scoring(Metadata metadata) {
        super(metadata);
    }

    public List<ScoringElement> getElements() {
        return elements;
    }

    public void setElements(List<ScoringElement> elements) {
        this.elements = elements;
    }

}
