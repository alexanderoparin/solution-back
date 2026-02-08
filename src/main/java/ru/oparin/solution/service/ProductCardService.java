package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.wb.CardDto;
import ru.oparin.solution.dto.wb.CardsListResponse;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.ProductBarcode;
import ru.oparin.solution.model.ProductCard;
import ru.oparin.solution.model.User;
import ru.oparin.solution.repository.CabinetRepository;
import ru.oparin.solution.repository.ProductBarcodeRepository;
import ru.oparin.solution.repository.ProductCardRepository;

import java.util.List;
import java.util.Optional;

/**
 * Сервис для работы с карточками товаров.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductCardService {

    private final ProductCardRepository productCardRepository;
    private final ProductBarcodeRepository barcodeRepository;
    private final CabinetRepository cabinetRepository;

    /**
     * Сохраняет или обновляет карточки товаров из ответа WB API (кабинет по умолчанию для продавца).
     */
    @Transactional
    public void saveOrUpdateCards(CardsListResponse response, User seller) {
        if (isEmptyResponse(response)) {
            return;
        }
        Cabinet cabinet = cabinetRepository.findDefaultByUserId(seller.getId())
                .orElseThrow(() -> new IllegalStateException("У продавца нет кабинета по умолчанию"));
        saveOrUpdateCards(response, cabinet);
    }

    /**
     * Сохраняет или обновляет карточки товаров из ответа WB API для указанного кабинета.
     */
    @Transactional
    public void saveOrUpdateCards(CardsListResponse response, Cabinet cabinet) {
        if (isEmptyResponse(response)) {
            return;
        }
        ProcessingResult result = processCards(response.getCards(), cabinet.getUser(), cabinet);
        log.info("Обработано карточек: создано {}, обновлено {}",
                result.savedCount(), result.updatedCount());
    }

    private boolean isEmptyResponse(CardsListResponse response) {
        return response == null 
                || response.getCards() == null 
                || response.getCards().isEmpty();
    }

    private ProcessingResult processCards(List<CardDto> cards, User seller, Cabinet cabinet) {
        int savedCount = 0;
        int updatedCount = 0;

        for (CardDto cardDto : cards) {
            if (!isValidCard(cardDto)) {
                continue;
            }

            Optional<SaveResult> result = processCard(cardDto, seller, cabinet);
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

    private Optional<SaveResult> processCard(CardDto cardDto, User seller, Cabinet cabinet) {
        try {
            ProductCard card = mapToProductCard(cardDto, seller, cabinet);
            if (card == null) {
                return Optional.empty();
            }

            Optional<ProductCard> existingCard = productCardRepository.findByNmIdAndCabinet_Id(card.getNmId(), cabinet.getId());

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

        saveBarcodes(cardDto, existingCard.getCabinet());

        return Optional.of(new SaveResult(false));
    }

    /**
     * Сохраняет баркоды товара из карточки для кабинета.
     */
    private void saveBarcodes(CardDto cardDto, Cabinet cabinet) {
        if (cabinet == null || cardDto.getSizes() == null || cardDto.getSizes().isEmpty()) {
            return;
        }

        for (CardDto.Size size : cardDto.getSizes()) {
            if (size.getSkus() == null || size.getSkus().isEmpty()) {
                continue;
            }

            if (size.getChrtId() == null) {
                log.warn("Размер для товара nmID {} не имеет chrtID, пропускаем", cardDto.getNmId());
                continue;
            }

            for (String barcodeValue : size.getSkus()) {
                if (barcodeValue == null || barcodeValue.isEmpty()) {
                    continue;
                }

                Optional<ProductBarcode> existingBarcode = barcodeRepository.findByBarcodeAndCabinet_Id(barcodeValue, cabinet.getId());

                if (existingBarcode.isPresent()) {
                    ProductBarcode barcode = existingBarcode.get();
                    barcode.setNmId(cardDto.getNmId());
                    barcode.setChrtId(size.getChrtId());
                    barcode.setTechSize(size.getTechSize());
                    barcode.setWbSize(size.getWbSize());
                    barcodeRepository.save(barcode);
                } else {
                    ProductBarcode barcode = ProductBarcode.builder()
                            .nmId(cardDto.getNmId())
                            .cabinet(cabinet)
                            .chrtId(size.getChrtId())
                            .barcode(barcodeValue)
                            .techSize(size.getTechSize())
                            .wbSize(size.getWbSize())
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

        saveBarcodes(cardDto, card.getCabinet());

        return Optional.of(new SaveResult(true));
    }

    private ProductCard mapToProductCard(CardDto cardDto, User seller, Cabinet cabinet) {
        String photoTm = extractPhotoTm(cardDto);

        return ProductCard.builder()
                .nmId(cardDto.getNmId())
                .imtId(cardDto.getImtId())
                .seller(seller)
                .cabinet(cabinet)
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
