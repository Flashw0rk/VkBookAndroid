package org.example.pult;

import java.io.InputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class ExcelDataManager {

    public interface ExcelDataService {
        List<String> readHeaders(InputStream inputStream, String sheetName) throws IOException;
        Map<String, Integer> getColumnWidths(InputStream inputStream, String sheetName) throws IOException;
        List<RowDataDynamic> readAllRows(InputStream inputStream, String sheetName) throws IOException;
    }

    private final ExcelDataService excelDataService;

    public ExcelDataManager(ExcelDataService excelDataService) {
        this.excelDataService = excelDataService;
    }

    // Методы для чтения данных
    public List<String> readHeaders(InputStream inputStream, String sheetName) throws IOException {
        return excelDataService.readHeaders(inputStream, sheetName);
    }

    public Map<String, Integer> getColumnWidths(InputStream inputStream, String sheetName) throws IOException {
        return excelDataService.getColumnWidths(inputStream, sheetName);
    }

    public List<RowDataDynamic> readAllRows(InputStream inputStream, String sheetName) throws IOException {
        return excelDataService.readAllRows(inputStream, sheetName);
    }
}
