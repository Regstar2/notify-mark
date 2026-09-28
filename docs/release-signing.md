# Подпись release-сборки

Этот документ описывает локальную сборку подписанного APK для публичных beta/release-версий NotifyMark.

## Политика ключа

Для публичных APK используется один постоянный release-keystore. Сам keystore, его пароли и пароль ключа не хранятся в репозитории, GitHub Actions или файлах, попадающих в артефакты.

Gradle читает signing-параметры только из переменных окружения текущего процесса:

- `NOTIFYMARK_RELEASE_STORE_FILE`;
- `NOTIFYMARK_RELEASE_STORE_PASSWORD`;
- `NOTIFYMARK_RELEASE_KEY_ALIAS`;
- `NOTIFYMARK_RELEASE_KEY_PASSWORD`.

Если запрошена сборка release-артефакта и хотя бы одна переменная отсутствует, сборка завершается ошибкой. Debug-сборка от release-ключа не зависит.

## Создание постоянного keystore

Keystore создаётся один раз и хранится вне репозитория. Пример для Windows:

```powershell
New-Item -ItemType Directory -Force "C:\Base\keys" | Out-Null

keytool -genkeypair -v `
    -keystore "C:\Base\keys\notify-mark-release.jks" `
    -alias "notifymark-release" `
    -keyalg RSA `
    -keysize 4096 `
    -validity 10000 `
    -storetype JKS
```

`keytool` запросит пароли и данные сертификата интерактивно. Не передавайте пароли аргументами командной строки и не записывайте их в `gradle.properties`, `local.properties`, workflow или commit.

Если для NotifyMark уже существует release-keystore, новый ключ создавать нельзя: используйте исходный keystore и alias, иначе Android не примет APK как обновление существующей установки.

## Сборка подписанного APK

Из корня репозитория:

```powershell
$env:NOTIFYMARK_RELEASE_STORE_FILE = "C:\Base\keys\notify-mark-release.jks"
$env:NOTIFYMARK_RELEASE_KEY_ALIAS = "notifymark-release"

$StorePassword = Read-Host "Keystore password" -AsSecureString
$KeyPassword = Read-Host "Key password" -AsSecureString

$env:NOTIFYMARK_RELEASE_STORE_PASSWORD = [System.Net.NetworkCredential]::new("", $StorePassword).Password
$env:NOTIFYMARK_RELEASE_KEY_PASSWORD = [System.Net.NetworkCredential]::new("", $KeyPassword).Password

try {
    .\gradlew.bat assembleRelease
    if ($LASTEXITCODE -ne 0) {
        throw "assembleRelease failed"
    }
}
finally {
    Remove-Item Env:NOTIFYMARK_RELEASE_STORE_FILE -ErrorAction SilentlyContinue
    Remove-Item Env:NOTIFYMARK_RELEASE_STORE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:NOTIFYMARK_RELEASE_KEY_ALIAS -ErrorAction SilentlyContinue
    Remove-Item Env:NOTIFYMARK_RELEASE_KEY_PASSWORD -ErrorAction SilentlyContinue
    Remove-Variable StorePassword, KeyPassword -ErrorAction SilentlyContinue
}
```

Ожидаемый APK:

```text
app/build/outputs/apk/release/app-release.apk
```

Для первого beta-релиза `minifyEnabled` и `shrinkResources` намеренно выключены. Их следует включать только отдельной задачей после проверки release-сборки, правил R8 и пользовательского сценария.

## Проверка подписи

Найдите актуальный `apksigner` в установленном Android SDK и проверьте APK:

```powershell
$SdkRoot = if ($env:ANDROID_SDK_ROOT) {
    $env:ANDROID_SDK_ROOT
}
elseif ($env:ANDROID_HOME) {
    $env:ANDROID_HOME
}
else {
    Join-Path $env:LOCALAPPDATA "Android\Sdk"
}

$BuildTools = Get-ChildItem (Join-Path $SdkRoot "build-tools") -Directory |
    Where-Object { $_.Name -match '^\d+(\.\d+){1,2}$' } |
    Sort-Object { [version]$_.Name } -Descending |
    Select-Object -First 1

if (-not $BuildTools) {
    throw "Android SDK build-tools not found"
}

$ApkSigner = Join-Path $BuildTools.FullName "apksigner.bat"
$Apk = ".\app\build\outputs\apk\release\app-release.apk"

& $ApkSigner verify --verbose --print-certs $Apk
if ($LASTEXITCODE -ne 0) {
    throw "apksigner verify failed"
}
```

Проверка должна завершиться с кодом `0` и показать сертификат подписанта.

## Проверка установки и обновления

1. Установите подписанную release-сборку:

```powershell
adb install -r ".\app\build\outputs\apk\release\app-release.apk"
```

2. Создайте в приложении тестовую задачу или измените настройку, чтобы было что проверить после обновления.
3. Повторно соберите APK с тем же keystore и alias.
4. Ещё раз выполните `adb install -r` для нового APK.
5. Убедитесь, что установка проходит без ошибки несовпадения подписи, а данные приложения сохраняются.

Повторная установка APK с тем же `versionCode` подходит для проверки непрерывности подписи через ADB. Для реального обновления через канал распространения следующая версия должна иметь больший `versionCode`.

## Backup и восстановление

Нужно сохранить вместе:

- файл release-keystore;
- alias ключа;
- пароль keystore;
- пароль ключа;
- краткую запись, что этот ключ относится к Android applicationId `com.regstar.obsidiannotification`.

Рекомендуемый минимум:

- рабочая копия keystore вне Git-репозитория;
- вторая копия в зашифрованном резервном хранилище;
- alias и пароли в менеджере паролей;
- периодическая проверка, что резервная копия читается через `keytool -list`.

Потеря release-ключа означает, что для прямого APK-канала нельзя будет выпускать обновления поверх уже установленных сборок, подписанных этим ключом.

## Контроль Git

Файлы `*.jks`, `*.keystore`, `*.p12`, `*.pfx` и `keystore.properties` игнорируются Git. После работы можно дополнительно проверить историю:

```powershell
git log --all --name-only --pretty=format: -- "*.jks" "*.keystore" "*.p12" "*.pfx" "keystore.properties" |
    Where-Object { $_ } |
    Sort-Object -Unique
```

Для чистой истории команда не должна вывести signing-файлы.
