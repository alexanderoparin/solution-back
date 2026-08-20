package ru.oparin.solution.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import ru.oparin.solution.model.WbCampaignType;

/**
 * Конвертер для преобразования WbCampaignType enum в Integer для хранения в БД
 * и обратно при чтении из БД.
 */
@Converter(autoApply = true)
public class WbCampaignTypeConverter implements AttributeConverter<WbCampaignType, Integer> {

    /**
     * Преобразует enum в числовой код для сохранения в БД.
     *
     * @param campaignType enum тип кампании
     * @return числовой код типа кампании или null
     */
    @Override
    public Integer convertToDatabaseColumn(WbCampaignType campaignType) {
        if (campaignType == null) {
            return null;
        }
        return campaignType.getCode();
    }

    /**
     * Преобразует числовой код из БД в enum.
     *
     * @param code числовой код типа кампании
     * @return enum тип кампании или null
     */
    @Override
    public WbCampaignType convertToEntityAttribute(Integer code) {
        if (code == null) {
            return null;
        }
        return WbCampaignType.fromCode(code);
    }
}

