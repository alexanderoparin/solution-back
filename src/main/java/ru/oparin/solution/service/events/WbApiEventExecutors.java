package ru.oparin.solution.service.events;

/**
 * Имена Spring-бинов исполнителей WB API событий.
 */
public final class WbApiEventExecutors {

    public static final String CONTENT = "contentCardsListPageEventExecutor";
    public static final String ANALYTICS = "analyticsSalesFunnelEventExecutor";
    public static final String PRICES_CABINET_WITH_SPP = "pricesCabinetWithSppEventExecutor";
    public static final String PROMOTION_COUNT = "promotionCountEventExecutor";
    public static final String PROMOTION_ADVERTS_BATCH = "promotionAdvertsBatchEventExecutor";
    public static final String PROMOTION_STATS_BATCH = "promotionStatsBatchEventExecutor";
    public static final String PROMOTION_NORMQUERY_STATS_BATCH = "promotionNormQueryStatsBatchEventExecutor";
    public static final String PROMOTION_CAMPAIGN_START = "promotionCampaignStartEventExecutor";
    public static final String PROMOTION_CAMPAIGN_PAUSE = "promotionCampaignPauseEventExecutor";
    public static final String ITEM_RATING_SYNC = "itemRatingSyncCabinetEventExecutor";
    public static final String PROMOTION_CALENDAR_SYNC = "promotionCalendarSyncCabinetEventExecutor";
    public static final String WAREHOUSES_SYNC = "warehousesSyncCabinetEventExecutor";
    public static final String STOCKS = "stocksByNmIdEventExecutor";
    public static final String FBS_WAREHOUSES_SYNC = "fbsWbWarehousesSyncCabinetEventExecutor";
    public static final String FBS_STOCKS = "fbsStocksCabinetEventExecutor";
    public static final String AB_TEST_START = "abTestStartEventExecutor";
    public static final String AB_TEST_APPLY_PHOTO = "abTestApplyPhotoEventExecutor";
    public static final String AB_TEST_STATS_POLL = "abTestStatsPollEventExecutor";

    private WbApiEventExecutors() {
    }
}
