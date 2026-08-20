package ru.oparin.solution.service.sync;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.oparin.solution.dto.wb.WbCardsListRequest;
import ru.oparin.solution.dto.wb.WbCardsListResponse;
import ru.oparin.solution.service.wb.WbContentApiClient;

import java.util.ArrayList;

/**
 * Загрузка списка карточек товаров из WB API с пагинацией.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WbCardsSyncService {

    private static final int CARDS_PAGE_LIMIT = 100;
    private static final int WITH_PHOTO_ALL = -1;

    private final WbContentApiClient contentApiClient;

    /**
     * Загружает все карточки товаров селлера с пагинацией (лимит WB: 100 запросов в минуту).
     */
    public WbCardsListResponse fetchAllCards(String apiKey) {
        WbCardsListRequest initialRequest = createInitialCardsRequest();
        WbCardsListResponse response = contentApiClient.getCardsList(apiKey, initialRequest);

        if (response.getCards() == null) {
            response.setCards(new ArrayList<>());
        }

        int totalReceived = response.getCards().size();
        Integer totalInResponse = response.getCursor() != null ? response.getCursor().getTotal() : null;
        log.info("Первая страница: получено {} карточек, total в ответе: {}", totalReceived, totalInResponse);

        int pageNumber = 1;
        while (hasMoreCards(response, totalReceived)) {
            pageNumber++;

            WbCardsListRequest nextRequest = createNextPageRequest(response);
            WbCardsListResponse nextResponse = contentApiClient.getCardsList(apiKey, nextRequest);

            if (nextResponse.getCards() == null || nextResponse.getCards().isEmpty()) {
                log.info("Страница {}: пустой ответ, завершаем пагинацию", pageNumber);
                break;
            }

            int cardsOnPage = nextResponse.getCards().size();
            response.getCards().addAll(nextResponse.getCards());
            totalReceived += cardsOnPage;
            response.setCursor(nextResponse.getCursor());

            Integer nextTotal = nextResponse.getCursor() != null ? nextResponse.getCursor().getTotal() : null;
            log.info("Страница {}: получено {} карточек, total в ответе: {}, всего получено: {}",
                    pageNumber, cardsOnPage, nextTotal, totalReceived);

            if (nextTotal != null && nextTotal < CARDS_PAGE_LIMIT) {
                log.info("Страница {}: total ({}) < limit ({}), последняя страница", pageNumber, nextTotal, CARDS_PAGE_LIMIT);
                break;
            }
        }

        log.info("Пагинация завершена. Всего получено карточек: {}", totalReceived);
        return response;
    }

    private WbCardsListRequest createInitialCardsRequest() {
        WbCardsListRequest.Cursor cursor = WbCardsListRequest.Cursor.builder()
                .limit(CARDS_PAGE_LIMIT)
                .build();
        WbCardsListRequest.Filter filter = WbCardsListRequest.Filter.builder()
                .withPhoto(WITH_PHOTO_ALL)
                .build();
        WbCardsListRequest.Settings settings = WbCardsListRequest.Settings.builder()
                .cursor(cursor)
                .filter(filter)
                .build();
        return WbCardsListRequest.builder()
                .settings(settings)
                .build();
    }

    private boolean hasMoreCards(WbCardsListResponse response, int totalReceived) {
        if (response.getCursor() == null || response.getCursor().getTotal() == null) {
            log.debug("hasMoreCards: cursor или total отсутствует, завершаем пагинацию");
            return false;
        }
        Integer total = response.getCursor().getTotal();
        boolean shouldContinue = total >= CARDS_PAGE_LIMIT;
        log.debug("hasMoreCards: total={}, limit={}, totalReceived={}, shouldContinue={}",
                total, CARDS_PAGE_LIMIT, totalReceived, shouldContinue);
        return shouldContinue;
    }

    private WbCardsListRequest createNextPageRequest(WbCardsListResponse response) {
        if (response.getCursor() == null) {
            throw new IllegalStateException("Cursor отсутствует в ответе для создания следующего запроса");
        }
        WbCardsListResponse.Cursor cursor = response.getCursor();
        log.debug("Создание запроса следующей страницы: nmID={}, updatedAt={}", cursor.getNmID(), cursor.getUpdatedAt());

        WbCardsListRequest.Cursor nextCursor = WbCardsListRequest.Cursor.builder()
                .limit(CARDS_PAGE_LIMIT)
                .nmID(cursor.getNmID())
                .updatedAt(cursor.getUpdatedAt())
                .build();
        WbCardsListRequest.Filter filter = WbCardsListRequest.Filter.builder()
                .withPhoto(WITH_PHOTO_ALL)
                .build();
        WbCardsListRequest.Settings nextSettings = WbCardsListRequest.Settings.builder()
                .cursor(nextCursor)
                .filter(filter)
                .build();
        return WbCardsListRequest.builder()
                .settings(nextSettings)
                .build();
    }
}
