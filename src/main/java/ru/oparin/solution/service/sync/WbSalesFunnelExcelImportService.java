package ru.oparin.solution.service.sync;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ru.oparin.solution.dto.analytics.WbSalesFunnelExcelImportResultDto;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.MarketplaceType;
import ru.oparin.solution.model.WbProductCard;
import ru.oparin.solution.model.WbProductCardAnalytics;
import ru.oparin.solution.repository.WbProductCardAnalyticsRepository;
import ru.oparin.solution.repository.WbProductCardRepository;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Импорт суточной воронки продаж из Excel-выгрузки WB (лист «Товары»).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WbSalesFunnelExcelImportService {

    private static final String PRODUCTS_SHEET_NAME = "Товары";
    private static final String HEADER_NM_ID = "Артикул WB";
    private static final String HEADER_DATE = "Дата";
    private static final String HEADER_OPEN_CARD = "Переходы в карточку";
    private static final String HEADER_ADD_TO_CART = "Положили в корзину";
    private static final String HEADER_ORDERS = "Заказали товаров, шт";
    private static final String HEADER_ORDERS_SUM = "Заказали на сумму";
    private static final long MAX_FILE_SIZE_BYTES = 20L * 1024 * 1024;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final WbProductCardRepository productCardRepository;
    private final WbProductCardAnalyticsRepository analyticsRepository;

    /**
     * Парсит Excel и upsert-ит записи {@link WbProductCardAnalytics} для кабинета.
     *
     * @param cabinet кабинет WB
     * @param file    xlsx из ЛК WB (воронка продаж → детализация по дням)
     */
    @Transactional
    public WbSalesFunnelExcelImportResultDto importFromExcel(Cabinet cabinet, MultipartFile file) {
        validateCabinetAndFile(cabinet, file);
        Map<Long, WbProductCard> cardsByNmId = loadCardsByNmId(cabinet.getId());

        ParsedWorkbook parsed;
        try (InputStream inputStream = file.getInputStream()) {
            parsed = parseWorkbook(inputStream);
        } catch (IOException e) {
            throw new UserException("Не удалось прочитать Excel-файл: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }

        int created = 0;
        int updated = 0;
        int skippedUnknown = 0;
        int skippedInvalid = 0;
        LocalDate minDate = null;
        LocalDate maxDate = null;

        for (ParsedRow row : parsed.rows()) {
            if (row.nmId() == null || row.date() == null) {
                skippedInvalid++;
                continue;
            }
            WbProductCard card = cardsByNmId.get(row.nmId());
            if (card == null) {
                skippedUnknown++;
                continue;
            }
            minDate = minDate == null || row.date().isBefore(minDate) ? row.date() : minDate;
            maxDate = maxDate == null || row.date().isAfter(maxDate) ? row.date() : maxDate;

            Optional<WbProductCardAnalytics> existing = analyticsRepository.findByProductCardNmIdAndDateAndCabinet_Id(
                    row.nmId(), row.date(), cabinet.getId());
            WbProductCardAnalytics entity = existing.orElseGet(() -> {
                WbProductCardAnalytics analytics = new WbProductCardAnalytics();
                analytics.setProductCard(card);
                analytics.setCabinet(cabinet);
                analytics.setDate(row.date());
                return analytics;
            });
            entity.setOpenCard(row.openCard());
            entity.setAddToCart(row.addToCart());
            entity.setOrders(row.orders());
            entity.setOrdersSum(row.ordersSum());
            analyticsRepository.save(entity);
            if (existing.isPresent()) {
                updated++;
            } else {
                created++;
            }
        }

        log.info(
                "Импорт воронки WB из Excel: cabinetId={}, rows={}, imported={}, created={}, updated={}, skippedUnknown={}, skippedInvalid={}",
                cabinet.getId(),
                parsed.rows().size(),
                created + updated,
                created,
                updated,
                skippedUnknown,
                skippedInvalid
        );

        return WbSalesFunnelExcelImportResultDto.builder()
                .rowsTotal(parsed.rows().size())
                .rowsImported(created + updated)
                .rowsCreated(created)
                .rowsUpdated(updated)
                .rowsSkippedUnknownNmId(skippedUnknown)
                .rowsSkippedInvalid(skippedInvalid)
                .periodFrom(minDate)
                .periodTo(maxDate)
                .warnings(parsed.warnings())
                .build();
    }

    private static void validateCabinetAndFile(Cabinet cabinet, MultipartFile file) {
        if (cabinet.getMarketplaceType() != MarketplaceType.WB) {
            throw new UserException("Импорт воронки из Excel доступен только для кабинетов Wildberries", HttpStatus.BAD_REQUEST);
        }
        if (file == null || file.isEmpty()) {
            throw new UserException("Файл не передан", HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new UserException("Файл слишком большой (максимум 20 МБ)", HttpStatus.BAD_REQUEST);
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new UserException("Поддерживается только формат .xlsx", HttpStatus.BAD_REQUEST);
        }
    }

    private Map<Long, WbProductCard> loadCardsByNmId(Long cabinetId) {
        Map<Long, WbProductCard> result = new HashMap<>();
        for (WbProductCard card : productCardRepository.findByCabinet_Id(cabinetId)) {
            if (card.getNmId() != null) {
                result.putIfAbsent(card.getNmId(), card);
            }
        }
        return result;
    }

    private ParsedWorkbook parseWorkbook(InputStream inputStream) throws IOException {
        List<String> warnings = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = resolveProductsSheet(workbook, warnings);
            HeaderMapping header = findHeaderMapping(sheet, warnings);
            List<ParsedRow> rows = new ArrayList<>();
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            for (int rowIndex = header.rowIndex() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                ParsedRow parsedRow = parseDataRow(row, header, formatter);
                if (parsedRow != null) {
                    rows.add(parsedRow);
                }
            }
            if (rows.isEmpty()) {
                throw new UserException("В файле не найдено строк с данными воронки", HttpStatus.BAD_REQUEST);
            }
            return new ParsedWorkbook(rows, warnings);
        }
    }

    private static Sheet resolveProductsSheet(Workbook workbook, List<String> warnings) {
        Sheet named = workbook.getSheet(PRODUCTS_SHEET_NAME);
        if (named != null) {
            return named;
        }
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet candidate = workbook.getSheetAt(i);
            if (findHeaderRowIndex(candidate) >= 0) {
                warnings.add("Лист «" + PRODUCTS_SHEET_NAME + "» не найден, использован лист «" + candidate.getSheetName() + "»");
                return candidate;
            }
        }
        throw new UserException(
                "Не найден лист «" + PRODUCTS_SHEET_NAME + "» с заголовками воронки продаж",
                HttpStatus.BAD_REQUEST
        );
    }

    private static HeaderMapping findHeaderMapping(Sheet sheet, List<String> warnings) {
        int headerRowIndex = findHeaderRowIndex(sheet);
        if (headerRowIndex < 0) {
            throw new UserException("Не найдена строка заголовков с «" + HEADER_NM_ID + "» и «" + HEADER_DATE + "»", HttpStatus.BAD_REQUEST);
        }
        Row headerRow = sheet.getRow(headerRowIndex);
        Map<String, Integer> columns = new HashMap<>();
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        for (Cell cell : headerRow) {
            String value = normalizeHeader(formatter.formatCellValue(cell));
            if (!value.isEmpty()) {
                columns.putIfAbsent(value, cell.getColumnIndex());
            }
        }
        Integer nmCol = findColumn(columns, HEADER_NM_ID);
        Integer dateCol = findColumn(columns, HEADER_DATE);
        if (nmCol == null || dateCol == null) {
            throw new UserException("В заголовках не найдены обязательные колонки «" + HEADER_NM_ID + "» и «" + HEADER_DATE + "»", HttpStatus.BAD_REQUEST);
        }
        if (headerRowIndex > 0) {
            warnings.add("Строка заголовков найдена на строке " + (headerRowIndex + 1));
        }
        return new HeaderMapping(
                headerRowIndex,
                nmCol,
                dateCol,
                findColumn(columns, HEADER_OPEN_CARD),
                findColumn(columns, HEADER_ADD_TO_CART),
                findColumn(columns, HEADER_ORDERS),
                findColumn(columns, HEADER_ORDERS_SUM)
        );
    }

    private static int findHeaderRowIndex(Sheet sheet) {
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        int lastRow = Math.min(sheet.getLastRowNum(), 30);
        for (int rowIndex = 0; rowIndex <= lastRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            boolean hasNm = false;
            boolean hasDate = false;
            for (Cell cell : row) {
                String header = normalizeHeader(formatter.formatCellValue(cell));
                if (header.contains(normalizeHeader(HEADER_NM_ID))) {
                    hasNm = true;
                }
                if (header.equals(normalizeHeader(HEADER_DATE))) {
                    hasDate = true;
                }
            }
            if (hasNm && hasDate) {
                return rowIndex;
            }
        }
        return -1;
    }

    private static Integer findColumn(Map<String, Integer> columns, String expectedHeader) {
        String normalizedExpected = normalizeHeader(expectedHeader);
        for (Map.Entry<String, Integer> entry : columns.entrySet()) {
            if (entry.getKey().contains(normalizedExpected)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static ParsedRow parseDataRow(Row row, HeaderMapping header, DataFormatter formatter) {
        Long nmId = readLong(row.getCell(header.nmCol()));
        LocalDate date = readDate(row.getCell(header.dateCol()), formatter);
        if (nmId == null && date == null) {
            return null;
        }
        return new ParsedRow(
                nmId,
                date,
                readInteger(row.getCell(header.openCardCol())),
                readInteger(row.getCell(header.addToCartCol())),
                readInteger(row.getCell(header.ordersCol())),
                readBigDecimal(row.getCell(header.ordersSumCol()))
        );
    }

    private static String normalizeHeader(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .replace('\u00A0', ' ')
                .replace("₽", "")
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private static Long readLong(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return Math.round(cell.getNumericCellValue());
        }
        String text = cell.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(text.replace(" ", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer readInteger(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return (int) Math.round(cell.getNumericCellValue());
        }
        String text = cell.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(text.replace(" ", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal readBigDecimal(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue()).setScale(2, RoundingMode.HALF_UP);
        }
        String text = cell.toString().trim().replace(" ", "").replace(",", ".");
        if (text.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate readDate(Cell cell, DataFormatter formatter) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            Date date = cell.getDateCellValue();
            return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        String text = formatter.formatCellValue(cell).trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.length() >= 10) {
            text = text.substring(0, 10);
        }
        try {
            return LocalDate.parse(text, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(text).toLocalDate();
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }

    private record ParsedWorkbook(List<ParsedRow> rows, List<String> warnings) {
    }

    private record ParsedRow(
            Long nmId,
            LocalDate date,
            Integer openCard,
            Integer addToCart,
            Integer orders,
            BigDecimal ordersSum
    ) {
    }

    private record HeaderMapping(
            int rowIndex,
            int nmCol,
            int dateCol,
            Integer openCardCol,
            Integer addToCartCol,
            Integer ordersCol,
            Integer ordersSumCol
    ) {
    }
}
