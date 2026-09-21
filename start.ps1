Write-Host "Starting P.U.L.S.E..." -ForegroundColor Cyan

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $projectRoot

$lanIp = (Get-NetIPAddress -AddressFamily IPv4 -InterfaceAlias "Wi-Fi" -ErrorAction SilentlyContinue |
    Where-Object { $_.IPAddress -notlike "127.*" -and $_.PrefixOrigin -ne "WellKnown" } |
    Select-Object -First 1 -ExpandProperty IPAddress)
if (-not $lanIp) {
    $lanIp = (Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
        Where-Object { $_.IPAddress -notlike "127.*" -and $_.IPAddress -notlike "169.254.*" } |
        Select-Object -First 1 -ExpandProperty IPAddress)
}

Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$projectRoot'; .\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=dev,local'"

Write-Host "P.U.L.S.E is starting in a new terminal." -ForegroundColor Green
Write-Host "This node works locally even when Supabase/Internet is unavailable." -ForegroundColor Yellow
Write-Host "Server: http://localhost:8080" -ForegroundColor Yellow
if ($lanIp) {
    Write-Host "LAN: http://$lanIp`:8080" -ForegroundColor Yellow
}
