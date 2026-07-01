package ru.oparin.solution.service.campaign;

import org.springframework.stereotype.Component;

import java.util.*;
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
    private static final ThreadLocal<Map<Long, Long>> TICK_LEADER_BY_CABINET = new ThreadLocal<>();
    /** РК, по которым в текущем тике уже был успешный HTTP GET /adv/v1/budget. */
    private static final ThreadLocal<Set<String>> BUDGET_POLLED_THIS_TICK = new ThreadLocal<>();

    private final ConcurrentHashMap<Long, Integer> roundRobinIndexByCabinet = new ConcurrentHashMap<>();

    /**
     * Начинает тик планировщика: для каждого кабинета выбирает одну РК для опроса бюджета.
     *
     * @param candidatesByCabinet advertId кандидатов по cabinetId (в активном слоте или на budget trail)
     */
    public void beginSchedulerTick(Map<Long, List<Long>> candidatesByCabinet) {
        Set<String> granted = new HashSet<>();
        Map<Long, Long> leaders = new HashMap<>();
        for (Map.Entry<Long, List<Long>> entry : candidatesByCabinet.entrySet()) {
            Long cabinetId = entry.getKey();
            List<Long> sorted = entry.getValue().stream().sorted().toList();
            if (sorted.isEmpty()) {
                continue;
            }
            int index = roundRobinIndexByCabinet.getOrDefault(cabinetId, 0);
            Long leader = sorted.get(index % sorted.size());
            roundRobinIndexByCabinet.put(cabinetId, (index + 1) % sorted.size());
            leaders.put(cabinetId, leader);
            granted.add(slotKey(cabinetId, leader));
        }
        API_GRANTED.set(granted);
        TICK_LEADER_BY_CABINET.set(leaders);
        BUDGET_POLLED_THIS_TICK.set(new HashSet<>());
    }

    /**
     * Лидер round-robin для кабинета в текущем тике планировщика.
     */
    public Optional<Long> getTickLeader(Long cabinetId) {
        Map<Long, Long> leaders = TICK_LEADER_BY_CABINET.get();
        if (leaders == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(leaders.get(cabinetId));
    }

    /**
     * {@code true}, если РК выбрана лидером очереди бюджета в текущем тике.
     */
    public boolean isTickLeader(Long cabinetId, Long advertId) {
        return getTickLeader(cabinetId).filter(advertId::equals).isPresent();
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

    /**
     * Помечает успешный опрос бюджета WB в текущем тике планировщика (один HTTP на РК за тик).
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
        TICK_LEADER_BY_CABINET.remove();
        BUDGET_POLLED_THIS_TICK.remove();
    }

    private static String slotKey(Long cabinetId, Long advertId) {
        return cabinetId + ":" + advertId;
    }
}
