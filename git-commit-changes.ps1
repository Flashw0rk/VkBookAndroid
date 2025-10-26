# PowerShell скрипт для коммита изменений Excel форматирования
# Использование: .\git-commit-changes.ps1

Write-Host "=== Git Commit Script: Excel Format Fix ===" -ForegroundColor Cyan
Write-Host ""

# Проверка, что мы в Git репозитории
if (-not (Test-Path ".git")) {
    Write-Host "⚠️  Git репозиторий не найден. Инициализировать?" -ForegroundColor Yellow
    $response = Read-Host "Введите 'yes' для инициализации"
    if ($response -eq "yes") {
        git init
        Write-Host "✅ Git репозиторий инициализирован" -ForegroundColor Green
    } else {
        Write-Host "❌ Отменено" -ForegroundColor Red
        exit
    }
}

Write-Host "📁 Текущий статус Git:" -ForegroundColor Yellow
git status --short
Write-Host ""

# Проверка наличия изменений
Write-Host "📝 Добавление файлов..." -ForegroundColor Yellow
$files = @(
    "app/src/main/java/com/example/vkbookandroid/editor/ArmaturesExcelWriter.kt",
    ".gitignore"
)

foreach ($file in $files) {
    if (Test-Path $file) {
        git add $file
        Write-Host "  ✅ Добавлен: $file" -ForegroundColor Green
    } else {
        Write-Host "  ⚠️  Файл не найден: $file" -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "📋 Создание коммита..." -ForegroundColor Yellow

# Читаем commit message из файла
$commitMessage = Get-Content "COMMIT_MESSAGE.txt" -Raw

# Создаем коммит
git commit -m $commitMessage

if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ Коммит создан успешно!" -ForegroundColor Green
    Write-Host ""
    Write-Host "📊 Последний коммит:" -ForegroundColor Yellow
    git log -1 --oneline
    Write-Host ""
    
    # Спрашиваем о push
    Write-Host "🚀 Push изменения на GitHub?" -ForegroundColor Yellow
    $response = Read-Host "Введите 'yes' для push"
    if ($response -eq "yes") {
        Write-Host "Pushing to remote..." -ForegroundColor Yellow
        git push
        if ($LASTEXITCODE -eq 0) {
            Write-Host "✅ Изменения загружены на GitHub!" -ForegroundColor Green
        } else {
            Write-Host "⚠️  Ошибка при push. Проверьте настройки remote:" -ForegroundColor Red
            git remote -v
        }
    }
} else {
    Write-Host "❌ Ошибка при создании коммита" -ForegroundColor Red
    Write-Host "Возможные причины:" -ForegroundColor Yellow
    Write-Host "  - Нет изменений для коммита" -ForegroundColor White
    Write-Host "  - Файлы уже закоммичены" -ForegroundColor White
    Write-Host "  - Нужно настроить git config (user.name, user.email)" -ForegroundColor White
}

Write-Host ""
Write-Host "✅ Скрипт завершен" -ForegroundColor Cyan




