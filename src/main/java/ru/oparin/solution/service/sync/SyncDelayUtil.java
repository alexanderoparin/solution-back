package ru.oparin.solution.service.sync;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/**
 * Утилита задержек для синхронизации с WB API (лимиты запросов).
 */
@UtilityClass
public class SyncDelayUtil {

    @SneakyThrows(InterruptedException.class)
    public static void sleep(long ms) {
        Thread.sleep(ms);
    }
}
