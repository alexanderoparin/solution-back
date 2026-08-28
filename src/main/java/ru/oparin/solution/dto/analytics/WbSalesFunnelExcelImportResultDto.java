package ru.oparin.solution.dto.analytics;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.List;

/**
 * Результат импорта воронки продаж из Excel-выгрузки WB.
 */
@Value
@Builder
public class WbSalesFunnelExcelImportResultDto {
    int rowsTotal;
    int rowsImported;
    int rowsCreated;
    int rowsUpdated;
    int rowsSkippedUnknownNmId;
    int rowsSkippedInvalid;
    LocalDate periodFrom;
    LocalDate periodTo;
    List<String> warnings;
}
