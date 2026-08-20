package ru.oparin.solution.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import ru.oparin.solution.model.WbCampaignStatus;

/**
 * Конвертер для автоматического преобразования между {@link WbCampaignStatus} и {@link Integer}
 * при сохранении в базу данных.
 */
@Converter(autoApply = true)
public class WbCampaignStatusConverter implements AttributeConverter<WbCampaignStatus, Integer> {

    @Override
    public Integer convertToDatabaseColumn(WbCampaignStatus attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public WbCampaignStatus convertToEntityAttribute(Integer dbData) {
        return WbCampaignStatus.fromCode(dbData);
    }
}

