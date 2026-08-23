package ru.oparin.solution.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Составной ключ связи РК Ozon ↔ SKU.
 */
public class OzonCampaignArticleId implements Serializable {

    private Long campaignId;
    private Long sku;

    public OzonCampaignArticleId() {
    }

    public OzonCampaignArticleId(Long campaignId, Long sku) {
        this.campaignId = campaignId;
        this.sku = sku;
    }

    public Long getCampaignId() {
        return campaignId;
    }

    public void setCampaignId(Long campaignId) {
        this.campaignId = campaignId;
    }

    public Long getSku() {
        return sku;
    }

    public void setSku(Long sku) {
        this.sku = sku;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OzonCampaignArticleId that)) {
            return false;
        }
        return Objects.equals(campaignId, that.campaignId) && Objects.equals(sku, that.sku);
    }

    @Override
    public int hashCode() {
        return Objects.hash(campaignId, sku);
    }
}
