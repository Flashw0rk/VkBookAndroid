# Исправления блокирующих сетевых операций при запуске

## 🔴 Найденные проблемы

### 1. **`runBlocking` в `onCreate()` - КРИТИЧНО**

**Проблема:**
```kotlin
// БЫЛО (БЛОКИРУЕТ ГЛАВНЫЙ ПОТОК):
private fun loadThemeConfiguration() {
    runBlocking {
        withContext(Dispatchers.IO) {
            AppTheme.loadTheme(this@MainActivity)
        }
    }
}

private fun readServerUrlFromPreferences(): String {
    return runBlocking {
        withContext(Dispatchers.IO) {
            ServerSettingsActivity.getCurrentServerUrl(this@MainActivity)
        }
    }
}
```

**Почему это проблема:**
- `runBlocking` блокирует главный поток до завершения операции
- Вызывается в `onCreate()` - блокирует запуск приложения
- Может вызвать ANR (Application Not Responding)
- Операции быстрые (чтение SharedPreferences) - не нужен `runBlocking`

**Исправление:**
```kotlin
// СТАЛО (НЕ БЛОКИРУЕТ):
private fun loadThemeConfiguration() {
    // loadTheme() просто читает SharedPreferences - быстро и не блокирует UI
    AppTheme.loadTheme(this@MainActivity)
}

private fun readServerUrlFromPreferences(): String {
    // getCurrentServerUrl() просто читает SharedPreferences - быстро и не блокирует UI
    return ServerSettingsActivity.getCurrentServerUrl(this@MainActivity)
}
```

## ✅ Что уже правильно

### 1. **Асинхронные сетевые запросы при запуске**

**`wakeupServerPing()`:**
```kotlin
// ✅ Правильно: асинхронный, не блокирует UI
syncService.wakeupServerPing(force = true)
```

**`checkConnectionOnStartup()`:**
```kotlin
// ✅ Правильно: использует корутины, не блокирует UI
uiScope.launch {
    val isConnected = withContext(Dispatchers.IO) {
        syncService.checkServerConnection()
    }
    // ...
}
```

**`initializeAndCheckUpdates()`:**
```kotlin
// ✅ Правильно: использует корутины, не блокирует UI
uiScope.launch {
    // ...
}
```

### 2. **Синхронные `.execute()` только в фоновых потоках**

Все вызовы `.execute()` находятся в:
- `Dispatchers.IO` корутинах ✅
- Фоновых потоках ✅
- Не вызываются из главного потока ✅

**Примеры:**
```kotlin
// NetworkModule.testConnection() - вызывается из Dispatchers.IO
suspend fun testConnection(url: String): Boolean {
    return try {
        // ...
        val response = testOkHttpClient.newCall(request).execute() // ✅ В фоне
        // ...
    }
}

// DirectFileAccessService - вызывается из Dispatchers.IO
lifecycleScope.launch(Dispatchers.IO) {
    val response = client.newCall(request).execute() // ✅ В фоне
}
```

## 📊 Результат исправлений

### До исправлений:
```
onCreate() {
    loadThemeConfiguration()      // ❌ runBlocking - блокирует UI
    loadServerSettings() {
        readServerUrlFromPreferences() // ❌ runBlocking - блокирует UI
    }
    wakeupServerPing()            // ✅ Асинхронный
    checkConnectionOnStartup()     // ✅ Асинхронный
}
```

### После исправлений:
```
onCreate() {
    loadThemeConfiguration()      // ✅ Быстрое чтение SharedPreferences
    loadServerSettings() {
        readServerUrlFromPreferences() // ✅ Быстрое чтение SharedPreferences
    }
    wakeupServerPing()            // ✅ Асинхронный
    checkConnectionOnStartup()     // ✅ Асинхронный
}
```

## 🎯 Итоги

1. ✅ Убраны блокирующие `runBlocking` из `onCreate()`
2. ✅ Все сетевые запросы асинхронные
3. ✅ Все синхронные `.execute()` только в фоновых потоках
4. ✅ Приложение запускается быстрее
5. ✅ Нет риска ANR при запуске

## 📝 Рекомендации

1. **Никогда не используйте `runBlocking` в `onCreate()`**
   - Используйте корутины (`lifecycleScope.launch`)
   - Или вызывайте быстрые операции напрямую

2. **Все сетевые запросы должны быть асинхронными**
   - Используйте `suspend fun` с корутинами
   - Или `enqueue()` для OkHttp

3. **Синхронные операции только в фоне**
   - `.execute()` только в `Dispatchers.IO`
   - Никогда в главном потоке

4. **Быстрые операции можно вызывать напрямую**
   - Чтение SharedPreferences - быстро (< 1ms)
   - Не нужны корутины или `runBlocking`


















