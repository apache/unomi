package org.oasis_open.wemi.context.server.api;

import javax.xml.bind.annotation.XmlTransient;
import java.io.Serializable;

/**
 * Created by loom on 24.04.14.
 */
public abstract class Item implements Serializable {

    protected String itemId;

    public Item() {
    }

    public Item(String itemId) {
        this(itemId, null);
    }

    public Item(String itemId, String parentId) {
        this.itemId = itemId;
    }

    @XmlTransient
    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Item item = (Item) o;

        if (itemId != null ? !itemId.equals(item.itemId) : item.itemId != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return itemId != null ? itemId.hashCode() : 0;
    }
}
