package ru.oparin.solution.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Составной ключ для CampaignArticle.
 */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CampaignArticleId implements Serializable {

    private Long campaignId;
    private Long nmId;
}

