# Closes any running Polylingy (a dev run started by Gradle, or the packaged/installed
# Polylingy.exe), then builds and starts the current code with `gradlew :app:run`.
# Other Java processes (Gradle daemons, IDEs) are left alone.
#
# Usage: powershell -NoProfile -File scripts\relaunch.ps1

$root = Split-Path $PSScriptRoot -Parent

$devRuns = Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" |
    Where-Object { $_.CommandLine -like '*com.polylingy.app.MainKt*' }
foreach ($p in $devRuns) {
    Write-Host "Stopping dev run (pid $($p.ProcessId))"
    Stop-Process -Id $p.ProcessId -Force
}

foreach ($p in Get-Process Polylingy -ErrorAction SilentlyContinue) {
    Write-Host "Stopping Polylingy.exe (pid $($p.Id)) from $($p.Path)"
    Stop-Process -Id $p.Id -Force
}

Set-Location $root
& .\gradlew.bat :app:run --console=plain
