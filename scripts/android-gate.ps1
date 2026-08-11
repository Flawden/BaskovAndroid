$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root

& ".\scripts\configure-android-sdk.ps1"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

if (-not (Test-Path ".\gradle\wrapper\gradle-wrapper.jar")) {
    & ".\scripts\bootstrap-gradle-wrapper.ps1"
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

& ".\gradlew.bat" --no-daemon --stacktrace testDebugUnitTest lintDebug assembleDebug
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
