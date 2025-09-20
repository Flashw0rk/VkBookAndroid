package org.example.pult.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.pult.ExcelDataManager;
import org.example.pult.RowDataDynamic;

import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Сервис для чтения данных из Excel файлов.
 * Теперь реализует интерфейс ExcelDataService из ядра, что позволяет
 * использовать его как на десктопе, так и на Android.
 * Логика чтения теперь работает с InputStream, что делает ее независимой от платформы.
 */
public class ExcelService implements ExcelDataManager.ExcelDataService {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy");

    // Удалён конструктор с filePath. Теперь методы принимают InputStream.

    @Override
    public List<String> readHeaders(InputStream inputStream, String sheetName) throws IOException {
        List<String> headers = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                throw new IOException("Лист с именем '" + sheetName + "' не найден.");
            }
            Row headerRow = sheet.getRow(0);
            if (headerRow != null) {
                for (Cell cell : headerRow) {
                    headers.add(getCellValueAsString(cell));
                }
            }
        }
        return headers;
    }

    public Map<String, Integer> getColumnWidths(InputStream inputStream, String sheetName) throws IOException {
        Map<String, Integer> widths = new LinkedHashMap<>();
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                throw new IOException("Лист с именем '" + sheetName + "' не найден");
            }
            Row headerRow = sheet.getRow(0);
            if (headerRow != null) {
                for (Cell cell : headerRow) {
                    String columnName = getCellValueAsString(cell);
                    int colIndex = cell.getColumnIndex();
                    int excelWidth = sheet.getColumnWidth(colIndex);
                    int pixelWidth = (int)(excelWidth * 7.0 / 256);
                    widths.put(columnName, Math.max(50, pixelWidth));
                }
            }
        }
        return widths;
    }

    @Override
    public List<RowDataDynamic> readAllRows(InputStream inputStream, String sheetName) throws IOException {
        List<RowDataDynamic> data = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                throw new IOException("Лист с именем '" + sheetName + "' не найден.");
            }
            List<String> headers = new ArrayList<>();
            Row headerRow = sheet.getRow(0);
            if (headerRow != null) {
                for (Cell cell : headerRow) {
                    headers.add(getCellValueAsString(cell));
                }
            }
            Iterator<Row> rowIterator = sheet.iterator();
            if (rowIterator.hasNext()) {
                rowIterator.next();
            }
            List<CellRangeAddress> mergedRegions = sheet.getMergedRegions();
            while (rowIterator.hasNext()) {
                Row currentRow = rowIterator.next();
                Map<String, String> rowMap = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    Cell cell = currentRow.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                    String cellValue = getMergedCellValue(cell, mergedRegions);
                    rowMap.put(headers.get(i), cellValue);
                }
                data.add(new RowDataDynamic(rowMap));
            }
        }
        return data;
    }

    protected String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        DataFormatter formatter = new DataFormatter();
        return formatter.formatCellValue(cell);
    }

    protected String getMergedCellValue(Cell cell, List<CellRangeAddress> mergedRegions) {
        for (CellRangeAddress mergedRegion : mergedRegions) {
            if (mergedRegion.isInRange(cell.getRowIndex(), cell.getColumnIndex())) {
                Row firstRow = cell.getSheet().getRow(mergedRegion.getFirstRow());
                Cell firstCell = firstRow.getCell(mergedRegion.getFirstColumn());
                return getCellValueAsString(firstCell);
            }
        }
        return getCellValueAsString(cell);
    }
}
