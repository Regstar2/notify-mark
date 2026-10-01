[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$Version
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if ($Version -notmatch '^v\d+\.\d+\.\d+([-.][0-9A-Za-z.-]+)?$') {
    throw "Invalid release version: $Version"
}

$versionName = $Version.Substring(1)
$buildGradlePath = Join-Path $root 'app/build.gradle'
$buildGradle = Get-Content -LiteralPath $buildGradlePath -Raw
$versionMatch = [regex]::Match($buildGradle, 'versionName\s+["'']([^"'']+)["'']')

if (-not $versionMatch.Success) {
    throw 'Unable to read versionName from app/build.gradle.'
}

if ($versionMatch.Groups[1].Value -ne $versionName) {
    throw "Tag '$Version' does not match app versionName '$($versionMatch.Groups[1].Value)'."
}

$requiredSigningVariables = @(
    'NOTIFYMARK_RELEASE_STORE_FILE',
    'NOTIFYMARK_RELEASE_STORE_PASSWORD',
    'NOTIFYMARK_RELEASE_KEY_ALIAS',
    'NOTIFYMARK_RELEASE_KEY_PASSWORD'
)

foreach ($name in $requiredSigningVariables) {
    $value = [Environment]::GetEnvironmentVariable($name)
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Required release signing variable '$name' is not configured."
    }
}

if (-not (Test-Path -LiteralPath $env:NOTIFYMARK_RELEASE_STORE_FILE -PathType Leaf)) {
    throw 'Configured NotifyMark release keystore file does not exist.'
}

$runningOnWindows = $env:OS -eq 'Windows_NT'
$gradle = if ($runningOnWindows) {
    Join-Path $root 'gradlew.bat'
}
else {
    Join-Path $root 'gradlew'
}

& $gradle clean assembleRelease --stacktrace
if ($LASTEXITCODE -ne 0) {
    throw 'Gradle release build failed.'
}

$releaseApk = Join-Path $root 'app/build/outputs/apk/release/app-release.apk'
if (-not (Test-Path -LiteralPath $releaseApk -PathType Leaf)) {
    throw "Release APK was not produced: $releaseApk"
}

& (Join-Path $PSScriptRoot 'security-audit.ps1') -ApkPath $releaseApk
if ($LASTEXITCODE -ne 0) {
    throw 'Security audit failed for the release APK.'
}

$sdkRoot = if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_SDK_ROOT)) {
    $env:ANDROID_SDK_ROOT
}
elseif (-not [string]::IsNullOrWhiteSpace($env:ANDROID_HOME)) {
    $env:ANDROID_HOME
}
elseif ($runningOnWindows -and -not [string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
    Join-Path $env:LOCALAPPDATA 'Android/Sdk'
}
else {
    $null
}

if ([string]::IsNullOrWhiteSpace($sdkRoot)) {
    throw 'Android SDK root is not configured.'
}

$buildToolsRoot = Join-Path $sdkRoot 'build-tools'
$buildTools = Get-ChildItem -LiteralPath $buildToolsRoot -Directory |
    Where-Object { $_.Name -match '^\d+(\.\d+){1,2}$' } |
    Sort-Object { [version]$_.Name } -Descending |
    Select-Object -First 1

if (-not $buildTools) {
    throw 'Android SDK build-tools were not found.'
}

$apkSignerName = if ($runningOnWindows) { 'apksigner.bat' } else { 'apksigner' }
$apkSigner = Join-Path $buildTools.FullName $apkSignerName

if (-not (Test-Path -LiteralPath $apkSigner -PathType Leaf)) {
    throw "apksigner was not found: $apkSigner"
}

& $apkSigner verify --verbose --print-certs $releaseApk
if ($LASTEXITCODE -ne 0) {
    throw 'apksigner verification failed.'
}

$dist = Join-Path $root 'dist'
New-Item -ItemType Directory -Path $dist -Force | Out-Null

$outputName = "NotifyMark-$Version.apk"
$outputApk = Join-Path $dist $outputName
Copy-Item -LiteralPath $releaseApk -Destination $outputApk -Force

$hash = (Get-FileHash -LiteralPath $outputApk -Algorithm SHA256).Hash.ToLowerInvariant()
$checksumPath = "$outputApk.sha256"
"$hash  $outputName" | Set-Content -LiteralPath $checksumPath -Encoding ascii -NoNewline

Write-Host "Release APK: $outputApk"
Write-Host "SHA-256: $hash"
