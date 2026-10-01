[CmdletBinding()]
param(
    [string]$ApkPath = "",
    [switch]$SkipHistory
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$failures = [System.Collections.Generic.List[string]]::new()
$warnings = [System.Collections.Generic.List[string]]::new()

function Add-Failure {
    param([string]$Message)
    $failures.Add($Message)
}

function Add-Warning {
    param([string]$Message)
    $warnings.Add($Message)
}

function Get-AndroidAttribute {
    param([System.Xml.XmlElement]$Node, [string]$Name)
    return $Node.GetAttribute($Name, "http://schemas.android.com/apk/res/android")
}

function Test-SourceManifest {
    param([string]$RepoRoot)

    [xml]$manifest = Get-Content -LiteralPath (Join-Path $RepoRoot "app/src/main/AndroidManifest.xml") -Raw
    $application = $manifest.manifest.application
    $permissions = @($manifest.manifest."uses-permission" | ForEach-Object { Get-AndroidAttribute $_ "name" })

    if ($permissions -contains "android.permission.INTERNET") {
        Add-Failure "AndroidManifest.xml requests android.permission.INTERNET."
    }

    foreach ($permission in @(
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.MANAGE_EXTERNAL_STORAGE",
        "android.permission.CAMERA",
        "android.permission.RECORD_AUDIO",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.READ_CONTACTS",
        "android.permission.WRITE_CONTACTS",
        "android.permission.READ_CALL_LOG",
        "android.permission.READ_SMS",
        "android.permission.SEND_SMS"
    )) {
        if ($permissions -contains $permission) {
            Add-Failure "Unexpected sensitive permission in source manifest: $permission"
        }
    }

    if ((Get-AndroidAttribute $application "allowBackup") -ne "false") {
        Add-Failure "android:allowBackup must be false."
    }
    if ((Get-AndroidAttribute $application "dataExtractionRules") -ne "@xml/backup_rules") {
        Add-Failure "android:dataExtractionRules must point to @xml/backup_rules."
    }
    if ((Get-AndroidAttribute $application "fullBackupContent") -ne "@xml/backup_rules_legacy") {
        Add-Failure "android:fullBackupContent must point to @xml/backup_rules_legacy."
    }

    $expectedExported = @(".ui.MainActivity", ".ui.tiles.TasksTileService", ".ui.tiles.NewTaskTileService")
    $componentNodes = $manifest.SelectNodes(
        "/manifest/application/activity | /manifest/application/activity-alias | " +
        "/manifest/application/service | /manifest/application/receiver | /manifest/application/provider"
    )

    foreach ($node in $componentNodes) {
        if ((Get-AndroidAttribute $node "exported") -ne "true") {
            continue
        }
        $name = Get-AndroidAttribute $node "name"
        if ($expectedExported -notcontains $name) {
            Add-Failure "Unexpected exported component in source manifest: $name"
        }
        if ($name -like "*.tiles.*TileService" -and
            (Get-AndroidAttribute $node "permission") -ne "android.permission.BIND_QUICK_SETTINGS_TILE") {
            Add-Failure "Exported Quick Settings tile lacks BIND_QUICK_SETTINGS_TILE: $name"
        }
    }
}

function Test-BackupRuleFile {
    param([string]$Path, [string[]]$Sections)

    [xml]$xml = Get-Content -LiteralPath $Path -Raw
    $domains = @("root","file","database","sharedpref","external","device_root","device_file","device_database","device_sharedpref")

    foreach ($section in $Sections) {
        $nodes = if ($section -eq "legacy") {
            @($xml."full-backup-content".exclude)
        } else {
            @($xml."data-extraction-rules".$section.exclude)
        }

        foreach ($domain in $domains) {
            $matched = $nodes | Where-Object {
                $_.domain -eq $domain -and ($_.path -eq "." -or $_.path -eq "./")
            }
            if (-not $matched) {
                Add-Failure "Backup rules do not exclude domain '$domain' in section '$section'."
            }
        }
    }
}

function Test-GitHistory {
    param([string]$RepoRoot)

    Push-Location $RepoRoot
    try {
        $trackedNames = @(git log --all --name-only --pretty=format: |
            Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
            Sort-Object -Unique)

        $sensitivePathPatterns = @(
            '(^|/)local\.properties$',
            '(^|/)keystore\.properties$',
            '(^|/)\.env(\..+)?$',
            '\.(jks|keystore|p12|pfx)$',
            '(^|/)(id_rsa|id_dsa|id_ecdsa|id_ed25519)$',
            '\.(pem|key)$',
            '(^|/)google-services\.json$',
            '(^|/)(credentials|secrets?)(\.[^/]+)?$',
            '(^|/)\.obsidian/',
            '(^|/)(dump|backup)[^/]*\.(sql|zip|7z|tar|gz)$'
        )

        foreach ($path in $trackedNames) {
            foreach ($pattern in $sensitivePathPatterns) {
                if ($path -match $pattern) {
                    Add-Failure "Sensitive-looking path exists in Git history: $path"
                    break
                }
            }
        }

        $commits = @(git rev-list --all)
        $secretPatterns = @(
            '-----BEGIN[[:space:]]+((RSA|EC|DSA|OPENSSH)[[:space:]]+)?PRIVATE KEY-----',
            'AKIA[0-9A-Z]{16}',
            'AIza[0-9A-Za-z_-]{35}',
            'gh[pousr]_[0-9A-Za-z]{20,}',
            '(api[_-]?key|access[_-]?token|auth[_-]?token|client[_-]?secret|password|passwd)[[:space:]]*[:=][[:space:]]*"[^"[:space:]]{8,}"',
            "(api[_-]?key|access[_-]?token|auth[_-]?token|client[_-]?secret|password|passwd)[[:space:]]*[:=][[:space:]]*'[^'[:space:]]{8,}'",
            'https?://[^/@[:space:]]+:[^/@[:space:]]+@'
        )

        foreach ($pattern in $secretPatterns) {
            $matches = @(& git grep -I -l -E -i -- $pattern @commits -- 2>$null)
            foreach ($match in ($matches | Sort-Object -Unique | Select-Object -First 20)) {
                Add-Failure "Secret-like content found in Git history: $match"
            }
            if ($matches.Count -gt 20) {
                Add-Failure "Secret-like content has additional matches not listed: $($matches.Count - 20)"
            }
        }

        $emailPaths = @(
            & git grep -I -l -E -i -- '[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}' @commits -- 2>$null |
                ForEach-Object { ($_ -split ':', 2)[1] } |
                Where-Object { $_ } |
                Sort-Object -Unique
        )
        foreach ($path in ($emailPaths | Select-Object -First 20)) {
            Add-Warning "Email-like text exists in Git history; confirm it is intentional before publication: $path"
        }

        $localPathFiles = @(
            & git grep -I -l -E -- '([A-Za-z]:\\Users\\|/Users/[^/]+/|/home/[^/]+/)' @commits -- 2>$null |
                ForEach-Object { ($_ -split ':', 2)[1] } |
                Where-Object { $_ -and $_ -ne 'scripts/security-audit.ps1' } |
                Sort-Object -Unique
        )
        foreach ($path in ($localPathFiles | Select-Object -First 20)) {
            Add-Warning "Local user path exists in Git history; review before publication: $path"
        }
    }
    finally {
        Pop-Location
    }
}

function Find-ApkAnalyzer {
    $command = Get-Command apkanalyzer -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    $roots = @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME) |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) }

    if ($IsWindows -and $env:LOCALAPPDATA) {
        $roots += (Join-Path $env:LOCALAPPDATA "Android/Sdk")
    }

    foreach ($root in ($roots | Select-Object -Unique)) {
        $cmdlineRoot = Join-Path $root "cmdline-tools"
        if (-not (Test-Path -LiteralPath $cmdlineRoot)) {
            continue
        }
        $candidate = Get-ChildItem -LiteralPath $cmdlineRoot -Recurse -File -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -in @("apkanalyzer", "apkanalyzer.bat") } |
            Select-Object -First 1
        if ($candidate) {
            return $candidate.FullName
        }
    }
    return $null
}

