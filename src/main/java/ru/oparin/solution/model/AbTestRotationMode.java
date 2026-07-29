package ru.oparin.solution.model;

/**
 * Режим ротации главного фото в А/Б-тесте.
 */
public enum AbTestRotationMode {
    /** Смена после набора N показов у текущего варианта. */
    ROTATION_BY_VIEWS,
    /** Смена по фиксированному интервалу. */
    ROTATION_BY_INTERVAL
}
