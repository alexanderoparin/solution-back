package ru.oparin.solution.service.campaign;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Очередь опроса бюджета WB в рамках одного кабинета за тик планировщика.
 * HTTP к {@code POST /api/advert/v2/budget} разрешён кандидатам (слот / trail)
 * и внеочередным РК ({@link #grantMandatoryPoll}).
 * <p>
 * Вне тика планировщика ({@link #endSchedulerTick}) ограничение не действует — ручные вызовы идут как раньше.
 */
@Component
public class WbCabinetBudgetPollCoordinator {

    private static final ThreadLocal<Set<String>> API_GRANTED = new ThreadLocal<>();
    /** РК, по которым в текущем тике уже был успешный HTTP POST /api/advert/v2/budget. */
    private static final ThreadLocal<Set<String>> BUDGET_POLLED_THIS_TICK = new ThreadLocal<>();

    /**
     * Начинает тик планировщика: все кандидаты кабинета получают право на опрос бюджета.
     *
     * @param candidatesByCabinet advertId кандидатов по cabinetId (в активном слоте или на budget trail)
     */
    public void beginSchedulerTick(Map<Long, List<Long>> candidatesByCabinet) {
        Set<String> granted = new HashSet<>();
        for (Map.Entry<Long, List<Long>> entry : candidatesByCabinet.entrySet()) {
            Long cabinetId = entry.getKey();
            for (Long advertId : entry.getValue()) {
                if (advertId != null) {
                    granted.add(slotKey(cabinetId, advertId));
                }
            }
        }
        API_GRANTED.set(granted);
        BUDGET_POLLED_THIS_TICK.set(new HashSet<>());
    }

    /**
     * Разрешает внеочередной запрос бюджета (вход в слот, автопополнение и т.п.) в текущем тике.
     */
    public void grantMandatoryPoll(Long cabinetId, Long advertId) {
        Set<String> granted = API_GRANTED.get();
        if (granted != null) {
            granted.add(slotKey(cabinetId, advertId));
        }
    }

    /**
     * Снимает разрешение HTTP у всех РК кабинета (пакетный опрос не удался — не разгоняем одиночные запросы).
     */
    public void revokeCabinet(Long cabinetId) {
        Set<String> granted = API_GRANTED.get();
        if (granted == null || cabinetId == null) {
            return;
        }
        String prefix = cabinetId + ":";
        granted.removeIf(key -> key.startsWith(prefix));
    }

    /**
     * {@code true}, если в текущем тике планировщика этой РК разрешён запрос к WB.
     * Вне тика — всегда {@code true}.
     */
    public boolean mayCallWbApi(Long cabinetId, Long advertId) {
        Set<String> granted = API_GRANTED.get();
        if (granted == null) {
            return true;
        }
        return granted.contains(slotKey(cabinetId, advertId));
    }

    /**
     * Помечает успешный опрос бюджета WB в текущем тике планировщика.
     */
    public void markBudgetPolledThisTick(Long cabinetId, Long advertId) {
        Set<String> polled = BUDGET_POLLED_THIS_TICK.get();
        if (polled != null) {
            polled.add(slotKey(cabinetId, advertId));
        }
    }

    /**
     * {@code true}, если для РК в этом тике планировщика бюджет уже получен по HTTP.
     */
    public boolean wasBudgetPolledThisTick(Long cabinetId, Long advertId) {
        Set<String> polled = BUDGET_POLLED_THIS_TICK.get();
        return polled != null && polled.contains(slotKey(cabinetId, advertId));
    }

    public void endSchedulerTick() {
        API_GRANTED.remove();
        BUDGET_POLLED_THIS_TICK.remove();
    }

    private static String slotKey(Long cabinetId, Long advertId) {
        return cabinetId + ":" + advertId;
    }
}
