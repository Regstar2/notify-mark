# Security-аудит перед публичной beta

Документ относится к Issue #12 и фиксирует проверяемую политику публикации NotifyMark. Он не заменяет проверку финального подписанного APK: перед релизом тот же аудит должен быть запущен на фактическом `app-release.apk`.

## Блокирующие находки

BLOCKER:

- секрет, приватный ключ, signing-файл или credential в текущем дереве или достижимой Git history;
- неожиданное разрешение `INTERNET` или доступ к чувствительным Android API;
- неожиданно экспортированный Android-компонент;
- автоматический backup/restore локальных Markdown-файлов, настроек или сохранённых SAF URI;
- реальный пользовательский vault, дамп или иной приватный файл в истории.

Email-подобные строки и локальные пользовательские пути аудит выводит как предупреждения: перед переводом репозитория в public их нужно вручную подтвердить как намеренные.

## Текущее состояние

### Signing

Release signing получает путь к keystore, alias и пароли только из `NOTIFYMARK_RELEASE_*` или локального `local.properties`. `.gitignore` исключает `local.properties`, `keystore.properties`, `*.jks`, `*.keystore`, `*.p12` и `*.pfx`.

Security-аудит дополнительно проверяет имена файлов во всей достижимой Git history и падает при обнаружении signing material или типичных credential-файлов.

### Samples

`samples/tasks.md` и `samples/obsidian-tasks-syntax-examples.md` содержат синтетические тестовые задачи и примеры синтаксиса, а не копию реального vault.

### Permissions и components

Ожидаемые permissions: `POST_NOTIFICATIONS`, `VIBRATE`, `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`. `INTERNET`, broad storage, camera, microphone, location, contacts, SMS и call-log permissions блокируются аудитом.

Из компонентов приложения намеренно экспортируются только launcher `.ui.MainActivity` и две Quick Settings service. Обе tile service защищены `android.permission.BIND_QUICK_SETTINGS_TILE`. В merged APK AndroidX также добавляет `androidx.profileinstaller.ProfileInstallReceiver`; аудит допускает его только при защите системным permission `android.permission.DUMP`.

### Сеть

В проекте нет собственного HTTP/socket-клиента и нет `INTERNET` permission. GitHub-ссылки открываются через `ACTION_VIEW` только после действия пользователя; соединение выполняет выбранное внешнее приложение.

Персональный support email не хранится в текущей конфигурации. Для публичной beta используется GitHub Issues текущего репозитория.

### Backup и restore

`android:allowBackup="false"` дополнен явными правилами исключения всех app backup domains:

- Android 11 и ниже: `backup_rules_legacy.xml`;
- Android 12+: `backup_rules.xml` отдельно для cloud backup и device-to-device transfer.

Исключены files, databases, shared preferences, app-specific external storage и device-protected equivalents. SAF-selected Markdown documents остаются у document provider и не копируются backup-механизмом NotifyMark.

## Автоматическая проверка

CI делает checkout с полной историей (`fetch-depth: 0`) и после сборки debug APK запускает:

```powershell
./scripts/security-audit.ps1 -ApkPath "./app/build/outputs/apk/debug/app-debug.apk"
```

Скрипт проверяет source manifest, backup rules, sensitive paths во всей Git history, типовые secret patterns и merged manifest APK через `apkanalyzer`. Значение найденного секрета в лог не печатается.

## Проверка финального release APK

После локальной подписанной сборки:

```powershell
.\gradlew.bat assembleRelease
.\scripts\security-audit.ps1 -ApkPath ".\app\build\outputs\apk\release\app-release.apk"
```

Затем отдельно проверяется подпись по `docs/release-signing.md`.

Релиз нельзя публиковать при ненулевом коде security-аудита или пока предупреждение о персональных данных/локальном пути не классифицировано вручную.
