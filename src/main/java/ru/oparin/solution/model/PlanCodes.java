package ru.oparin.solution.model;

/**
 * Стабильные коды тарифных планов и услуг.
 */
public final class PlanCodes {

    public static final String CAMPAIGN_FREE = "campaign_free";
    public static final String ANALYTICS_FREE = "analytics_free";
    public static final String PRO_MONTH = "pro_month";
    public static final String AB_PACK_1 = "ab_pack_1";
    public static final String AB_PACK_5 = "ab_pack_5";
    public static final String AB_PACK_10 = "ab_pack_10";

    /** Префикс тарифов услуги «Управление РК». */
    public static final String CAMPAIGN_PLAN_PREFIX = "campaign_";
    /** Префикс пакетов А/Б тестов. */
    public static final String AB_PACK_PREFIX = "ab_pack_";

    /** Стартовая бесплатная квота А/Б на кабинет. */
    public static final int AB_TEST_FREE_QUOTA = 3;

    private PlanCodes() {
    }
}
