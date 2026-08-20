package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.User;
import ru.oparin.solution.model.WbArticleGoal;
import ru.oparin.solution.model.WbProductCard;
import ru.oparin.solution.repository.WbArticleGoalRepository;
import ru.oparin.solution.repository.WbProductCardRepository;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WbArticleGoalService {

    private final WbArticleGoalRepository goalRepository;
    private final WbProductCardRepository productCardRepository;

    @Transactional(readOnly = true)
    public Optional<String> findGoalText(Long cabinetId, Long nmId) {
        if (cabinetId == null || nmId == null) {
            return Optional.empty();
        }
        return goalRepository.findByCabinetIdAndNmId(cabinetId, nmId).map(WbArticleGoal::getGoalText);
    }

    /**
     * Создаёт или обновляет цель на артикул. Доступно, если карточка артикула есть у селлера в этом кабинете.
     */
    @Transactional
    public void upsertGoal(User seller, Long cabinetId, Long nmId, String goal) {
        if (cabinetId == null) {
            throw new UserException("Кабинет не указан", HttpStatus.BAD_REQUEST);
        }
        WbProductCard card = productCardRepository.findByNmIdAndCabinet_Id(nmId, cabinetId)
                .orElseThrow(() -> new UserException("Артикул не найден", HttpStatus.NOT_FOUND));
        if (card.getCabinet() == null
                || card.getCabinet().getUser() == null
                || !card.getCabinet().getUser().getId().equals(seller.getId())) {
            throw new UserException("Артикул не принадлежит продавцу", HttpStatus.FORBIDDEN);
        }
        String text = goal != null ? goal : "";
        WbArticleGoal entity = goalRepository.findByCabinetIdAndNmId(cabinetId, nmId)
                .orElseGet(() -> WbArticleGoal.builder()
                        .cabinetId(cabinetId)
                        .nmId(nmId)
                        .goalText("")
                        .build());
        entity.setGoalText(text);
        goalRepository.save(entity);
    }
}
