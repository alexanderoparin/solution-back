package ru.oparin.solution.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.dto.analytics.NormQueryClusterSortField;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Динамическая сортировка и пагинация агрегированных поисковых запросов Ozon (native SQL).
 */
@Repository
public class OzonPromotionCampaignSearchPhraseStatisticsRepositoryImpl
        implements OzonPromotionCampaignSearchPhraseStatisticsRepositoryCustom {

    private static final String AGGREGATE_SELECT = """
            SELECT
                s.search_phrase AS normQuery,
                CASE WHEN COALESCE(SUM(s.clicks), 0) > 0
                    THEN SUM(s.avg_pos * s.clicks) / SUM(s.clicks)
                    ELSE AVG(s.avg_pos) END AS avgPos,
                COALESCE(SUM(s.clicks), 0) AS clicks,
                COALESCE(SUM(s.to_cart), 0) AS atbs,
                COALESCE(SUM(s.orders), 0) AS orders,
                COALESCE(SUM(s.spend), 0) AS spend,
                CASE WHEN COALESCE(SUM(s.clicks), 0) > 0
                    THEN SUM(s.spend) / SUM(s.clicks)
                    ELSE NULL END AS cpc,
                CASE WHEN COALESCE(SUM(s.orders), 0) > 0
                    THEN SUM(s.spend) / SUM(s.orders)
                    ELSE 0 END AS cpo
            """;

    private static final String TOTALS_SELECT = """
            SELECT
                CASE WHEN COALESCE(SUM(s.clicks), 0) > 0
                    THEN SUM(s.avg_pos * s.clicks) / SUM(s.clicks)
                    ELSE AVG(s.avg_pos) END AS avgPos,
                COALESCE(SUM(s.clicks), 0) AS clicks,
                COALESCE(SUM(s.to_cart), 0) AS atbs,
                COALESCE(SUM(s.orders), 0) AS orders,
                COALESCE(SUM(s.spend), 0) AS spend,
                CASE WHEN COALESCE(SUM(s.clicks), 0) > 0
                    THEN SUM(s.spend) / SUM(s.clicks)
                    ELSE NULL END AS cpc,
                CASE WHEN COALESCE(SUM(s.orders), 0) > 0
                    THEN SUM(s.spend) / SUM(s.orders)
                    ELSE 0 END AS cpo
            """;

    private static final String FROM_TABLE =
            " FROM solution.ozon_promotion_campaign_search_phrase_statistics s ";

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public long countAggregatedClusters(
            Long campaignId,
            LocalDate dateFrom,
            LocalDate dateTo,
            Long sku,
            String searchPattern
    ) {
        String inner = buildAggregateInnerSql(sku, searchPattern);
        Query query = entityManager.createNativeQuery("SELECT COUNT(*) FROM (" + inner + ") c");
        bindBaseParams(query, campaignId, dateFrom, dateTo, sku, searchPattern);
        return ((Number) query.getSingleResult()).longValue();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<OzonPromotionCampaignSearchPhraseStatisticsRepository.SearchPhraseClusterAggregateRow> findAggregatedClustersPage(
            Long campaignId,
            LocalDate dateFrom,
            LocalDate dateTo,
            Long sku,
            String searchPattern,
            NormQueryClusterSortField sortBy,
            Sort.Direction sortDir,
            int limit,
            int offset
    ) {
        String inner = buildAggregateInnerSql(sku, searchPattern);
        String sql = "SELECT * FROM (" + inner + ") agg "
                + "ORDER BY agg." + sortBy.getSqlAlias() + " " + sortDir.name() + " NULLS LAST "
                + "LIMIT :limit OFFSET :offset";
        Query query = entityManager.createNativeQuery(sql);
        bindBaseParams(query, campaignId, dateFrom, dateTo, sku, searchPattern);
        query.setParameter("limit", limit);
        query.setParameter("offset", offset);

        List<Object[]> raw = query.getResultList();
        List<OzonPromotionCampaignSearchPhraseStatisticsRepository.SearchPhraseClusterAggregateRow> rows =
                new ArrayList<>(raw.size());
        for (Object[] row : raw) {
            rows.add(new AggregateRowProjection(row));
        }
        return rows;
    }

    @Override
    public OzonPromotionCampaignSearchPhraseStatisticsRepository.SearchPhraseClusterTotalsRow findTotalsByCampaignAndPeriod(
            Long campaignId,
            LocalDate dateFrom,
            LocalDate dateTo,
            Long sku,
            String searchPattern
    ) {
        String sql = TOTALS_SELECT + FROM_TABLE + buildWhereClause(sku, searchPattern);
        Query query = entityManager.createNativeQuery(sql);
        bindBaseParams(query, campaignId, dateFrom, dateTo, sku, searchPattern);
        Object result = query.getSingleResult();
        if (result == null) {
            return null;
        }
        return new TotalsRowProjection((Object[]) result);
    }

    private String buildAggregateInnerSql(Long sku, String searchPattern) {
        return AGGREGATE_SELECT + FROM_TABLE + buildWhereClause(sku, searchPattern) + " GROUP BY s.search_phrase ";
    }

    private static String buildWhereClause(Long sku, String searchPattern) {
        StringBuilder where = new StringBuilder("""
                WHERE s.campaign_id = :campaignId
                  AND s.date BETWEEN :dateFrom AND :dateTo
                """);
        if (sku != null) {
            where.append(" AND (s.sku = :sku OR s.sku IS NULL)");
        }
        if (searchPattern != null) {
            where.append(" AND LOWER(s.search_phrase) LIKE :search");
        }
        return where.toString();
    }

    private static void bindBaseParams(
            Query query,
            Long campaignId,
            LocalDate dateFrom,
            LocalDate dateTo,
            Long sku,
            String searchPattern
    ) {
        query.setParameter("campaignId", campaignId);
        query.setParameter("dateFrom", dateFrom);
        query.setParameter("dateTo", dateTo);
        if (sku != null) {
            query.setParameter("sku", sku);
        }
        if (searchPattern != null) {
            query.setParameter("search", searchPattern);
        }
    }

    private static final class AggregateRowProjection
            implements OzonPromotionCampaignSearchPhraseStatisticsRepository.SearchPhraseClusterAggregateRow {

        private final String searchPhrase;
        private final BigDecimal avgPos;
        private final Integer clicks;
        private final Integer atbs;
        private final Integer orders;
        private final BigDecimal spend;
        private final BigDecimal cpc;
        private final BigDecimal cpo;

        AggregateRowProjection(Object[] row) {
            searchPhrase = row[0] != null ? row[0].toString() : null;
            avgPos = toBigDecimal(row[1]);
            clicks = toInt(row[2]);
            atbs = toInt(row[3]);
            orders = toInt(row[4]);
            spend = toBigDecimal(row[5]);
            cpc = toBigDecimal(row[6]);
            cpo = toBigDecimal(row[7]);
        }

        @Override
        public String getSearchPhrase() {
            return searchPhrase;
        }

        @Override
        public BigDecimal getAvgPos() {
            return avgPos;
        }

        @Override
        public Integer getClicks() {
            return clicks;
        }

        @Override
        public Integer getAtbs() {
            return atbs;
        }

        @Override
        public Integer getOrders() {
            return orders;
        }

        @Override
        public BigDecimal getSpend() {
            return spend;
        }

        @Override
        public BigDecimal getCpc() {
            return cpc;
        }

        @Override
        public BigDecimal getCpo() {
            return cpo;
        }
    }

    private static final class TotalsRowProjection
            implements OzonPromotionCampaignSearchPhraseStatisticsRepository.SearchPhraseClusterTotalsRow {

        private final BigDecimal avgPos;
        private final Integer clicks;
        private final Integer atbs;
        private final Integer orders;
        private final BigDecimal spend;
        private final BigDecimal cpc;
        private final BigDecimal cpo;

        TotalsRowProjection(Object[] row) {
            avgPos = toBigDecimal(row[0]);
            clicks = toInt(row[1]);
            atbs = toInt(row[2]);
            orders = toInt(row[3]);
            spend = toBigDecimal(row[4]);
            cpc = toBigDecimal(row[5]);
            cpo = toBigDecimal(row[6]);
        }

        @Override
        public BigDecimal getAvgPos() {
            return avgPos;
        }

        @Override
        public Integer getClicks() {
            return clicks;
        }

        @Override
        public Integer getAtbs() {
            return atbs;
        }

        @Override
        public Integer getOrders() {
            return orders;
        }

        @Override
        public BigDecimal getSpend() {
            return spend;
        }

        @Override
        public BigDecimal getCpc() {
            return cpc;
        }

        @Override
        public BigDecimal getCpo() {
            return cpo;
        }
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        return new BigDecimal(value.toString());
    }

    private static Integer toInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(value.toString());
    }
}
