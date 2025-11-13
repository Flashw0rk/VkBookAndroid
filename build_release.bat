@echo off
chcp 65001 >nul
echo ========================================
echo Building VkBookAndroid RELEASE APK
echo ========================================
cd /d "%~dp0"
call gradlew.bat assembleRelease
if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo Build SUCCESS!
    echo ========================================
    echo.
    echo APK location:
    echo app\build\outputs\apk\release\app-release.apk
    echo.
    echo ИСПРАВЛЕНИЯ в этой версии:
    echo - Защита от множественных загрузок фона
    echo - Корректная загрузка фона для тем Атом и Росатом
    echo - Правильные цвета кнопок (фоновый цвет -30%%)
    echo ========================================
) else (
    echo.
    echo ========================================
    echo Build FAILED! Error code: %ERRORLEVEL%
    echo ========================================
)
pause









