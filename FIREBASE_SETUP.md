# Настройка Firebase для VkBookAndroid

## Что добавлено

В проект добавлена поддержка Firebase Analytics, Crashlytics и Remote Config. Все компоненты **опциональны** — приложение работает как с Firebase, так и без него.

## Как подключить Firebase (опционально)

### 1. Создайте проект в Firebase Console

1. Перейдите на [https://console.firebase.google.com/](https://console.firebase.google.com/)
2. Создайте новый проект или выберите существующий
3. Добавьте Android-приложение с package name: `com.example.vkbookandroid`

### 2. Скачайте google-services.json

1. В Firebase Console скачайте файл `google-services.json`
2. Поместите его в папку `app/` (рядом с `build.gradle.kts`)
3. Файл уже добавлен в `.gitignore` для безопасности

### 3. Активируйте плагины

Раскомментируйте в `app/build.gradle.kts`:

```kotlin
plugins {
    // ...
    id("com.google.gms.google-services") version "4.4.0" apply true  // Изменить apply false → apply true
    id("com.google.firebase.crashlytics") version "2.9.9" apply true  // Изменить apply false → apply true
}
```

### 4. Синхронизируйте проект

```bash
./gradlew clean build
```

## Что работает без Firebase

Если файл `google-services.json` отсутствует или плагины не активированы:

- ✅ Приложение работает полностью
- ✅ Аналитика логируется в Logcat (тег `AnalyticsManager`)
- ✅ Ошибки логируются в Logcat (тег `CrashlyticsManager`)
- ✅ Remote Config возвращает значения по умолчанию

## Возможности аналитики

### События, которые логируются:

- **tab_opened**: Открытие вкладки (Арматура, График и т.д.)
- **theme_changed**: Смена темы оформления
- **sync_completed**: Завершение синхронизации (успех/провал, количество файлов)
- **reminder_added**: Добавление напоминания (тип: weekly, monthly и т.д.)
- **search_performed**: Выполнение поиска (длина запроса, количество результатов)

### Crashlytics:

- Автоматически отправляет краш-репорты в production
- Логирует не-фатальные ошибки через `CrashlyticsManager.recordException()`
- Добавляет контекст через `setCustomKey()` и `log()`

### Remote Config:

Параметры по умолчанию (можно изменить в Firebase Console):

- `min_supported_version`: Минимальная поддерживаемая версия приложения
- `force_update_required`: Принудительное обновление
- `maintenance_mode`: Режим обслуживания
- `show_promo_banner`: Показывать промо-баннер
- `max_search_results`: Максимум результатов поиска
- `enable_experimental_features`: Экспериментальные функции

## Тестирование

### Coverage (JaCoCo)

```bash
# Прогнать unit и UI тесты с измерением покрытия
./gradlew testDebugUnitTest connectedDebugAndroidTest jacocoTestReport

# Отчёт будет в app/build/reports/jacoco/jacocoTestReport/html/index.html
```

### Performance тесты

```bash
# Прогнать performance-тесты
./gradlew testDebugUnitTest --tests "*.PerformanceTest"
```

### Screenshot тесты

```bash
# Создать эталонные скриншоты
./gradlew executeScreenshotTests -Precord

# Проверить, что UI не изменился
./gradlew verifyDebugAndroidTestScreenshotTest
```

## Примечания

- Все Firebase-компоненты используют reflection, поэтому R8/ProGuard rules **не требуются**
- При сборке Release без `google-services.json` зависимости Firebase просто не добавляются
- Логи аналитики/crashlytics видны в Logcat даже без Firebase (для отладки)





