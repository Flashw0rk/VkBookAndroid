@echo off
chcp 65001 >nul
echo ╔════════════════════════════════════════════════════════════╗
echo ║      СБОРКА НОВОГО RELEASE APK                            ║
echo ╚════════════════════════════════════════════════════════════╝
echo.

cd ..

echo [1/4] Очистка предыдущих сборок...
call gradlew clean

echo.
echo [2/4] Сборка Release APK с ProGuard...
call gradlew assembleRelease

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ОШИБКА] Сборка провалилась!
    pause
    exit /b 1
)

echo.
echo [3/4] Копирование APK в папку релиза...
copy /Y "app\build\outputs\apk\release\app-release.apk" "Release_v1.0\VkBookAndroid-v1.0-release.apk"

echo.
echo [4/4] Информация о файле:
dir "Release_v1.0\VkBookAndroid-v1.0-release.apk"

echo.
echo ╔════════════════════════════════════════════════════════════╗
echo ║              ✅ СБОРКА ЗАВЕРШЕНА УСПЕШНО!                  ║
echo ╚════════════════════════════════════════════════════════════╝
echo.
echo Файл готов: Release_v1.0\VkBookAndroid-v1.0-release.apk
echo.
echo Mapping файл (для Play Console):
echo app\build\outputs\mapping\release\mapping.txt
echo.
pause

