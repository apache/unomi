package org.oasis_open.contextserver.api.campaigns;

import org.oasis_open.contextserver.api.Item;
import org.oasis_open.contextserver.api.Metadata;
import org.oasis_open.contextserver.api.conditions.Condition;

import java.util.Date;

public class Campaign extends Item {
    public static final String ITEM_TYPE = "campaign";

    private Metadata metadata;

    private Date startDate;

    private Date endDate;

    private Condition entryCondition;

    public Campaign() {
    }

    public Campaign(Metadata metadata) {
        super(metadata.getIdWithScope());
        this.metadata = metadata;
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        this.itemId = metadata.getIdWithScope();
        this.metadata = metadata;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    public Condition getEntryCondition() {
        return entryCondition;
    }

    public void setEntryCondition(Condition entryCondition) {
        this.entryCondition = entryCondition;
    }
}
