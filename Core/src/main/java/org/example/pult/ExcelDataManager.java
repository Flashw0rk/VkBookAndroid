package org.example.pult;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.Locale;

/**
 * Менеджер данных Excel.
 * Не зависит от UI и подходит для Android/Java.
 */
public class ExcelDataManager {

    /**
     * Интерфейс сервиса чтения Excel-файлов.
     * Реализации могут быть разные (Android, Desktop).
     */
    public interface ExcelDataService {
        List<String> readHeaders(InputStream inputStream, String sheetName) throws IOException;
        List<RowDataDynamic> readAllRows(InputStream inputStream, String sheetName) throws IOException;
        Map<String, Integer> getColumnWidths(InputStream inputStream, String sheetName) throws IOException;
    }

    private final ExcelDataService excelService;

    /**
     * Конструктор.
     *
     * @param excelService сервис для чтения Excel.
     */
    public ExcelDataManager(ExcelDataService excelService) {
        this.excelService = excelService;
    }

    /**
     * Читает заголовки таблицы.
     *
     * @param inputStream поток Excel-файла
     * @param sheetName   имя листа
     * @return список заголовков
     * @throws IOException если чтение не удалось
     */
    public List<String> getTableHeaders(InputStream inputStream, String sheetName) throws IOException {
        return excelService.readHeaders(inputStream, sheetName);
    }

    /**
     * Читает все строки таблицы.
     *
     * @param inputStream поток Excel-файла
     * @param sheetName   имя листа
     * @return список строк данных
     * @throws IOException если чтение не удалось
     */
    public List<RowDataDynamic> loadTableData(InputStream inputStream, String sheetName) throws IOException {
        return excelService.readAllRows(inputStream, sheetName);
    }

    /**
     * Создает предикат для фильтрации строк по поисковому тексту.
     *
     * @param searchText текст поиска
     * @return предикат для фильтрации
     */
    public Predicate<RowDataDynamic> createPredicate(String searchText) {
        return rowData -> {
            if (searchText == null || searchText.isEmpty()) {
                return true;
            }
            String lowerCaseSearch = searchText.toLowerCase(Locale.ROOT);
            return rowData.getAllProperties().stream()
                    .anyMatch(value -> value != null && value.toLowerCase(Locale.ROOT).contains(lowerCaseSearch));
        };
    }
}


