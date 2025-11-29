package ru.oparin.solution.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import ru.oparin.solution.model.CampaignStatus;

/**
 * Конвертер для автоматического преобразования между {@link CampaignStatus} и {@link Integer}
 * при сохранении в базу данных.
 */
@Converter(autoApply = true)
public class CampaignStatusConverter implements AttributeConverter<CampaignStatus, Integer> {

    @Override
    public Integer convertToDatabaseColumn(CampaignStatus attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public CampaignStatus convertToEntityAttribute(Integer dbData) {
        return CampaignStatus.fromCode(dbData);
    }
}

