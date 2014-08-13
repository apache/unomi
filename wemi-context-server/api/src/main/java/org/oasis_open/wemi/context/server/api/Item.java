package org.oasis_open.wemi.context.server.api;

import javax.xml.bind.annotation.XmlTransient;
import java.io.Serializable;
import java.util.Properties;

/**
 * Created by loom on 24.04.14.
 */
public abstract class Item implements Serializable {

    protected String itemId;
    protected String parentId;

    public Item() {
    }

    public Item(String itemId) {
        this(itemId, null);
    }

    public Item(String itemId, String parentId) {
        this.itemId = itemId;
        this.parentId = parentId;
    }

    @XmlTransient
    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    @XmlTransient
    public String getParentId() {
        return parentId;
    }


    public void setParentId(String parentId) {
        this.parentId = parentId;
    }
}
