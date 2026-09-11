# PowerShell script to monitor live TeamSpeak 6 & Android WebRTC negotiation
param(
    [string]$DeviceIp = "192.168.10.209:39377"
)

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$tsLogsDir = "$env:LOCALAPPDATA\TeamSpeak\Logs\Default"

Write-Host "=== Iniciando Monitor de Transmissao TS6 + Android ===" -ForegroundColor Cyan
Write-Host "Conectando ao dispositivo ADB: $DeviceIp..." -ForegroundColor Gray
& $adb connect $DeviceIp | Out-Null

$latestTsLog = Get-ChildItem -Path $tsLogsDir -Filter "ts5client_*.log" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($latestTsLog) {
    Write-Host "Monitorando log do TS6: $($latestTsLog.Name)" -ForegroundColor Green
} else {
    Write-Host "Nenhum log do TS6 encontrado em $tsLogsDir" -ForegroundColor Yellow
}

Write-Host "`n[Aguardando eventos de video... Pressione Ctrl+C para sair]`n" -ForegroundColor Cyan

# Clear logcat buffer for clean stream
& $adb -s $DeviceIp logcat -c

# Monitor logcat in a background job or loop
& $adb -s $DeviceIp logcat -v time WebRtcManager:I WebRtcVideoView:I TeamSpeakService:I *:S
