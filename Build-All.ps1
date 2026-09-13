# PowerShell 5.1+; compiler prerequisites are documented in START_HERE_BUILD_STATUS.md.
[CmdletBinding()]
param([ValidateSet('all','android','firmware')][string]$Target = 'all', [switch]$Check, [switch]$Offline)
$ErrorActionPreference = 'Stop'
$Launcher = Get-Command py -ErrorAction SilentlyContinue
$Prefix = @('-3')
if (-not $Launcher) { $Launcher = Get-Command python -ErrorAction SilentlyContinue; $Prefix = @() }
if (-not $Launcher) { throw 'Install Python 3.10+ and reopen PowerShell.' }
$ScriptArgs = @((Join-Path $PSScriptRoot 'tools\build_binaries.py'), '--target', $Target)
if ($Check) { $ScriptArgs += '--check' }
if ($Offline) { $ScriptArgs += '--offline' }
& $Launcher.Source @Prefix @ScriptArgs
if ($LASTEXITCODE -ne 0) { throw "Build/check did not complete. Exit code: $LASTEXITCODE. Read the printed diagnostic; no successful build is implied." }
