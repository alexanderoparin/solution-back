package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.wb.CardDto;
import ru.oparin.solution.dto.wb.CardsListResponse;
import ru.oparin.solution.model.ProductBarcode;
import ru.oparin.solution.model.ProductCard;
import ru.oparin.solution.model.User;
import ru.oparin.solution.repository.ProductBarcodeRepository;
import ru.oparin.solution.repository.ProductCardRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Сервис для работы с карточками товаров.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductCardService {

    private final ProductCardRepository productCardRepository;
    private final ProductBarcodeRepository barcodeRepository;

    /**
     * Сохраняет или обновляет карточки товаров из ответа WB API.
     */
    @Transactional
    public void saveOrUpdateCards(CardsListResponse response, User seller) {
        if (isEmptyResponse(response)) {
            return;
        }

        ProcessingResult result = processCards(response.getCards(), seller);
        
        log.info("Обработано карточек: создано {}, обновлено {}", 
                result.savedCount(), result.updatedCount());
    }

    private boolean isEmptyResponse(CardsListResponse response) {
        return response == null 
                || response.getCards() == null 
                || response.getCards().isEmpty();
    }

    private ProcessingResult processCards(List<CardDto> cards, User seller) {
        int savedCount = 0;
        int updatedCount = 0;

        for (CardDto cardDto : cards) {
            if (!isValidCard(cardDto)) {
                continue;
            }

            Optional<SaveResult> result = processCard(cardDto, seller);
            if (result.isPresent()) {
                if (result.get().isNew()) {
                    savedCount++;
                } else {
                    updatedCount++;
                }
            }
        }

        return new ProcessingResult(savedCount, updatedCount);
    }

    private boolean isValidCard(CardDto cardDto) {
        return cardDto != null && cardDto.getNmId() != null;
    }

    private Optional<SaveResult> processCard(CardDto cardDto, User seller) {
        try {
            ProductCard card = mapToProductCard(cardDto, seller);
            if (card == null) {
                return Optional.empty();
            }

            Optional<ProductCard> existingCard = productCardRepository.findByNmId(card.getNmId());

            if (existingCard.isPresent()) {
                return handleExistingCard(existingCard.get(), card, seller, cardDto);
            } else {
                return handleNewCard(card, cardDto);
            }

        } catch (Exception e) {
            log.error("Ошибка при обработке карточки nmID {}: {}", 
                    cardDto.getNmId(), e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<SaveResult> handleExistingCard(
            ProductCard existingCard, 
            ProductCard updatedCard, 
            User seller,
            CardDto cardDto
    ) {
        if (!isCardBelongsToSeller(existingCard, seller)) {
            log.warn("Карточка с nmID {} принадлежит другому продавцу, пропускаем", 
                    existingCard.getNmId());
            return Optional.empty();
        }

        updateCardFields(existingCard, updatedCard);
        productCardRepository.save(existingCard);
        
        // Обновляем баркоды
        saveBarcodes(cardDto);
        
        return Optional.of(new SaveResult(false));
    }

    /**
     * Сохраняет баркоды товара из карточки.
     */
    private void saveBarcodes(CardDto cardDto) {
        if (cardDto.getSizes() == null || cardDto.getSizes().isEmpty()) {
            return;
        }

        // Удаляем старые баркоды для этого товара
        barcodeRepository.deleteByNmId(cardDto.getNmId());

        // Используем Set для отслеживания уже обработанных SKU в рамках одного товара
        // (на случай, если один и тот же SKU встречается несколько раз в разных размерах)
        Set<String> processedSkus = new HashSet<>();

        // Сохраняем новые баркоды
        for (CardDto.Size size : cardDto.getSizes()) {
            if (size.getSkus() == null || size.getSkus().isEmpty()) {
                continue;
            }

            for (String sku : size.getSkus()) {
                if (sku == null || sku.isEmpty()) {
                    continue;
                }

                // Пропускаем дубликаты SKU в рамках одного товара
                if (processedSkus.contains(sku)) {
                    log.debug("Пропущен дубликат SKU {} для товара nmID {}", sku, cardDto.getNmId());
                    continue;
                }

                processedSkus.add(sku);

                // Проверяем, существует ли уже такой баркод (на случай проблем с транзакцией)
                Optional<ProductBarcode> existingBarcode = barcodeRepository
                        .findByNmIdAndSku(cardDto.getNmId(), sku);

                if (existingBarcode.isPresent()) {
                    // Обновляем существующий баркод
                    ProductBarcode barcode = existingBarcode.get();
                    barcode.setChrtId(size.getChrtId());
                    barcode.setTechSize(size.getTechSize());
                    barcodeRepository.save(barcode);
                } else {
                    // Создаем новый баркод
                    ProductBarcode barcode = ProductBarcode.builder()
                            .nmId(cardDto.getNmId())
                            .chrtId(size.getChrtId())
                            .sku(sku)
                            .techSize(size.getTechSize())
                            .build();
                    barcodeRepository.save(barcode);
                }
            }
        }
    }

    private boolean isCardBelongsToSeller(ProductCard card, User seller) {
        return card.getSeller().getId().equals(seller.getId());
    }

    private Optional<SaveResult> handleNewCard(ProductCard card, CardDto cardDto) {
        productCardRepository.save(card);
        
        // Сохраняем баркоды
        saveBarcodes(cardDto);
        
        return Optional.of(new SaveResult(true));
    }

    private ProductCard mapToProductCard(CardDto cardDto, User seller) {
        String photoTm = extractPhotoTm(cardDto);

        return ProductCard.builder()
                .nmId(cardDto.getNmId())
                .imtId(cardDto.getImtId())
                .seller(seller)
                .title(cardDto.getTitle())
                .subjectName(cardDto.getSubjectName())
                .brand(cardDto.getBrand())
                .vendorCode(cardDto.getVendorCode())
                .photoTm(photoTm)
                .build();
    }

    private String extractPhotoTm(CardDto cardDto) {
        if (cardDto.getPhotos() == null || cardDto.getPhotos().isEmpty()) {
            return null;
        }

        CardDto.Photo firstPhoto = cardDto.getPhotos().get(0);
        return firstPhoto != null ? firstPhoto.getTm() : null;
    }

    private void updateCardFields(ProductCard existing, ProductCard updated) {
        existing.setImtId(updated.getImtId());
        existing.setTitle(updated.getTitle());
        existing.setSubjectName(updated.getSubjectName());
        existing.setBrand(updated.getBrand());
        existing.setVendorCode(updated.getVendorCode());
        existing.setPhotoTm(updated.getPhotoTm());
    }

    private record ProcessingResult(int savedCount, int updatedCount) {
    }

    private record SaveResult(boolean isNew) {
    }
}
