# Подпись release-сборки

Этот документ описывает локальную сборку подписанного APK для публичных beta/release-версий NotifyMark.

## Политика ключа

Для публичных APK используется один постоянный release-keystore. Сам keystore не хранится в репозитории. Пароли и alias могут храниться локально в `local.properties`, который исключён из Git, либо передаваться через переменные окружения.

Gradle использует значения в таком порядке:

1. переменные окружения `NOTIFYMARK_RELEASE_*`;
2. локальные свойства `notifyMark.release.*` из `local.properties`.

Поддерживаемые значения:

- `NOTIFYMARK_RELEASE_STORE_FILE` / `notifyMark.release.storeFile`;
- `NOTIFYMARK_RELEASE_STORE_PASSWORD` / `notifyMark.release.storePassword`;
- `NOTIFYMARK_RELEASE_KEY_ALIAS` / `notifyMark.release.keyAlias`;
- `NOTIFYMARK_RELEASE_KEY_PASSWORD` / `notifyMark.release.keyPassword`.

Если запрошена release-сборка и хотя бы одно значение отсутствует, сборка завершается ошибкой. Debug-сборка от release-ключа не зависит.

`local.properties` содержит секреты в открытом виде. Его нельзя прикладывать к issue, логам, архивам или копировать в репозиторий.

## Создание постоянного keystore

Keystore создаётся один раз и хранится вне репозитория. Если для уже опубликованной версии NotifyMark существовал другой release-keystore, новый ключ нельзя использовать для обновления такой установки.

Рекомендуемый путь на Windows:

```text
$env:USERPROFILE\.notify-mark\keys\notify-mark-release.jks
```

Для нового проекта можно сгенерировать сильные случайные пароли PowerShell-командами и записать их в локальный `local.properties`. Полный воспроизводимый блок команд приведён в рабочей инструкции Issue #9.

## Сборка подписанного APK

После заполнения `local.properties`:

```powershell
.\gradlew.bat assembleRelease
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

2. Создайте тестовую задачу или измените настройку.
3. Повторно соберите APK с тем же keystore и теми же signing-параметрами.
4. Ещё раз выполните `adb install -r`.
5. Убедитесь, что установка проходит без ошибки несовпадения подписи и данные приложения сохраняются.

Повторная установка APK с тем же `versionCode` подходит для проверки непрерывности подписи через ADB. Для реального обновления через канал распространения следующая версия должна иметь больший `versionCode`.

Если на устройстве уже установлена debug-сборка с тем же `applicationId`, release APK с новым ключом поверх неё не установится. Не удаляйте такую установку автоматически, если в ней есть нужные данные.

## Backup и восстановление

Нужно сохранить вместе:

- файл release-keystore;
- alias ключа;
- пароль keystore;
- пароль ключа;
- запись, что этот ключ относится к Android applicationId `com.regstar.obsidiannotification`.

Рекомендуемый минимум:

- рабочая копия keystore вне Git-репозитория;
- вторая копия в зашифрованном резервном хранилище;
- alias и пароли в менеджере паролей;
- периодическая проверка резервной копии через `keytool -list`.

Потеря release-ключа или его паролей означает, что для прямого APK-канала нельзя будет выпускать обновления поверх уже установленных сборок, подписанных этим ключом.

## Контроль Git

Файлы `local.properties`, `*.jks`, `*.keystore`, `*.p12`, `*.pfx` и `keystore.properties` игнорируются Git. После работы можно проверить историю:

```powershell
git log --all --name-only --pretty=format: -- "local.properties" "*.jks" "*.keystore" "*.p12" "*.pfx" "keystore.properties" |
    Where-Object { $_ } |
    Sort-Object -Unique
```

Для чистой истории команда не должна вывести signing-файлы.


## GitHub Actions release automation

Автоматическая публикация реализована в `.github/workflows/release.yml`. Workflow использует точный существующий tag, повторно запускает проектный CI, собирает подписанный release APK через `scripts/release.ps1`, проверяет APK security-аудитом и `apksigner`, затем создаёт SHA-256 checksum.

Для GitHub Actions нужны repository secrets:

- `NOTIFYMARK_RELEASE_KEYSTORE_BASE64` — release-keystore целиком в Base64;
- `NOTIFYMARK_RELEASE_STORE_PASSWORD`;
- `NOTIFYMARK_RELEASE_KEY_ALIAS`;
- `NOTIFYMARK_RELEASE_KEY_PASSWORD`.

Keystore материализуется только во временном каталоге runner и удаляется шагом `always()` после workflow. Значение keystore и пароли не должны выводиться в logs.

### Dry-run

Ручной `workflow_dispatch` принимает планируемую release-версию в формате tag и параметр `publish`.

При `publish = false` workflow:

1. использует checkout выбранного для ручного запуска ref (для первой проверки — `main`);
2. проверяет, что указанная версия совпадает с `versionName`;
3. запускает `scripts/ci.ps1`;
4. собирает подписанный release APK;
5. проверяет security-аудит и подпись;
6. создаёт `NotifyMark-<tag>.apk` и `.apk.sha256` в `dist/`;
7. загружает эти два файла как GitHub Actions artifact;
8. не создаёт tag и GitHub Release.

Это штатный способ проверить pipeline перед первой публичной публикацией.

### Публикация

Push подходящего tag `v*` автоматически запускает публикацию. Перед сборкой workflow дополнительно проверяет SemVer-подобный формат tag и соответствие `v...` значению `versionName` в `app/build.gradle`.

GitHub Release создаётся только после успешных CI, release security-аудита и проверки подписи. Release notes генерируются GitHub автоматически. Tags с `alpha`, `beta` или `rc` публикуются как prerelease и не помечаются как latest.

Ручной запуск с `publish = true` требует существующий tag, переключается на его точный commit и выполняет тот же publish path, что и tag push. Если GitHub Release для tag уже существует, workflow завершается ошибкой вместо молчаливой перезаписи.
