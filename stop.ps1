Write-Host "Stopping P.U.L.S.E..." -ForegroundColor Cyan

$processes = Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" |
    Where-Object { $_.CommandLine -like "*pulse*" -or $_.CommandLine -like "*spring-boot*" }

if ($processes) {
    foreach ($process in $processes) {
        Stop-Process -Id $process.ProcessId -Force
        Write-Host "Stopped Java process $($process.ProcessId)" -ForegroundColor Green
    }
} else {
    Write-Host "No P.U.L.S.E Java process found." -ForegroundColor Yellow
}