package org.oasis_open.contextserver.api.campaigns;

/**
 * Created by kevan on 03/06/15.
 */
public class CampaignDetail {
    private long engagedProfiles = 0;
    private long campaignSessionViews = 0;
    private long campaignSessionSuccess = 0;
    private long numberOfGoals = 0;
    private double conversionRate;
    private Campaign campaign;

    public CampaignDetail(Campaign campaign) {
        this.campaign = campaign;
    }

    public long getEngagedProfiles() {
        return engagedProfiles;
    }

    public void setEngagedProfiles(long engagedProfiles) {
        this.engagedProfiles = engagedProfiles;
    }

    public long getNumberOfGoals() {
        return numberOfGoals;
    }

    public void setNumberOfGoals(long numberOfGoals) {
        this.numberOfGoals = numberOfGoals;
    }

    public double getConversionRate() {
        return conversionRate;
    }

    public void setConversionRate(double conversionRate) {
        this.conversionRate = conversionRate;
    }

    public Campaign getCampaign() {
        return campaign;
    }

    public void setCampaign(Campaign campaign) {
        this.campaign = campaign;
    }

    public long getCampaignSessionViews() {
        return campaignSessionViews;
    }

    public void setCampaignSessionViews(long campaignSessionViews) {
        this.campaignSessionViews = campaignSessionViews;
    }

    public long getCampaignSessionSuccess() {
        return campaignSessionSuccess;
    }

    public void setCampaignSessionSuccess(long campaignSessionSuccess) {
        this.campaignSessionSuccess = campaignSessionSuccess;
    }
}
