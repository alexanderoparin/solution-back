package ru.oparin.solution.service.campaign;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.model.WbCampaignAutoBudgetSettings;
import ru.oparin.solution.repository.WbCampaignAutoBudgetSettingsRepository;

/**
 * Автоматическое управление флагом промо-бонусов в настройках автопополнения.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WbCampaignAutoBudgetPromoService {

    static final String DISABLED_BY_WB_MESSAGE =
            "Автоматически отключено использование промо-бонусов в автопополнении: "
                    + "WB отклонил пополнение с промо (промо-бонусы недоступны или истекли)";

    private final WbCampaignAutoBudgetSettingsRepository autoBudgetRepository;
    private final WbCampaignChangeLogService changeLogService;

    /**
     * Отключает {@code usePromoCashback}, если он был включён, и пишет запись в историю (user = Auto).
     *
     * @return {@code true}, если настройка изменена
     */
    @Transactional
    public boolean disableUsePromoCashbackAfterWbRejection(Long advertId, Long cabinetId) {
        return autoBudgetRepository.findById(advertId)
                .filter(WbCampaignAutoBudgetSettings::isUsePromoCashback)
                .map(settings -> {
                    settings.setUsePromoCashback(false);
                    autoBudgetRepository.save(settings);
                    changeLogService.log(advertId, cabinetId, null, DISABLED_BY_WB_MESSAGE);
                    log.info(
                            "advertId={} cabinetId={}: usePromoCashback отключён после отказа WB по промо",
                            advertId,
                            cabinetId
                    );
                    return true;
                })
                .orElse(false);
    }
}
