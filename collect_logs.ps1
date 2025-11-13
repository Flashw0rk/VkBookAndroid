# Скрипт для сбора логов График проверок

Write-Host "Очистка старых логов..."
adb logcat -c

Write-Host ""
Write-Host "==================================================================="
Write-Host "Теперь выполните следующие действия в приложении:"
Write-Host "1. Скачайте обновления с сервера"
Write-Host "2. Откройте вкладку 'График проверок'"
Write-Host "==================================================================="
Write-Host ""
Write-Host "Нажмите любую клавишу, когда будете готовы собрать логи..."
$null = $Host.UI.RawUI.ReadKey('NoEcho,IncludeKeyDown')

Write-Host ""
Write-Host "Собираю логи..."
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$logFile = "график_проверок_логи_$timestamp.txt"
adb logcat -d -s ChecksSchedule:D UpdatesFileService:D MainActivity:D | Out-File -Encoding UTF8 $logFile

Write-Host ""
Write-Host "======================================"
Write-Host "✅ Логи сохранены в файл: $logFile"
Write-Host "======================================"
Write-Host ""
Write-Host "Отправьте этот файл разработчику"
Write-Host ""



















