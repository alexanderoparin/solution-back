package ru.oparin.solution.service.campaign;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Журнал и application-log для управления РК из планировщика: без спама при повторных тиках.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignScheduleControlNotifier {

    private final CampaignChangeLogService changeLogService;
    private final CampaignBudgetTimelineService timelineService;

    /** Последняя категория сообщения в истории (ключ: cabinetId:advertId). */
    private final ConcurrentHashMap<String, String> lastHistoryCategory = new ConcurrentHashMap<>();

    /**
     * Запуск поставлен в очередь.
     */
    public void onStartEnqueued(Long advertId, Long cabinetId) {
        writeHistoryOnce(advertId, cabinetId, "start_enqueued", "Запуск РК поставлен в очередь");
    }

    /**
     * Не удалось поставить запуск в очередь (прочая ошибка).
     */
    public void onStartFailed(Long advertId, Long cabinetId, String reason) {
        writeHistoryOnce(advertId, cabinetId, "start_error", "Запуск РК не выполнен: " + reason);
    }

    /**
     * Фактический успешный запуск (после выполнения события в очереди или прямого вызова).
     */
    public void onStartSucceededOnWb(Long advertId, Long cabinetId) {
        lastHistoryCategory.remove(historyKey(cabinetId, advertId));
        changeLogService.log(advertId, cabinetId, null, "РК запущена");
        timelineService.recordStart(advertId, cabinetId);
        log.info("РК advertId={} cabinetId={}: успешный запуск", advertId, cabinetId);
    }

    /**
     * Пауза поставлена в очередь.
     */
    public void onPauseEnqueued(Long advertId, Long cabinetId, String reason) {
        writeHistoryOnce(advertId, cabinetId, "pause_enqueued", reason + " — поставлено в очередь");
    }

    /**
     * Не удалось поставить паузу в очередь (прочая ошибка).
     */
    public void onPauseFailed(Long advertId, Long cabinetId, String reason) {
        writeHistoryOnce(advertId, cabinetId, "pause_error", "Остановка РК не выполнена: " + reason);
    }

    /**
     * Фактическая успешная пауза (после выполнения события в очереди или прямого вызова).
     */
    public void onPauseSucceededOnWb(Long advertId, Long cabinetId) {
        lastHistoryCategory.remove(historyKey(cabinetId, advertId));
        changeLogService.log(advertId, cabinetId, null, "РК остановлена");
        timelineService.recordStop(advertId, cabinetId);
        log.info("РК advertId={} cabinetId={}: успешная остановка", advertId, cabinetId);
    }

    private void writeHistoryOnce(Long advertId, Long cabinetId, String category, String message) {
        String key = historyKey(cabinetId, advertId);
        String prev = lastHistoryCategory.put(key, category);
        if (category.equals(prev)) {
            log.debug("РК advertId={} cabinetId={}: {} (повтор)", advertId, cabinetId, message);
            return;
        }
        changeLogService.log(advertId, cabinetId, null, message);
        log.info("РК advertId={} cabinetId={}: {}", advertId, cabinetId, message);
    }

    private static String historyKey(Long cabinetId, Long advertId) {
        return cabinetId + ":" + advertId;
    }
}
