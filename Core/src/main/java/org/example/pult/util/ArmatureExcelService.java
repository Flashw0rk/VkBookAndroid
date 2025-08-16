package org.example.pult.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.pult.RowDataDynamic;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Сервис для чтения Excel с арматурой.
 * Работает и с Android (через InputStream), и с Java.
 */
public class ArmatureExcelService extends ExcelService {

    // Новый метод для Android — читает данные из InputStream
    public List<RowDataDynamic> readAllRows(InputStream inputStream, String sheetName) throws IOException {
        List<RowDataDynamic> rows = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                throw new IOException("Лист с именем '" + sheetName + "' не найден");
            }

            // Заголовки из первой строки
            List<String> headers = readHeadersFromWorkbook(workbook, sheetName);
            List<CellRangeAddress> mergedRegions = sheet.getMergedRegions();

            Iterator<Row> rowIterator = sheet.iterator();
            if (rowIterator.hasNext()) {
                rowIterator.next(); // пропускаем заголовки
            }

            while (rowIterator.hasNext()) {
                Row currentRow = rowIterator.next();
                Map<String, String> rowMap = new LinkedHashMap<>();

                for (int i = 0; i < headers.size(); i++) {
                    Cell cell = currentRow.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                    String cellValue = getMergedCellValue(cell, mergedRegions);
                    rowMap.put(headers.get(i), cellValue);
                }
                rows.add(new RowDataDynamic(rowMap));
            }
        }

        return rows;
    }

    // Чтение заголовков из Workbook
    private List<String> readHeadersFromWorkbook(Workbook workbook, String sheetName) {
        List<String> headers = new ArrayList<>();
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet != null && sheet.getPhysicalNumberOfRows() > 0) {
            Row headerRow = sheet.getRow(0);
            for (Cell cell : headerRow) {
                headers.add(cell.toString().trim());
            }
        }
        return headers;
    }

    // Переопределяем с таким же уровнем доступа, как в ExcelService
    @Override
    protected String getMergedCellValue(Cell cell, List<CellRangeAddress> mergedRegions) {
        if (cell == null) return "";
        return cell.toString();
    }
}