function Test-Apk {
    param([string]$Path)

    $resolvedApk = (Resolve-Path -LiteralPath $Path).Path
    $apkAnalyzer = Find-ApkAnalyzer
    if (-not $apkAnalyzer) {
        Add-Failure "apkanalyzer was not found in PATH or Android SDK."
        return
    }

    $manifestLines = @(& $apkAnalyzer manifest print $resolvedApk)
    if ($LASTEXITCODE -ne 0) {
        Add-Failure "apkanalyzer could not read APK manifest."
        return
    }

    [xml]$apkManifest = $manifestLines -join [Environment]::NewLine
    $application = $apkManifest.manifest.application
    $permissions = @($apkManifest.manifest."uses-permission" |
        ForEach-Object { Get-AndroidAttribute $_ "name" }) |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
        Sort-Object -Unique

    Write-Host "APK permissions:"
    $permissions | ForEach-Object { Write-Host "  $_" }

    if ($permissions -contains "android.permission.INTERNET") {
        Add-Failure "APK requests android.permission.INTERNET."
    }

    foreach ($permission in @(
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.MANAGE_EXTERNAL_STORAGE",
        "android.permission.CAMERA",
        "android.permission.RECORD_AUDIO",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.READ_CONTACTS",
        "android.permission.WRITE_CONTACTS",
        "android.permission.READ_CALL_LOG",
        "android.permission.READ_SMS",
        "android.permission.SEND_SMS"
    )) {
        if ($permissions -contains $permission) {
            Add-Failure "Unexpected sensitive permission in APK: $permission"
        }
    }

    if ((Get-AndroidAttribute $application "allowBackup") -ne "false") {
        Add-Failure "APK manifest does not have android:allowBackup=false."
    }

    $componentNodes = $apkManifest.SelectNodes(
        "/manifest/application/activity | /manifest/application/activity-alias | " +
        "/manifest/application/service | /manifest/application/receiver | /manifest/application/provider"
    )
    Write-Host "APK exported components:"
    foreach ($node in $componentNodes) {
        if ((Get-AndroidAttribute $node "exported") -ne "true") {
            continue
        }
        $name = Get-AndroidAttribute $node "name"
        Write-Host "  $name"
        $permission = Get-AndroidAttribute $node "permission"
        $isExpectedAppComponent =
            $name.EndsWith(".ui.MainActivity", [System.StringComparison]::Ordinal) -or
            $name.EndsWith(".ui.tiles.TasksTileService", [System.StringComparison]::Ordinal) -or
            $name.EndsWith(".ui.tiles.NewTaskTileService", [System.StringComparison]::Ordinal)
        $isProtectedProfileInstaller =
            $name -eq "androidx.profileinstaller.ProfileInstallReceiver" -and
            $permission -eq "android.permission.DUMP"

        if (-not $isExpectedAppComponent -and -not $isProtectedProfileInstaller) {
            Add-Failure "Unexpected exported component in APK: $name"
        }
        if ($name.EndsWith("TileService", [System.StringComparison]::Ordinal) -and
            $permission -ne "android.permission.BIND_QUICK_SETTINGS_TILE") {
            Add-Failure "Exported Quick Settings tile lacks BIND_QUICK_SETTINGS_TILE in APK: $name"
        }
    }
}

$repoRoot = (git rev-parse --show-toplevel).Trim()
Test-SourceManifest -RepoRoot $repoRoot
Test-BackupRuleFile -Path (Join-Path $repoRoot "app/src/main/res/xml/backup_rules.xml") -Sections @("cloud-backup", "device-transfer")
Test-BackupRuleFile -Path (Join-Path $repoRoot "app/src/main/res/xml/backup_rules_legacy.xml") -Sections @("legacy")

if (-not $SkipHistory) {
    Test-GitHistory -RepoRoot $repoRoot
}

if (-not [string]::IsNullOrWhiteSpace($ApkPath)) {
    Test-Apk -Path $ApkPath
}

$warnings | ForEach-Object { Write-Host "WARNING: $_" }

if ($failures.Count -gt 0) {
    $failures | ForEach-Object { Write-Host "ERROR: $_" }
    Write-Host "Security audit failed with $($failures.Count) blocking finding(s)."
    exit 1
}

Write-Host "Security audit passed."
