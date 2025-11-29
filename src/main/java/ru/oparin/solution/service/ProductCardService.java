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
                return handleExistingCard(existingCard.get(), card, seller);
            } else {
                return handleNewCard(card);
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
            User seller
    ) {
        if (!isCardBelongsToSeller(existingCard, seller)) {
            log.warn("Карточка с nmID {} принадлежит другому продавцу, пропускаем", 
                    existingCard.getNmId());
            return Optional.empty();
        }

        updateCardFields(existingCard, updatedCard);
        productCardRepository.save(existingCard);
        
        return Optional.of(new SaveResult(false));
    }

    private boolean isCardBelongsToSeller(ProductCard card, User seller) {
        return card.getSeller().getId().equals(seller.getId());
    }

    private Optional<SaveResult> handleNewCard(ProductCard card) {
        productCardRepository.save(card);
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
