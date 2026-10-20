package ru.oparin.solution.model;

/**
 * Стабильные коды тарифных планов и услуг.
 */
public final class PlanCodes {

    public static final String CAMPAIGN_FREE = "campaign_free";
    public static final String ANALYTICS_FREE = "analytics_free";
    /** Legacy PRO (деактивирован); новые коды — {@code pro_*} с тиром кабинетов. */
    public static final String PRO_MONTH = "pro_month";
    /** Бесплатный пакет А/Б (деактивирован). */
    public static final String AB_PACK_FREE = "ab_pack_free";
    public static final String AB_PACK_1 = "ab_pack_1";
    public static final String AB_PACK_5 = "ab_pack_5";
    public static final String AB_PACK_10 = "ab_pack_10";

    /** Префикс тарифов полного доступа PRO. */
    public static final String PRO_PLAN_PREFIX = "pro_";
    /** Префикс тарифов услуги «Управление РК». */
    public static final String CAMPAIGN_PLAN_PREFIX = "campaign_";
    /** Префикс пакетов А/Б тестов. */
    public static final String AB_PACK_PREFIX = "ab_pack_";

    /**
     * Fallback стартовой бесплатной квоты А/Б (legacy).
     */
    public static final int AB_TEST_FREE_QUOTA = 3;

    private PlanCodes() {
    }

    /**
     * Код тарифа полного доступа (legacy {@link #PRO_MONTH} или новый {@code pro_*}).
     */
    public static boolean isProCode(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        return PRO_MONTH.equals(code) || code.startsWith(PRO_PLAN_PREFIX);
    }

    /**
     * Код услуги «Управление РК» (включая legacy campaign_free/week/month).
     */
    public static boolean isCampaignCode(String code) {
        return code != null && code.startsWith(CAMPAIGN_PLAN_PREFIX);
    }

    /**
     * Код пакета А/Б.
     */
    public static boolean isAbPackCode(String code) {
        return code != null && code.startsWith(AB_PACK_PREFIX);
    }
}
