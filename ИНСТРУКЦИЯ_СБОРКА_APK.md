# Инструкция по созданию установочного APK файла

## Способ 1: Через батник (рекомендуется)

1. Откройте командную строку в корне проекта
2. Запустите:
   ```
   build_release_apk.bat
   ```
3. Дождитесь завершения сборки (5-10 минут)
4. APK будет создан в: `app\build\outputs\apk\release\app-release.apk`

## Способ 2: Через Gradle напрямую

1. Откройте командную строку в корне проекта
2. Выполните:
   ```
   gradlew.bat clean
   gradlew.bat assembleRelease
   ```
3. APK будет создан в: `app\build\outputs\apk\release\app-release.apk`

## Способ 3: Через Android Studio

1. Откройте проект в Android Studio
2. Меню: `Build -> Build Bundle(s) / APK(s) -> Build APK(s)`
3. Или: `Build -> Generate Signed Bundle / APK -> APK -> release`
4. APK будет создан в: `app\build\outputs\apk\release\app-release.apk`

## Проверка результата

После сборки проверьте наличие файла:
```
app\build\outputs\apk\release\app-release.apk
```

Размер файла должен быть примерно 10-20 MB.

## Примечания

- Сборка выполняется **без подписи** (для тестирования)
- ProGuard отключен (для стабильности)
- Версия: 1.0 (versionCode: 1, versionName: "1.0")
- Сборка может занять 5-10 минут в зависимости от компьютера

## Если сборка не удается

1. Проверьте наличие Java JDK
2. Проверьте наличие Android SDK
3. Проверьте логи сборки на наличие ошибок
4. Убедитесь, что все зависимости загружены (`gradlew.bat --refresh-dependencies`)




