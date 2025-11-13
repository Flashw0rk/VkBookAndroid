@echo off
chcp 65001 >nul
echo ╔════════════════════════════════════════════════════════════╗
echo ║         VkBookAndroid v1.0 - УСТАНОВКА                     ║
echo ╚════════════════════════════════════════════════════════════╝
echo.

echo [1/3] Проверка подключения устройства...
adb devices
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ОШИБКА] ADB не найден!
    echo.
    echo Установите Android SDK Platform Tools:
    echo https://developer.android.com/tools/releases/platform-tools
    echo.
    pause
    exit /b 1
)

echo.
echo [2/3] Установка VkBookAndroid-v1.0-release.apk...
adb install -r VkBookAndroid-v1.0-release.apk

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ╔════════════════════════════════════════════════════════════╗
    echo ║              ✅ УСТАНОВКА ЗАВЕРШЕНА УСПЕШНО!               ║
    echo ╚════════════════════════════════════════════════════════════╝
    echo.
    echo Приложение установлено на устройство.
    echo Откройте VkBookAndroid на вашем телефоне/планшете.
    echo.
) else (
    echo.
    echo [ОШИБКА] Установка не удалась!
    echo.
    echo Возможные причины:
    echo - Устройство не подключено
    echo - Отладка по USB отключена
    echo - Нехватка места на устройстве
    echo.
    echo См. файл УСТАНОВКА.txt для альтернативных способов.
)

echo.
pause

