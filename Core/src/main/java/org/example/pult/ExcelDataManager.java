
// ExcelDataManager.java - Обновленная версия
// Этот класс был очищен от всех зависимостей JavaFX.
// Теперь он выполняет только логику обработки данных и не содержит
// никаких элементов UI. Он работает с RowDataDynamic, который также был
// обновлен, и использует InputStream для чтения файлов, что соответствует
// рекомендациям для Android.
package org.example.pult;

import org.example.pult.RowDataDynamic;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

// Зависимости от UI-фреймворков удалены.
// Классы, которые использовались для UI, заменены на абстрактные концепции
// или полностью удалены.
public class ExcelDataManager {
    // Интерфейс для сервиса, который будет заниматься чтением Excel
    // Это абстракция, которая позволяет использовать разные реализации
    // для десктопа и Android.
    public interface ExcelDataService {
        List<String> readHeaders(InputStream inputStream, String sheetName) throws IOException;
        List<RowDataDynamic> readAllRows(InputStream inputStream, String sheetName) throws IOException;
        Map<String, Integer> getColumnWidths(InputStream inputStream, String sheetName) throws IOException;
    }

    private final ExcelDataService excelService;
    private final ExcelDataService armatureExcelService;

    // Конструктор теперь принимает только сервисы для работы с данными
    public ExcelDataManager(ExcelDataService excelService, ExcelDataService armatureExcelService) {
        this.excelService = excelService;
        this.armatureExcelService = armatureExcelService;
    }

    // Метод для загрузки данных, который возвращает List<RowDataDynamic>
    // вместо того, чтобы напрямую работать с TableView.
    // Теперь эта логика отделена от представления.
    public List<RowDataDynamic> loadTableData(InputStream inputStream, String sheetName) throws IOException {
        return excelService.readAllRows(inputStream, sheetName);
    }

    // Метод для получения заголовков
    public List<String> getTableHeaders(InputStream inputStream, String sheetName) throws IOException {
        return excelService.readHeaders(inputStream, sheetName);
    }

    // Этот метод теперь возвращает Predicate, который можно использовать
    // в любом месте для фильтрации данных.
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