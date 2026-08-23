package ru.oparin.solution.dto.ozon;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Ответ GET /api/client/campaign Ozon Performance API.
 * Поле {@code list} может быть массивом или одним объектом кампании.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class OzonPerformanceCampaignListResponse {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private JsonNode list;
    private String total;

    /**
     * Распаковывает {@code list} в плоский список кампаний.
     */
    public List<Item> resolveItems() {
        if (list == null || list.isNull()) {
            return List.of();
        }
        if (list.isArray()) {
            return MAPPER.convertValue(list, new TypeReference<>() {
            });
        }
        if (list.isObject()) {
            Item item = MAPPER.convertValue(list, Item.class);
            List<Item> single = new ArrayList<>(1);
            single.add(item);
            return single;
        }
        return List.of();
    }

    /**
     * Элемент списка кампаний.
     */
    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        private Long id;
        private String title;
        private String state;
        private String advObjectType;
        private String paymentType;
        private Long dailyBudget;
        private Long budget;
        private LocalDate fromDate;
        private LocalDate toDate;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
