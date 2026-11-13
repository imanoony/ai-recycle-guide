[CmdletBinding()]
param(
    [string]$AvdName,
    [string]$DeviceSerial,
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$serverPath = Join-Path $repoRoot 'server'
$androidPath = Join-Path $repoRoot 'android'
$applicationId = 'com.example.airecycleguide'
$serverUrl = 'http://127.0.0.1:8000'

function Find-SdkTool([string]$toolName, [string]$relativePath) {
    $sdkRoot = $env:ANDROID_SDK_ROOT
    if (-not $sdkRoot) { $sdkRoot = $env:ANDROID_HOME }

    if (-not $sdkRoot) {
        $localProperties = Join-Path $androidPath 'local.properties'
        if (Test-Path -LiteralPath $localProperties) {
            $sdkLine = Get-Content -LiteralPath $localProperties | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
            if ($sdkLine) {
                $sdkRoot = ($sdkLine -replace '^sdk\.dir=', '').Replace('\\', '\').Replace('\:', ':')
            }
        }
    }

    if ($sdkRoot) {
        $candidate = Join-Path $sdkRoot $relativePath
        if (Test-Path -LiteralPath $candidate) { return $candidate }
    }

    $command = Get-Command $toolName -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    return $null
}

function Invoke-Checked([string]$program, [string[]]$arguments, [string]$workingDirectory) {
    Push-Location $workingDirectory
    try {
        & $program @arguments
        if ($LASTEXITCODE -ne 0) {
            throw "$program failed with exit code $LASTEXITCODE"
        }
    }
    finally {
        Pop-Location
    }
}

if (-not (Get-Command uv -ErrorAction SilentlyContinue)) {
    throw 'uv was not found on PATH. Install uv, then run this script again.'
}

$adb = Find-SdkTool 'adb.exe' 'platform-tools/adb.exe'
if (-not $adb) { throw 'adb was not found. Set ANDROID_SDK_ROOT or ANDROID_HOME, or add platform-tools to PATH.' }

$deviceLines = @(& $adb devices -l | Select-String '^\S+\s+device(?:\s|$)')
$connectedSerials = @($deviceLines | ForEach-Object { ($_.Line -split '\s+')[0] })
$deviceId = $null
$startedEmulator = $false

if ($DeviceSerial) {
    if ($connectedSerials -notcontains $DeviceSerial) {
        throw "Device '$DeviceSerial' is not connected to adb. Connected devices: $($connectedSerials -join ', ')"
    }
    $deviceId = $DeviceSerial
}
elseif ($connectedSerials.Count -eq 1) {
    $deviceId = $connectedSerials[0]
}
elseif ($connectedSerials.Count -gt 1) {
    throw "More than one adb device is connected. Specify one with -DeviceSerial. Connected: $($connectedSerials -join ', ')"
}
else {
    $emulator = Find-SdkTool 'emulator.exe' 'emulator/emulator.exe'
    if (-not $emulator) { throw 'No adb device is connected and emulator.exe was not found. Connect a device or configure an AVD.' }

    $availableAvds = @(& $emulator -list-avds | Where-Object { $_.Trim() })
    if (-not $AvdName) {
        if ($availableAvds.Count -eq 1) {
            $AvdName = $availableAvds[0].Trim()
        }
        elseif ($availableAvds.Count -gt 1) {
            throw "More than one AVD is available. Specify one with -AvdName. Available: $($availableAvds -join ', ')"
        }
        else {
            throw 'No adb device is connected and no AVD is configured. Connect your phone with adb wireless debugging or create an AVD.'
        }
    }
    if ($availableAvds -notcontains $AvdName) {
        throw "AVD '$AvdName' was not found. Available: $($availableAvds -join ', ')"
    }

    Write-Host "Starting Android emulator '$AvdName'..."
    Start-Process -FilePath $emulator -ArgumentList @('-avd', $AvdName) | Out-Null
    $startedEmulator = $true
}

if ($startedEmulator) {
    Write-Host 'Waiting for Android emulator to finish booting...'
    $deadline = (Get-Date).AddMinutes(5)
    $deviceId = $null
    while ((Get-Date) -lt $deadline) {
        $deviceLine = & $adb devices -l | Select-String '^emulator-\d+\s+device(?:\s|$)' | Select-Object -First 1
        if ($deviceLine) {
            $deviceId = ($deviceLine.Line -split '\s+')[0]
            $bootComplete = (& $adb -s $deviceId shell getprop sys.boot_completed 2>$null | Out-String).Trim()
            if ($bootComplete -eq '1') { break }
        }
        Start-Sleep -Seconds 3
    }
    if (-not $deviceId -or $bootComplete -ne '1') {
        throw 'The Android emulator did not finish booting within five minutes.'
    }
}
else {
    Write-Host "Using connected adb device $deviceId."
}

# ADB reverse lets both USB and Wi-Fi connected phones reach the host server via localhost.
if ($deviceId -notmatch '^emulator-\d+$') {
    Invoke-Checked $adb @('-s', $deviceId, 'reverse', 'tcp:8000', 'tcp:8000') $repoRoot
    $apiBaseUrl = 'http://127.0.0.1:8000/'
}
else {
    $apiBaseUrl = 'http://10.0.2.2:8000/'
}

# Start FastAPI in its own console unless the health endpoint already responds.
try {
    Invoke-RestMethod -Uri "$serverUrl/health" -TimeoutSec 2 | Out-Null
    Write-Host 'Reusing FastAPI server at http://127.0.0.1:8000.'
}
catch {
    Write-Host 'Starting FastAPI server on port 8000...'
    $shell = (Get-Process -Id $PID).Path
    $serverCommand = "Set-Location -LiteralPath '$serverPath'; uv run fastapi dev app/main.py --host 0.0.0.0 --port 8000"
    Start-Process -FilePath $shell -ArgumentList @('-NoExit', '-Command', $serverCommand) | Out-Null
}

Write-Host 'Waiting for FastAPI health check...'
$serverReady = $false
$deadline = (Get-Date).AddMinutes(2)
while ((Get-Date) -lt $deadline) {
    try {
        Invoke-RestMethod -Uri "$serverUrl/health" -TimeoutSec 2 | Out-Null
        $serverReady = $true
        break
    }
    catch { Start-Sleep -Seconds 2 }
}
if (-not $serverReady) { throw 'FastAPI did not become healthy at http://127.0.0.1:8000/health.' }

$apkPath = Join-Path $androidPath 'app/build/outputs/apk/debug/app-debug.apk'
if (-not $SkipBuild) {
    $gradleWrapper = Join-Path $androidPath 'gradlew.bat'
    Write-Host "Building the app with API base URL $apiBaseUrl..."
    Invoke-Checked $gradleWrapper @(':app:assembleDebug', "-PapiBaseUrl=$apiBaseUrl", '--console=plain') $androidPath
}
if (-not (Test-Path -LiteralPath $apkPath)) {
    throw "Debug APK not found at '$apkPath'. Run without -SkipBuild to build it first."
}

Write-Host "Installing the app on $deviceId..."
Invoke-Checked $adb @('-s', $deviceId, 'install', '-r', $apkPath) $repoRoot
Write-Host "Launching $applicationId on $deviceId..."
Invoke-Checked $adb @('-s', $deviceId, 'shell', 'monkey', '-p', $applicationId, '1') $repoRoot
Write-Host 'Local demo is running. FastAPI docs: http://127.0.0.1:8000/docs'
Write-Host 'FastAPI analysis is connected to the Gemini API; check the server console for provider errors if analysis fails.'