$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root

if (Test-Path ".\gradle\wrapper\gradle-wrapper.jar") {
    Write-Host "Gradle wrapper already exists."
    exit 0
}

$Gradle = Get-Command gradle -ErrorAction SilentlyContinue
if ($Gradle) {
    $VersionOutput = (& gradle --version | Out-String)
    if ($VersionOutput -match "Gradle 8\.13") {
        gradle wrapper --gradle-version 8.13 --distribution-type bin
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        Write-Host "Gradle wrapper generated from installed Gradle 8.13."
        exit 0
    }
}

$Temp = Join-Path ([System.IO.Path]::GetTempPath()) "baskov-gradle-8.13"
$Zip = Join-Path $Temp "gradle-8.13-bin.zip"
$Home = Join-Path $Temp "gradle-8.13"
New-Item -ItemType Directory -Force -Path $Temp | Out-Null

if (-not (Test-Path (Join-Path $Home "bin\gradle.bat"))) {
    Write-Host "Downloading official Gradle 8.13 distribution..."
    Invoke-WebRequest "https://services.gradle.org/distributions/gradle-8.13-bin.zip" -OutFile $Zip
    Expand-Archive -Path $Zip -DestinationPath $Temp -Force
}

& (Join-Path $Home "bin\gradle.bat") wrapper --gradle-version 8.13 --distribution-type bin
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host "Gradle wrapper generated. Next: .\scripts\android-gate.ps1"
