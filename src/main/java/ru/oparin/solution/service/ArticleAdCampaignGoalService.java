package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.ArticleAdCampaignGoal;
import ru.oparin.solution.model.ProductCard;
import ru.oparin.solution.model.User;
import ru.oparin.solution.repository.ArticleAdCampaignGoalRepository;
import ru.oparin.solution.repository.ProductCardRepository;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ArticleAdCampaignGoalService {

    private final ArticleAdCampaignGoalRepository goalRepository;
    private final ProductCardRepository productCardRepository;

    @Transactional(readOnly = true)
    public Optional<String> findGoalText(Long cabinetId, Long nmId) {
        if (cabinetId == null || nmId == null) {
            return Optional.empty();
        }
        return goalRepository.findByCabinetIdAndNmId(cabinetId, nmId).map(ArticleAdCampaignGoal::getGoalText);
    }

    /**
     * Создаёт или обновляет цель РК. Доступно, если карточка артикула есть у селлера в этом кабинете.
     */
    @Transactional
    public void upsertGoal(User seller, Long cabinetId, Long nmId, String goal) {
        if (cabinetId == null) {
            throw new UserException("Кабинет не указан", HttpStatus.BAD_REQUEST);
        }
        ProductCard card = productCardRepository.findByNmIdAndCabinet_Id(nmId, cabinetId)
                .orElseThrow(() -> new UserException("Артикул не найден", HttpStatus.NOT_FOUND));
        if (!card.getSeller().getId().equals(seller.getId())) {
            throw new UserException("Артикул не принадлежит продавцу", HttpStatus.FORBIDDEN);
        }
        String text = goal != null ? goal : "";
        ArticleAdCampaignGoal entity = goalRepository.findByCabinetIdAndNmId(cabinetId, nmId)
                .orElseGet(() -> ArticleAdCampaignGoal.builder()
                        .cabinetId(cabinetId)
                        .nmId(nmId)
                        .goalText("")
                        .build());
        entity.setGoalText(text);
        goalRepository.save(entity);
    }
}
