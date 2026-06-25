package ru.oparin.solution.service.campaign;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Очередь опроса бюджета WB в рамках одного кабинета: за тик планировщика
 * реальный HTTP-запрос к {@code /adv/v1/budget} разрешён только выбранным РК (round-robin).
 * <p>
 * Вне тика планировщика ({@link #endSchedulerTick}) ограничение не действует — ручные вызовы идут как раньше.
 */
@Component
public class CabinetBudgetPollCoordinator {

    private static final ThreadLocal<Set<String>> API_GRANTED = new ThreadLocal<>();

    private final ConcurrentHashMap<Long, Integer> roundRobinIndexByCabinet = new ConcurrentHashMap<>();

    /**
     * Начинает тик планировщика: для каждого кабинета выбирает одну РК для опроса бюджета.
     *
     * @param candidatesByCabinet advertId кандидатов по cabinetId (в активном слоте или на budget trail)
     */
    public void beginSchedulerTick(Map<Long, List<Long>> candidatesByCabinet) {
        Set<String> granted = new HashSet<>();
        for (Map.Entry<Long, List<Long>> entry : candidatesByCabinet.entrySet()) {
            Long cabinetId = entry.getKey();
            List<Long> sorted = entry.getValue().stream().sorted().toList();
            if (sorted.isEmpty()) {
                continue;
            }
            int index = roundRobinIndexByCabinet.getOrDefault(cabinetId, 0);
            Long leader = sorted.get(index % sorted.size());
            roundRobinIndexByCabinet.put(cabinetId, (index + 1) % sorted.size());
            granted.add(slotKey(cabinetId, leader));
        }
        API_GRANTED.set(granted);
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

    public void endSchedulerTick() {
        API_GRANTED.remove();
    }

    private static String slotKey(Long cabinetId, Long advertId) {
        return cabinetId + ":" + advertId;
    }
}
