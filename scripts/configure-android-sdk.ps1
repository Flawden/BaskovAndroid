$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$LocalProperties = Join-Path $Root "local.properties"
$RequiredPlatform = "android-36"

function Get-NormalizedSdkPath([string]$Path) {
    if ([string]::IsNullOrWhiteSpace($Path)) { return $null }
    try {
        return (Resolve-Path -LiteralPath $Path -ErrorAction Stop).Path
    }
    catch {
        return $null
    }
}

function Test-AndroidSdkRoot([string]$Path) {
    if ([string]::IsNullOrWhiteSpace($Path)) { return $false }
    return (Test-Path -LiteralPath (Join-Path $Path "platforms")) -and
           (Test-Path -LiteralPath (Join-Path $Path "build-tools"))
}

function Read-LocalSdkDir {
    if (-not (Test-Path -LiteralPath $LocalProperties)) { return $null }

    foreach ($line in Get-Content -LiteralPath $LocalProperties) {
        if ($line -match '^\s*sdk\.dir\s*=\s*(.+?)\s*$') {
            $value = $Matches[1].Trim()
            $value = $value -replace '\\\\', '\'
            $value = $value -replace '\\:', ':'
            return $value
        }
    }
    return $null
}

$candidates = New-Object System.Collections.Generic.List[string]

$localSdk = Read-LocalSdkDir
if ($localSdk) { $candidates.Add($localSdk) }
if ($env:ANDROID_HOME) { $candidates.Add($env:ANDROID_HOME) }
if ($env:ANDROID_SDK_ROOT) { $candidates.Add($env:ANDROID_SDK_ROOT) }
if ($env:LOCALAPPDATA) { $candidates.Add((Join-Path $env:LOCALAPPDATA "Android\Sdk")) }
if ($env:USERPROFILE) { $candidates.Add((Join-Path $env:USERPROFILE "AppData\Local\Android\Sdk")) }

$seen = @{}
$sdk = $null
$foundRootWithoutPlatform = $null

foreach ($candidate in $candidates) {
    $normalized = Get-NormalizedSdkPath $candidate
    if (-not $normalized) { continue }
    $key = $normalized.ToLowerInvariant()
    if ($seen.ContainsKey($key)) { continue }
    $seen[$key] = $true

    if (-not (Test-AndroidSdkRoot $normalized)) { continue }

    $platformJar = Join-Path $normalized "platforms\$RequiredPlatform\android.jar"
    if (Test-Path -LiteralPath $platformJar) {
        $sdk = $normalized
        break
    }

    if (-not $foundRootWithoutPlatform) {
        $foundRootWithoutPlatform = $normalized
    }
}

if (-not $sdk) {
    if ($foundRootWithoutPlatform) {
        Write-Host "Android SDK found at: $foundRootWithoutPlatform" -ForegroundColor Yellow
        Write-Host "Missing required SDK platform: $RequiredPlatform" -ForegroundColor Yellow
        Write-Host "Open Android Studio > Tools > SDK Manager and install Android SDK Platform 36, then rerun .\scripts\android-gate.ps1." -ForegroundColor Yellow
        exit 3
    }

    $defaultPath = if ($env:LOCALAPPDATA) { Join-Path $env:LOCALAPPDATA "Android\Sdk" } else { "%LOCALAPPDATA%\Android\Sdk" }
    Write-Host "Android SDK was not found." -ForegroundColor Red
    Write-Host "Checked local.properties, ANDROID_HOME, ANDROID_SDK_ROOT, and the default Windows SDK location:" -ForegroundColor Red
    Write-Host "  $defaultPath" -ForegroundColor Red
    Write-Host "Install the Android SDK with Android Studio (Tools > SDK Manager), including Android SDK Platform 36, then rerun .\scripts\android-gate.ps1." -ForegroundColor Yellow
    exit 2
}

# Prefer the process environment over sdk.dir on Windows. Android tools read
# ANDROID_HOME, and environment variables are inherited by the Gradle child
# process. Removing sdk.dir also avoids Android Lint PropertyEscape failures
# caused by Windows drive-letter/property escaping.
if (Test-Path -LiteralPath $LocalProperties) {
    $remainingLines = @(Get-Content -LiteralPath $LocalProperties | Where-Object { $_ -notmatch '^\s*sdk\.dir\s*=' })
    if ($remainingLines.Count -gt 0) {
        $text = ($remainingLines -join "`n") + "`n"
        [System.IO.File]::WriteAllText($LocalProperties, $text, (New-Object System.Text.UTF8Encoding($false)))
    }
    else {
        Remove-Item -LiteralPath $LocalProperties -Force
    }
}

if ((Test-Path -LiteralPath $LocalProperties) -and
    (Select-String -LiteralPath $LocalProperties -Pattern '^\s*sdk\.dir\s*=' -Quiet)) {
    Write-Host "Failed to remove sdk.dir from local.properties." -ForegroundColor Red
    exit 4
}

$env:ANDROID_HOME = $sdk
Write-Host "Android SDK configured via ANDROID_HOME: $sdk"
if (Test-Path -LiteralPath $LocalProperties) {
    Write-Host "local.properties preserved without sdk.dir (gitignored)."
}
else {
    Write-Host "local.properties not required for this gate."
}
