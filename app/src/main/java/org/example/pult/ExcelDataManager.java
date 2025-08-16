package org.example.pult;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.Predicate;

/**
 * Класс для управления чтением данных из Excel.
 * Поддерживает два сервиса: основной Excel и Excel с арматурой.
 */
public class ExcelDataManager {

    /**
     * Интерфейс для сервиса чтения Excel-файлов.
     * Для Android будет своя реализация, для Desktop — своя.
     */
    public interface ExcelDataService {
        List<String> readHeaders(InputStream inputStream, String sheetName) throws IOException;
        List<RowDataDynamic> readAllRows(InputStream inputStream, String sheetName) throws IOException;
    }

    private final ExcelDataService excelService;
    private final ExcelDataService armatureExcelService;

    /**
     * Конструктор для работы с двумя сервисами.
     *
     * @param excelService         — сервис для основного Excel.
     * @param armatureExcelService — сервис для Excel с арматурой.
     */
    public ExcelDataManager(ExcelDataService excelService, ExcelDataService armatureExcelService) {
        this.excelService = excelService;
        this.armatureExcelService = armatureExcelService;
    }

    /**
     * Чтение заголовков основной таблицы.
     */
    public List<String> getTableHeaders(InputStream inputStream, String sheetName) throws IOException {
        return excelService.readHeaders(inputStream, sheetName);
    }

    /**
     * Чтение заголовков таблицы арматуры.
     */
    public List<String> getArmatureHeaders(InputStream inputStream, String sheetName) throws IOException {
        return armatureExcelService.readHeaders(inputStream, sheetName);
    }

    /**
     * Чтение всех строк основной таблицы.
     */
    public List<RowDataDynamic> loadTableData(InputStream inputStream, String sheetName) throws IOException {
        return excelService.readAllRows(inputStream, sheetName);
    }

    /**
     * Чтение всех строк таблицы арматуры.
     */
    public List<RowDataDynamic> loadArmatureData(InputStream inputStream, String sheetName) throws IOException {
        return armatureExcelService.readAllRows(inputStream, sheetName);
    }

    /**
     * Фильтрация строк по тексту (общая для обеих таблиц).
     */
    public Predicate<RowDataDynamic> createPredicate(String searchText) {
        return rowData -> {
            if (searchText == null || searchText.isEmpty()) {
                return true;
            }
            String lowerCaseSearchText = searchText.toLowerCase();
            return rowData.getAllProperties().stream()
                    .anyMatch(value -> value != null && value.toLowerCase().contains(lowerCaseSearchText));
        };
    }
}
