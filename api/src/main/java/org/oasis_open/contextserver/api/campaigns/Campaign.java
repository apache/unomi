package org.oasis_open.contextserver.api.campaigns;

import org.oasis_open.contextserver.api.Metadata;
import org.oasis_open.contextserver.api.MetadataItem;
import org.oasis_open.contextserver.api.conditions.Condition;

import java.util.Date;

public class Campaign extends MetadataItem {
    public static final String ITEM_TYPE = "campaign";

    private Date startDate;

    private Date endDate;

    private Condition entryCondition;

    private Double cost;

    private String currency;

    public Campaign() {
    }

    public Campaign(Metadata metadata) {
        super(metadata);
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

    public Double getCost() {
        return cost;
    }

    public void setCost(Double cost) {
        this.cost = cost;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}
