package org.oasis_open.contextserver.api;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;

public abstract class MetadataItem extends Item {
    protected Metadata metadata;

    public MetadataItem() {
    }

    public MetadataItem(Metadata metadata) {
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

    @XmlTransient
    public String getScope() {
        return metadata.getScope();
    }

}
