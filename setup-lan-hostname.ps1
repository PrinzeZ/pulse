param(
    [Parameter(Mandatory=$true)]
    [string]$ServerIp
)

# Run PowerShell as Administrator on each LAN client once.
# Adds a stable local hostname so P.U.L.S.E can use:
#   http://pulse.local:8080
# The hostname survives DHCP/IP changes only if this script is rerun when
# the P.U.L.S.E server moves to a different machine/IP.

$hostsPath = "$env:SystemRoot\System32\drivers\etc\hosts"
$entry = "$ServerIp`t pulse.local"

if (-not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
    [Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Write-Host "Please run this script as Administrator." -ForegroundColor Red
    exit 1
}

$lines = @(Get-Content $hostsPath -ErrorAction Stop)
$lines = $lines | Where-Object { $_ -notmatch '(^|\s)pulse\.local(\s|$)' }
Add-Content -Path $hostsPath -Value $entry

ipconfig /flushdns | Out-Null

Write-Host ""
Write-Host "P.U.L.S.E LAN hostname configured." -ForegroundColor Green
Write-Host "pulse.local -> $ServerIp" -ForegroundColor Cyan
Write-Host "Test: http://pulse.local:8080" -ForegroundColor Cyan
