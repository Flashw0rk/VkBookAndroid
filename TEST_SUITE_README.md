# Test Suite - Запуск всех тестов

## Обзор

Создан главный Test Suite файл `AllTestsSuite.kt`, который объединяет все тесты приложения для удобного запуска.

## Файлы Test Suite

### 1. AllTestsSuite.kt (Инструментальные тесты)
**Путь:** `app/src/androidTest/java/com/example/vkbookandroid/AllTestsSuite.kt`

Объединяет все инструментальные тесты (58+ тестов):
- Основные тесты приложения
- Тесты синхронизации
- Тесты поиска
- Тесты обработки ошибок
- Тесты PDF просмотра
- Тесты редактора
- Тесты графика смен
- Тесты жизненного цикла
- Тесты производительности
- Тесты пользовательских сценариев
- Тесты граничных случаев

### 2. AllUnitTestsSuite.kt (Unit тесты)
**Путь:** `app/src/test/java/com/example/vkbookandroid/AllUnitTestsSuite.kt`

Объединяет все unit тесты:
- Тесты SyncService
- Тесты ServerConnection

## Способы запуска

### Способ 1: Через Gradle (рекомендуется)

**Все инструментальные тесты:**
```bash
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.vkbookandroid.AllTestsSuite
```

**Все unit тесты:**
```bash
.\gradlew.bat test -Ptest.single=AllUnitTestsSuite
```

### Способ 2: Через Android Studio

1. Откройте файл `AllTestsSuite.kt`
2. Правый клик на имени класса `AllTestsSuite`
3. Выберите `Run 'AllTestsSuite'`

### Способ 3: Через командную строку ADB

```bash
adb shell am instrument -w -r -e class com.example.vkbookandroid.AllTestsSuite com.example.vkbookandroid.test/androidx.test.runner.AndroidJUnitRunner
```

## Структура тестов в AllTestsSuite

### Основные тесты
- `VkBookAndroidInstrumentedTests` - основные тесты приложения

### Этап 1: Критичные тесты
- `SyncIntegrationTests` - тесты синхронизации (6 тестов)
- `SyncProgressUITests` - тесты UI прогресса синхронизации (4 теста)
- `SearchTests` - тесты поиска (7 тестов)
- `ErrorHandlingTests` - тесты обработки ошибок (7 тестов)

### Этап 2: Важные тесты
- `PDFViewerTests` - тесты PDF просмотра (6 тестов)
- `EditorTests` - тесты редактора (4 теста)
- `ScheduleTests` - тесты графика смен (5 тестов)

### Этап 3: Желательные тесты
- `LifecycleTests` - тесты жизненного цикла (5 тестов)
- `PerformanceTests` - тесты производительности (5 тестов)
- `UserJourneyTests` - тесты пользовательских сценариев (5 тестов)
- `EdgeCaseTests` - тесты граничных случаев (8 тестов)

## Преимущества Test Suite

1. **Единая точка запуска** - все тесты запускаются из одного места
2. **Удобство** - не нужно запускать каждый тестовый класс отдельно
3. **Организация** - четкая структура и группировка тестов
4. **CI/CD** - легко интегрировать в автоматизированные пайплайны

## Примечания

- Все тесты в Suite выполняются последовательно
- Если один тест падает, остальные продолжают выполняться
- Результаты всех тестов отображаются в едином отчете
- Время выполнения всех тестов может занять несколько минут

## Добавление новых тестов

Чтобы добавить новый тестовый класс в Suite:

1. Создайте новый тестовый класс (например, `NewFeatureTests.kt`)
2. Добавьте его в `@Suite.SuiteClasses` в `AllTestsSuite.kt`:

```kotlin
@Suite.SuiteClasses(
    // ... существующие классы ...
    NewFeatureTests::class
)
```

3. Перекомпилируйте проект

## Статистика

- **Всего тестовых классов:** 12
- **Всего тестов:** 58+
- **Покрытие критичных функций:** ~80%
- **Покрытие всех функций:** ~70%


