<div align="center">

<img src="docs/assets/icon-transparent.png" width="120" alt="NotifyMark">

# NotifyMark

Android-приложение для локальных напоминаний по задачам в Markdown: читает выбранные заметки, планирует уведомления и записывает изменения обратно в исходный Markdown.

[![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-0A7EA4?style=for-the-badge&logo=android&logoColor=white)](#требования)

**Русский** · [English](README_EN.md)

[Быстрый старт](#быстрый-старт) ·
[Документация](#документация) ·
[Релизы](../../releases) ·
[Обратная связь](#обратная-связь)

</div>

---

## О проекте

NotifyMark хранит напоминания рядом с обычными Markdown-задачами и не требует отдельного сервера. Источником может быть встроенный файл приложения, отдельный Markdown-документ или папка, выбранная через Android Storage Access Framework (SAF).

Markdown остаётся источником истины: приложение разбирает строки задач, показывает их в списке и календаре, планирует локальные напоминания и обновляет исходную строку после выполнения, пропуска или редактирования.

## Статус проекта

| Область | Статус |
|---|---|
| Основной Android-клиент | **Beta** |
| Первая публичная версия | Подготовлена как `v0.10.1-beta.1`, GitHub Release ещё не опубликован |
| Нативный формат NotifyMark | Реализован |
| Совместимость с Obsidian Tasks | Частичная, ограничения задокументированы |
| Release pipeline | Подписанный APK, security audit, проверка подписи и SHA-256 автоматизированы |
| Язык интерфейса | Русский; английская локализация UI не входит в эту beta |

Beta не означает подтверждённую стабильность. Перед публикацией final tag workflow повторно запускает CI и release-проверки; ручная проверка финального APK на устройстве остаётся отдельным release gate.

## Возможности

- встроенное Markdown-хранилище;
- отдельные файлы и папки через SAF;
- даты и время, повторения, grace period и snooze;
- теги, группы, приоритеты и подзадачи;
- список задач и календарь;
- действия из уведомления: выполнить, отложить, пропустить и открыть задачу;
- восстановление расписания после перезагрузки, обновления приложения и изменения системного времени;
- Quick Settings tiles для открытия задач и создания новой задачи;
- запись статуса обратно в исходный Markdown;
- частичная совместимость с метаданными Obsidian Tasks.

## Быстрый старт

Пока первый публичный beta-релиз не опубликован, воспроизводимый способ запуска — debug-сборка из исходников:

```powershell
.\gradlew.bat assembleDebug
adb install -r ".\app\build\outputs\apk\debug\app-debug.apk"
```

После запуска выберите встроенное хранилище либо подключите Markdown-файл/папку и выдайте необходимые Android-разрешения.

Для публичной beta после публикации используйте подписанный APK из [GitHub Releases](../../releases), а не debug-сборку.

## Требования

- Android 8.0 или новее (`minSdk 26`);
- разрешение на уведомления на версиях Android, где оно запрашивается системой;
- special access для точных будильников, если нужны напоминания строго в заданное время;
- доступ SAF для внешних файлов и папок.

Для сборки из исходников нужны JDK 17 и Android SDK 35.

## Установка

### Публичная beta

После публикации `v0.10.1-beta.1`:

1. Откройте [GitHub Releases](../../releases/tag/v0.10.1-beta.1).
2. Скачайте `NotifyMark-v0.10.1-beta.1.apk` и файл `NotifyMark-v0.10.1-beta.1.apk.sha256`.
3. Проверьте SHA-256 APK по опубликованному checksum-файлу.
4. Разрешите установку APK из выбранного источника, если Android запросит это.
5. Установите APK и откройте NotifyMark.

PowerShell-проверка checksum:

```powershell
$Expected = ((Get-Content ".\NotifyMark-v0.10.1-beta.1.apk.sha256" -Raw).Trim() -split "\s+")[0]
$Actual = (Get-FileHash ".\NotifyMark-v0.10.1-beta.1.apk" -Algorithm SHA256).Hash
$Actual.ToLowerInvariant() -eq $Expected.ToLowerInvariant()
```

Если на устройстве установлена debug/dev-сборка с другой подписью, Android может отказать в обновлении поверх неё. Перед удалением такой сборки сохраните важные Markdown-файлы и настройки, которые нельзя восстановить.

## Использование

### Подключение источника

1. Откройте управление источниками.
2. Выберите встроенное хранилище, отдельный файл или папку.
3. Для внешнего источника подтвердите доступ в системном SAF-диалоге.
4. Создайте задачу в приложении или добавьте поддерживаемую строку в Markdown.

### Разрешения Android

- **SAF** — выдаёт NotifyMark доступ только к выбранному пользователем файлу или дереву документов. Для внешних источников приложение сохраняет persistable read/write permission; доступ может перестать работать, если document provider отозвал grant, файл был перемещён или источник стал недоступен.
- **Notifications** — нужны для показа напоминаний. На современных версиях Android это отдельное runtime-разрешение.
- **Exact alarms** — `SCHEDULE_EXACT_ALARM` используется для точного времени срабатывания. На версиях Android с special access его нужно разрешить отдельно; без него точность напоминаний может быть ограничена.
- **Boot completed** — `RECEIVE_BOOT_COMPLETED` используется для восстановления расписания после перезагрузки.
- **Vibrate** — используется для вибрации уведомлений.

### Формат задач

Нативный формат NotifyMark:

```markdown
- [ ] Подготовить отчёт @due(2026-10-05 18:00) @priority(high) #учёба
- [ ] Проверить резервную копию @repeat(1w) @snooze(30m)
```

Реализованы `@due(...)`, `@repeat(...)`, `@repeatUntilDone(...)`, `@grace(...)`, `@snooze(...)`, `@group(...)`, `@priority(...)`, `@tag(...)`, `#tags` и legacy-форма `@YYYY-MM-DD`.

Поддержка Obsidian Tasks ограничена подмножеством emoji-метаданных. Точные правила: [совместимость v0.10.0](docs/versions/v0.10.0-obsidian-tasks-plugin-compatibility.md).

## Приватность

- задачи и настройки обрабатываются локально;
- приложение не запрашивает `INTERNET` и не выполняет собственные HTTP/socket-запросы;
- внешние Markdown-файлы доступны только после выбора через SAF;
- внутренние Markdown-файлы, настройки и сохранённые SAF-ссылки исключены из Android cloud backup/device transfer;
- выполнение, пропуск и редактирование задач изменяет выбранные Markdown-файлы.

**Перед подключением важных заметок сделайте резервную копию или используйте синхронизацию с историей версий.** Beta может содержать ошибки записи, которые ещё не выявлены ручным QA.

## Диагностика

При отсутствии уведомлений проверьте:

1. разрешение на уведомления;
2. special access для exact alarms;
3. ограничения фоновой работы производителя устройства;
4. сохранённый SAF-доступ к внешнему источнику.

Unit-тесты:

```powershell
.\gradlew.bat testDebugUnitTest
```

## Сборка

```powershell
.\gradlew.bat assembleDebug
```

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Подписанная release-сборка требует настроенный release-keystore; подробности находятся в [docs/release-signing.md](docs/release-signing.md).

## Тестирование

Проектный CI запускается через:

```powershell
.\scripts\ci.ps1
```

Скрипт собирает debug APK, запускает unit-тесты и security audit. Release workflow дополнительно собирает подписанный APK, проверяет его через `apksigner` и сверяет SHA-256.

## Документация

| Задача | Документ |
|---|---|
| Индекс документации | [docs/README.md](docs/README.md) |
| Формат Markdown | [docs/markdown-format.md](docs/markdown-format.md) |
| Источники и SAF | [docs/source-system.md](docs/source-system.md) |
| Система уведомлений | [docs/notification-system.md](docs/notification-system.md) |
| Совместимость с Obsidian Tasks | [docs/versions/v0.10.0-obsidian-tasks-plugin-compatibility.md](docs/versions/v0.10.0-obsidian-tasks-plugin-compatibility.md) |
| Подпись и публикация APK | [docs/release-signing.md](docs/release-signing.md) |
| Release notes `v0.10.1-beta.1` | [docs/releases/v0.10.1-beta.1.md](docs/releases/v0.10.1-beta.1.md) |
| История изменений | [CHANGELOG.md](CHANGELOG.md) |
| Roadmap | [docs/roadmap.md](docs/roadmap.md) |

## Обратная связь

- [Сообщить об ошибке](../../issues/new?template=bug_report.yml)
- [Предложить улучшение](../../issues/new?template=feature_request.yml)
- [Открытые Issues](../../issues)

В приложении также есть переход к GitHub Issues. До внешнего распространения репозиторий должен быть доступен целевой аудитории; private Issues не считаются рабочим публичным каналом.

Не прикладывайте приватные заметки целиком. Перед публикацией логов или скриншотов удалите личные данные и содержимое Markdown, не относящееся к проблеме.

## Ограничения

- это beta без обещания стабильности, пока финальный tagged APK не прошёл полный ручной QA;
- Obsidian Tasks поддерживается частично: нет query blocks/query language, полной natural-language recurrence grammar и natural-language dates;
- `every ... when done` и ряд других recurrence-форм сохраняются как metadata, но не превращаются в нативное правило повторения;
- `⏳` и `🛫` сами по себе не являются Android notification trigger;
- работа внешних файлов зависит от SAF и document provider;
- точность напоминаний зависит от Android permissions и фоновых ограничений;
- `applicationId` пока остаётся `com.regstar.obsidiannotification` для сохранения Android identity;
- интерфейс приложения в `v0.10.1-beta.1` доступен на русском языке; английские README и release notes не означают наличие английской локализации UI.

## Лицензия

NotifyMark распространяется по лицензии [MIT](LICENSE). Лицензии сторонних компонентов перечислены в [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
