@echo off
cd /d "%~dp0"
echo ========================================
echo Запуск сборки релизного APK...
echo ========================================
call gradlew.bat clean assembleRelease
echo.
echo ========================================
echo Проверка результата...
echo ========================================
if exist "app\build\outputs\apk\release\app-release.apk" (
    echo.
    echo ✅ РЕЛИЗНЫЙ APK УСПЕШНО СОЗДАН!
    echo.
    echo 📍 Расположение файла:
    echo    %cd%\app\build\outputs\apk\release\app-release.apk
    echo.
    for %%F in ("app\build\outputs\apk\release\app-release.apk") do (
        echo 📦 Размер: %%~zF байт
        echo 📅 Дата: %%~tF
    )
) else (
    echo.
    echo ❌ APK файл не найден!
    echo    Проверьте ошибки сборки выше.
)
echo.
pause
