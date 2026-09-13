# Requires JDK 17, Gradle 8.11.1, Android SDK 36 and build-tools 35.0.0.
param([switch]$Offline)
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$Gradle = if ($env:GRADLE_BIN) { $env:GRADLE_BIN } else { Join-Path $Root '.tools\gradle-8.11.1\bin\gradle.bat' }
if (-not (Test-Path $Gradle)) { throw 'Unzip the verified Gradle 8.11.1 binary distribution into .tools, or set GRADLE_BIN.' }
if (-not $env:ANDROID_HOME) { throw 'Set ANDROID_HOME to your prepared Android SDK.' }
$Arguments = @('-p', (Join-Path $Root 'android'))
if ($Offline) { $Arguments += '--offline' }
$Arguments += ':app:assembleDebug'
& $Gradle @Arguments
if ($LASTEXITCODE -ne 0) { throw "Android build failed with exit $LASTEXITCODE" }
Write-Host (Join-Path $Root 'android\app\build\outputs\apk\debug\app-debug.apk')
