package ru.oparin.solution.service;

/**
 * Результат очистки осиротевших файлов в каталоге загрузок.
 *
 * @param deletedFiles число успешно удалённых файлов
 * @param freedBytes   суммарный размер удалённых файлов в байтах (до удаления)
 */
public record UploadsCleanupResult(int deletedFiles, long freedBytes) {

    public static UploadsCleanupResult empty() {
        return new UploadsCleanupResult(0, 0L);
    }
}
