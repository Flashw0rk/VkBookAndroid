@echo off
chcp 65001 >nul
echo ========================================
echo Building VkBookAndroid Debug APK
echo ========================================
cd /d "%~dp0"
call gradlew.bat assembleDebug
if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo Build SUCCESS!
    echo APK: app\build\outputs\apk\debug\app-debug.apk
    echo ========================================
    echo.
    echo ИСПРАВЛЕНО v1.0.2:
    echo - Защита от множественных загрузок во ВСЕХ фрагментах
    echo - Исправлен isFragmentReady^(^) - теперь не блокирует применение темы
    echo - Тема применяется даже к невидимым фрагментам
    echo - ArmatureFragment и DataFragment: упрощена проверка готовности
    echo - Фон для тем Атом и Росатом загружается стабильно
    echo ========================================
) else (
    echo.
    echo ========================================
    echo Build FAILED! Error code: %ERRORLEVEL%
    echo ========================================
)
pause

