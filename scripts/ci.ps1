[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$isWindows = $env:OS -eq 'Windows_NT'
$gradle = if ($isWindows) {
    Join-Path $root 'gradlew.bat'
}
else {
    Join-Path $root 'gradlew'
}

if (-not (Test-Path -LiteralPath $gradle -PathType Leaf)) {
    throw "Gradle wrapper was not found: $gradle"
}

& $gradle clean assembleDebug testDebugUnitTest --stacktrace
if ($LASTEXITCODE -ne 0) {
    throw 'Gradle debug build or unit tests failed.'
}

$debugApk = Join-Path $root 'app/build/outputs/apk/debug/app-debug.apk'
if (-not (Test-Path -LiteralPath $debugApk -PathType Leaf)) {
    throw "Debug APK was not produced: $debugApk"
}

& (Join-Path $PSScriptRoot 'security-audit.ps1') -ApkPath $debugApk
if ($LASTEXITCODE -ne 0) {
    throw 'Security audit failed for the debug APK.'
}
