package ru.oparin.solution.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serializable;

/**
 * Составной ключ для WbCampaignArticle.
 */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class WbCampaignArticleId implements Serializable {

    private Long campaignId;
    private Long nmId;
}

