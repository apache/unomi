package org.oasis_open.contextserver.api;

import javax.xml.bind.annotation.XmlTransient;
import java.io.Serializable;

/**
 * Created by loom on 24.04.14.
 */
public abstract class Item implements Serializable {

    protected String itemId;
    protected String itemType;
    protected String scope;

    public Item() {
        try {
            this.itemType = (String) this.getClass().getField("ITEM_TYPE").get(null);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            e.printStackTrace();
        }
    }

    public Item(String itemId) {
        this();
        this.itemId = itemId;
    }


    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public String getItemType() {
        return itemType;
    }

    public void setItemType(String itemType) {
        this.itemType = itemType;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
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
