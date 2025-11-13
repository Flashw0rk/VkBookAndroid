@echo off
echo ========================================
echo ПРОВЕРКА RELEASE APK С PROGUARD
echo ========================================
echo.

echo [1/3] Собираю Release APK...
call gradlew assembleRelease
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Сборка провалилась!
    exit /b 1
)

echo.
echo [2/3] Release APK собран успешно
echo Размер: 
dir /b "app\build\outputs\apk\release\app-release.apk" 2>nul && for %%A in ("app\build\outputs\apk\release\app-release.apk") do echo   %%~zA bytes

echo.
echo [3/3] ИНСТРУКЦИЯ ДЛЯ ПРОВЕРКИ:
echo ========================================
echo.
echo 1. В Android Studio:
echo    - Build ^> Select Build Variant ^> release
echo    - Run ^> Run 'app' (зеленая стрелка)
echo.
echo 2. На эмуляторе проверить:
echo    [√] Приложение запускается
echo    [√] Открываются вкладки
echo    [√] Можно создать правило
echo    [√] Настройки работают
echo.
echo 3. Если НЕ РАБОТАЕТ:
echo    - Отключить ProGuard (см. PROGUARD_DISABLE_OPTION.txt)
echo    - Размер будет 29 МБ вместо 15 МБ
echo.
echo ========================================
echo ГОТОВО! Проверьте release APK вручную
echo ========================================
pause

