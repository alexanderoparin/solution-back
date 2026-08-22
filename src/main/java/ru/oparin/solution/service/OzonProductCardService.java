package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.ozon.OzonProductInfoListResponse;
import ru.oparin.solution.dto.ozon.OzonProductListResponse;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.OzonProductCard;
import ru.oparin.solution.repository.OzonProductCardRepository;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Сохранение и чтение карточек товаров Ozon.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OzonProductCardService {

    private final OzonProductCardRepository productCardRepository;

    @Transactional(readOnly = true)
    public List<OzonProductCard> findByCabinetId(Long cabinetId) {
        return productCardRepository.findByCabinet_IdOrderByProductIdAsc(cabinetId);
    }

    /**
     * Сохраняет или обновляет карточки по списку товаров и детальной информации.
     */
    @Transactional
    public void saveOrUpdateProducts(
            Cabinet cabinet,
            OzonProductListResponse listResponse,
            OzonProductInfoListResponse infoResponse
    ) {
        if (listResponse == null || listResponse.getResult() == null
                || listResponse.getResult().getItems() == null
                || listResponse.getResult().getItems().isEmpty()) {
            return;
        }
        Map<Long, OzonProductInfoListResponse.Item> infoByProductId = infoResponse != null && infoResponse.getItems() != null
                ? infoResponse.getItems().stream()
                .filter(item -> item.getId() != null)
                .collect(Collectors.toMap(OzonProductInfoListResponse.Item::getId, Function.identity(), (a, b) -> a))
                : Map.of();

        int created = 0;
        int updated = 0;
        for (OzonProductListResponse.Item item : listResponse.getResult().getItems()) {
            if (item.getProductId() == null) {
                continue;
            }
            OzonProductInfoListResponse.Item info = infoByProductId.get(item.getProductId());
            OzonProductCard card = productCardRepository
                    .findByCabinet_IdAndProductId(cabinet.getId(), item.getProductId())
                    .orElseGet(() -> OzonProductCard.builder()
                            .cabinet(cabinet)
                            .productId(item.getProductId())
                            .build());
            boolean isNew = card.getId() == null;
            card.setOfferId(firstNotBlank(info != null ? info.getOfferId() : null, item.getOfferId()));
            if (info != null) {
                card.setTitle(trimTo(info.getName(), 500));
                card.setSku(info.getSku());
                card.setPhotoUrl(firstNotBlank(info.getPrimaryImage(), firstImage(info.getImages())));
            }
            productCardRepository.save(card);
            if (isNew) {
                created++;
            } else {
                updated++;
            }
        }
        log.info("Ozon каталог cabinetId={}: создано {}, обновлено {}", cabinet.getId(), created, updated);
    }

    private static String firstNotBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String firstImage(List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        return images.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isBlank()).findFirst().orElse(null);
    }

    private static String trimTo(String value, int maxLen) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLen ? trimmed : trimmed.substring(0, maxLen);
    }
}
