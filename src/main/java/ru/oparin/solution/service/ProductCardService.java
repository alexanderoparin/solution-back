package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.wb.CardDto;
import ru.oparin.solution.dto.wb.CardsListResponse;
import ru.oparin.solution.model.ProductCard;
import ru.oparin.solution.model.User;
import ru.oparin.solution.repository.ProductCardRepository;

/**
 * Сервис для работы с карточками товаров.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductCardService {

    private final ProductCardRepository productCardRepository;

    /**
     * Сохраняет или обновляет карточки товаров из ответа WB API.
     *
     * @param response ответ от WB API со списком карточек
     * @param seller продавец, владелец карточек
     */
    @Transactional
    public void saveOrUpdateCards(CardsListResponse response, User seller) {
        if (response == null || response.getCards() == null || response.getCards().isEmpty()) {
            return;
        }

        int savedCount = 0;
        int updatedCount = 0;

        for (CardDto cardDto : response.getCards()) {
            if (cardDto == null || cardDto.getNmId() == null) {
                continue;
            }

            ProductCard card = mapToProductCard(cardDto, seller);
            if (card == null) {
                continue;
            }

            Long nmId = card.getNmId();
            ProductCard existingCard = productCardRepository.findByNmId(nmId)
                    .orElse(null);

            if (existingCard != null) {
                if (!existingCard.getSeller().getId().equals(seller.getId())) {
                    log.warn("Карточка с nmID {} принадлежит другому продавцу, пропускаем", nmId);
                    continue;
                }
                updateCard(existingCard, card);
                productCardRepository.save(existingCard);
                updatedCount++;
            } else {
                productCardRepository.save(card);
                savedCount++;
            }
        }

        log.info("Обработано карточек: создано {}, обновлено {}", savedCount, updatedCount);
    }

    /**
     * Преобразует DTO карточки в сущность ProductCard.
     */
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

    /**
     * Извлекает URL миниатюры из массива photos.
     */
    private String extractPhotoTm(CardDto cardDto) {
        if (cardDto.getPhotos() == null || cardDto.getPhotos().isEmpty()) {
            return null;
        }

        CardDto.Photo firstPhoto = cardDto.getPhotos().get(0);
        return firstPhoto != null ? firstPhoto.getTm() : null;
    }

    /**
     * Обновляет существующую карточку данными из новой карточки.
     */
    private void updateCard(ProductCard existing, ProductCard updated) {
        existing.setImtId(updated.getImtId());
        existing.setTitle(updated.getTitle());
        existing.setSubjectName(updated.getSubjectName());
        existing.setBrand(updated.getBrand());
        existing.setVendorCode(updated.getVendorCode());
        existing.setPhotoTm(updated.getPhotoTm());
    }
}

