@echo off
chcp 65001 >nul
echo ╔════════════════════════════════════════════════════════════╗
echo ║         Изменение описания последнего коммита              ║
echo ╚════════════════════════════════════════════════════════════╝
echo.

echo Выберите действие:
echo.
echo 1. Изменить описание последнего коммита (НЕ запушен)
echo 2. Изменить описание + force push (УЖЕ запушен)
echo 3. Просто открыть редактор
echo.
set /p choice="Ваш выбор (1-3): "

if "%choice%"=="1" (
    echo.
    echo [1/2] Изменение коммита с новым описанием...
    git commit --amend -F COMMIT_MESSAGE.txt
    
    echo.
    echo [2/2] Готово!
    echo.
    echo ✅ Коммит изменен локально
    echo.
    echo Следующий шаг:
    echo   git push origin main
    echo.
)

if "%choice%"=="2" (
    echo.
    echo ⚠️  ВНИМАНИЕ: Force push перезапишет историю на GitHub!
    echo.
    set /p confirm="Вы уверены? (yes/no): "
    
    if "%confirm%"=="yes" (
        echo.
        echo [1/2] Изменение коммита...
        git commit --amend -F COMMIT_MESSAGE.txt
        
        echo.
        echo [2/2] Force push на GitHub...
        git push --force-with-lease origin main
        
        echo.
        echo ✅ Коммит изменен и запушен!
    ) else (
        echo.
        echo ❌ Отменено
    )
)

if "%choice%"=="3" (
    echo.
    echo Открываем редактор для изменения...
    git commit --amend
    
    echo.
    echo ✅ Готово! Проверьте изменения: git log -1
)

echo.
pause

