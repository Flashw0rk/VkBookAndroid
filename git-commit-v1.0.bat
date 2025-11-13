@echo off
chcp 65001 >nul
echo ╔════════════════════════════════════════════════════════════╗
echo ║         Git Commit для VkBookAndroid v1.0                  ║
echo ╚════════════════════════════════════════════════════════════╝
echo.

echo [1/5] Добавление файлов в коммит...
git add .

echo.
echo [2/5] Проверка изменений...
git status

echo.
echo [3/5] Создание коммита с описанием...
git commit -F COMMIT_MESSAGE.txt

echo.
echo [4/5] Добавление тега версии...
git tag -a v1.0 -m "Release version 1.0"

echo.
echo [5/5] Готово!
echo.
echo ════════════════════════════════════════════════════════════
echo.
echo ✅ Коммит создан локально
echo.
echo Следующие шаги:
echo   1. Проверить: git log --oneline -3
echo   2. Запушить: git push origin main
echo   3. Запушить тег: git push origin v1.0
echo.
echo Если нужно изменить описание:
echo   git commit --amend
echo.
echo ════════════════════════════════════════════════════════════
pause

