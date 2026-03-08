package ru.oparin.solution.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import ru.oparin.solution.dto.wb.CalendarPromotionsResponse;
import ru.oparin.solution.exception.WbApiUnauthorizedScopeException;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.PromotionParticipation;
import ru.oparin.solution.model.Role;
import ru.oparin.solution.repository.ProductCardRepository;
import ru.oparin.solution.repository.PromotionParticipationRepository;
import ru.oparin.solution.service.wb.WbApiCategory;
import ru.oparin.solution.service.wb.WbCalendarApiClient;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Синхронизация участия товаров кабинета в акциях календаря WB.
 * Получает список акций на сегодня, по каждой — список nmId в акции, пересекает с товарами кабинета и сохраняет.
 */
@Service
@Slf4j
public class PromotionCalendarService {

    private final WbCalendarApiClient calendarApiClient;
    private final ProductCardRepository productCardRepository;
    private final PromotionParticipationRepository participationRepository;
    private final CabinetService cabinetService;
    private final CabinetScopeStatusService cabinetScopeStatusService;
    private final PromotionCalendarService self;

    public PromotionCalendarService(WbCalendarApiClient calendarApiClient,
                                    ProductCardRepository productCardRepository,
                                    PromotionParticipationRepository participationRepository,
                                    CabinetService cabinetService,
                                    CabinetScopeStatusService cabinetScopeStatusService,
                                    @Lazy PromotionCalendarService self) {
        this.calendarApiClient = calendarApiClient;
        this.productCardRepository = productCardRepository;
        this.participationRepository = participationRepository;
        this.cabinetService = cabinetService;
        this.cabinetScopeStatusService = cabinetScopeStatusService;
        this.self = self;
    }

    /**
     * Синхронизирует данные об участии в акциях для одного кабинета (своя транзакция).
     * Берет акции на «сегодня» (UTC), по каждой запрашивает номенклатуры inAction=true,
     * пересекает с nmId кабинета и сохраняет в promotion_participations (старые по кабинету удаляются).
     *
     * @param cabinet кабинет с заполненным apiKey
     */
    @Transactional
    public void syncPromotionsForCabinet(Cabinet cabinet) {
        Long cabinetId = cabinet.getId();
        String apiKey = cabinet.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.debug("Кабинет {} без API-ключа, синхронизация акций пропущена", cabinetId);
            return;
        }

        ZonedDateTime today = ZonedDateTime.now(ZoneOffset.UTC);
        String start = WbCalendarApiClient.startOfDayUtc(today);
        String end = WbCalendarApiClient.endOfDayUtc(today);

        try {
            CalendarPromotionsResponse response = calendarApiClient.getPromotions(apiKey, start, end, false);
            if (response.getData() == null || response.getData().getPromotions() == null
                    || response.getData().getPromotions().isEmpty()) {
                participationRepository.deleteByCabinet_Id(cabinetId);
                log.debug("Кабинет {}: акций на сегодня нет, старые участия удалены", cabinetId);
                return;
            }

            List<Long> cabinetNmIds = productCardRepository.findByCabinet_Id(cabinetId).stream()
                    .map(c -> c.getNmId())
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            if (cabinetNmIds.isEmpty()) {
                participationRepository.deleteByCabinet_Id(cabinetId);
                log.debug("Кабинет {}: нет карточек, участия очищены", cabinetId);
                return;
            }

            Set<Long> cabinetNmIdSet = new HashSet<>(cabinetNmIds);
            List<PromotionParticipation> toSave = new ArrayList<>();

            for (CalendarPromotionsResponse.CalendarPromotionItem promo : response.getData().getPromotions()) {
                Long promoId = promo.getId();
                String promoName = promo.getName() != null ? promo.getName() : "";
                String promoType = promo.getType() != null ? promo.getType() : "";
                // Метод nomenclatures неприменим для автоакций (документация WB).
                if ("auto".equalsIgnoreCase(promoType)) {
                    log.debug("Кабинет {}: акция {} (type=auto) — метод nomenclatures не поддерживается, пропуск", cabinetId, promoId);
                    continue;
                }
                List<Long> inPromotion;
                try {
                    inPromotion = calendarApiClient.getAllNomenclatureIdsInPromotion(apiKey, promoId, true);
                } catch (HttpClientErrorException e) {
                    if (e.getStatusCode().value() == 422) {
                        log.warn("Кабинет {}: акция {} — 422 (номенклатуры недоступны), пропуск", cabinetId, promoId);
                        continue;
                    }
                    throw e;
                }
                for (Long nmId : inPromotion) {
                    if (cabinetNmIdSet.contains(nmId)) {
                        toSave.add(PromotionParticipation.builder()
                                .cabinet(cabinet)
                                .nmId(nmId)
                                .wbPromotionId(promoId)
                                .wbPromotionName(promoName)
                                .wbPromotionType(promoType)
                                .build());
                    }
                }
            }

            participationRepository.deleteByCabinet_Id(cabinetId);
            if (!toSave.isEmpty()) {
                participationRepository.saveAll(toSave);
            }
            log.info("Кабинет {}: синхронизация акций завершена, участий: {}", cabinetId, toSave.size());
            cabinetScopeStatusService.recordSuccess(cabinetId, WbApiCategory.PRICES_AND_DISCOUNTS);
        } catch (WbApiUnauthorizedScopeException e) {
            cabinetScopeStatusService.recordFailure(cabinetId, e.getCategory(), e.getMessage());
            log.warn("Для кабинета {} нет доступа к категории WB API: {}. Проверьте настройки токена в ЛК продавца.", cabinetId, e.getCategory().getDisplayName());
            throw e;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 401) {
                log.warn("Кабинет {}: API-ключ отклонён при запросе календаря акций (401)", cabinetId);
            } else {
                log.warn("Кабинет {}: ошибка календаря акций {}: {}", cabinetId, e.getStatusCode(), e.getMessage());
            }
            throw e;
        }
    }

    /**
     * Синхронизация акций для всех кабинетов с API-ключом (активные селлеры).
     * Для каждого кабинета вызывается syncPromotionsForCabinet через прокси — своя транзакция.
     */
    public void syncPromotionsForAllCabinets() {
        List<Cabinet> cabinets = cabinetService.findCabinetsWithApiKeyAndUser(Role.SELLER);
        log.info("Запуск синхронизации акций для {} кабинетов", cabinets.size());
        for (Cabinet cabinet : cabinets) {
            try {
                self.syncPromotionsForCabinet(cabinet);
            } catch (Exception e) {
                log.error("Ошибка синхронизации акций для кабинета {}: {}", cabinet.getId(), e.getMessage(), e);
            }
        }
    }
}
