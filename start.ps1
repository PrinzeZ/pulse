Write-Host "Starting P.U.L.S.E..." -ForegroundColor Cyan

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $projectRoot

Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$projectRoot'; .\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=dev,local'"

Write-Host "P.U.L.S.E is starting in a new terminal." -ForegroundColor Green
Write-Host "Server: http://localhost:8080" -ForegroundColor Yellow
Write-Host "LAN: http://192.168.1.63:8080/" -ForegroundColor Yellow