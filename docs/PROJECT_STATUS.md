# FGallery — PROJECT STATUS / HANDOFF

Актуализировано: **2026-10-25**. Этот документ — компактная сводка для продолжения в новом чате. Подробные критерии и история — `docs/OPEN_BUGS.md`; поведение — `docs/PRODUCT_SPEC.md` и `docs/UX_RULES.md`. Внешняя истина о публикации — GitHub, о пользовательском результате — последний прямой отчёт.

## Точка возобновления и проверенные ссылки
- Репозиторий: https://github.com/Mister-Mikhail/FGallery .
- `main`: начальный каркас (по проверке 2026-10-25).
- Рабочий код: `feature/quickpic-masonry-mvp`, PR #2 https://github.com/Mister-Mikhail/FGallery/pull/2 — **open, not merged**. Проверенный head до документационной ветки: `51a812cac69220a17bd69a2516fff4e275326ae3`. Последний его коммит документирует доставку 0.2.7.
- Последняя опубликованная подтверждённая debug-версия: **0.2.7 / versionCode 9**. Тестовый исходник `72e2f0ae2f32f05f71acca8061f391ff15fcea8a`, push CI `37254313897`; **16 JVM** и **34 Android 16 emulator** checks passed, опубликована через GitHub Releases. APK: https://github.com/Mister-Mikhail/FGallery/releases/download/v0.2.7/FGallery-0.2.7-arm64-v8a.apk . Certificate SHA256 `ce2293752c0a4a8c844caff57fa3bc28ee9206e927ad311dbcf3a5e71c803eb2`.
- Важная оговорка: этот CI подтверждает синтетические/emulator сценарии, а **не** завершение тестирования на Samsung Galaxy S23 / Android 16.
- Планировалась **0.2.8** (запрос пользователя 2026-10-08): отдельные исправления были описаны как сделанные локально, но не опубликованы в GitHub. Их нельзя извлечь из опубликованного PR или считать доставленными; выяснить, есть ли доступное рабочее дерево, либо повторно применить проверенным способом.

## Активные задачи с приоритетом (последние отчёты важнее старых CI)

| ID | Приоритет | Задача / точный результат | Статус |
|---|---|---|---|
| FG-01 | P0 | **Перемещение** 1/многих файлов по MediaStore/SAF/USB/SD: факт переноса; Grant/Deny/Back без падений, отмена, точное «Перемещение», без лишнего подтверждения по умолчанию; выбор папок, создание пустой папки. Массовый сценарий 2501 файла. | Последние пользовательские сообщения подтверждали поломку/краш; локальные 0.2.8-тесты не опубликованы. **OPEN; device test required** |
| FG-02 | P0 | **Кадрирование фото**: редактор открывается, ручки работают, итоговые пиксели действительно сохраняются; копия/замена без второго ненужного диалога, откат при ошибке. RAW → только JPEG-копия. | Ранние падения и несохранение; эмуляторные регрессии есть, **последний реальный результат не подтвердил закрытие** |
| FG-03 | P0 | **Превью видео / живые GIF**: не чёрные плитки, первый нормальный кадр/постер, активный превью-плеер не перезапускается от прокрутки, один muted live-preview ~5 с; анимированные GIF. | Пользователь сообщал о проблеме; GIF-поправки для 0.2.8 описаны локально; **нужен новый APK и реальный тест** |
| FG-04 | P0 | **Сборка 0.2.8 и проверка обновления поверх 0.2.7**: новый versionCode/versionName, тот же debug cert, публикация строго после сборки/инструментальных тестов; диалог скачивания/установки, отмена и пакетная подпись. | Не опубликовано. Последняя доставленная — 0.2.7 |
| FG-05 | P1 | **Съёмные носители**: названия и значки USB/SD, одноимённые папки по пути/накопителю, пустые папки, корректная автоактуализация на mount/unmount, отдельные физические корзины и восстановление. | Часть кода/эмуляторных тестов 0.2.7 есть; физический носитель на S23 не проверен. 0.2.8 локально правилась |
| FG-06 | P1 | **RAW/TIFF и большой зум**: Phase One P65+ original `.IIQ` (поддержка подтверждена как отображение), TIFF из RAW около 350MB, полные ~60.5MP; Canon EOS 5DS R ~50.6MP. Убрать минутные загрузки и пиксельность, определить потребление RAM на реальных файлах. | 0.2.7 synthetic 8984×6732 TIFF/RAW тесты успешны; реальные исходники, память/скорость на S23 — **не проверены** |
| FG-07 | P1 | **360° + SVG/PDF**: автоопределение 360 по метаданным, гироскоп/touch/pinch; SVG/PDF видны, корректные страницы/масштаб/перетаскивание, точные ошибки, работа с проблемным PDF. | Часть воспроизводилась и проходила эмулятор, не закрывать без устройства и реальных файлов |
| FG-08 | P1 | **UX 0.2.8**: плотные беззазорные плитки, компактные шапка/настройки, сортировка по имени/размеру/типу и направлению (в локальных заметках упоминались пять видов сортировки), убрать *пункты меню* PDF/SVG, доступ и подключение накопителя, но сохранить функциональность форматов/накопителей. | Локальные правки описаны, в GitHub не поступили |
| FG-09 | P1 | **Видео/Viewer регрессии**: кнопки и файл-меню скрываются синхронно через 1,5 с; аудио соседних видео не течёт; Cleanup после удаления показывает следующий/предыдущий и сохраняет каталог; корзина открывает медиа без восстановления. | В 0.2.7 тесты bin/viewer прошли; проверить на S23 |

## Что ранее подтверждалось положительно
- По прямому отчёту 2026-10-04 больше не заедает вход в папку; восстановление позиции скролла работало, поворот и Cleanup выглядели работоспособными. Не возвращай их в активные P0 без нового воспроизведения.
- Видео-кроп/сохранение пользователь подтвердил на сборке 0.2.4. Продолжать проверять сохранение копии/замену и регрессии, но не объявлять его безусловно сломанным.
- SVG/PDF открывались в отдельных промежуточных сборках; более поздние проблемы касались масштабирования, движения страниц и отдельных документов. Не утверждать, что поддержка отсутствует целиком.
- Phase One P65+ `.IIQ` отображался. **Устаревшая запись «Phase One отложен» отменена для IIQ/поддерживаемых RAW**. DWG остаётся отложен.

## Протокол каждого следующего сеанса
1. Прочитать этот файл, `AGENTS.md`, `docs/OPEN_BUGS.md`, `docs/PRODUCT_SPEC.md`, `docs/UX_RULES.md` и последние комментарии/PR/CI. Отдельно проверять обновления этого файла в документационном PR до его слияния.
2. Уточнять/дополнять статусы после **нового прямого пользовательского теста**, записывая сборку/SHA/телефон и результат. Ничего не закрывать на основании слов «код добавлен».
3. Восстановить/сверить нереализованный GitHub-пакет 0.2.8, начать с FG-01/02/03 и безопасного file-operation flow. Не публиковать очередной APK, не проверив собственную сборку и CI.
4. После кода записать изменённые файлы, тесты и устройство, commit SHA, ветку и PR, build run, ссылку на APK и следующий шаг. Обновить этот реестр.
5. Помнить: push рабочей ветки публикует release с версией из Gradle; повторно использовать `0.2.7` на новом SHA нельзя. Документацию менять в отдельной ветке, код выпускать новой версией.

## Первый результат проверки исходников — 2026-10-25
В `MainActivity.kt` (`feature/quickpic-masonry-mvp`) `executeWrite(PendingWriteOperation.Move)` при частичном сбое формирует `PartialMoveException(remaining, cause)`, однако ветка обработки `SecurityException` вызывает `MediaStore.createWriteRequest(..., operationUris(operation))` по исходному `operation` и сохраняет `pendingWrite = operation`. После Grant `writeLauncher` снова вызывает `performWrite(operation)` по **всем исходным файлам**, хотя часть уже перемещена. Это воспроизводимый по коду дефект управления частичной операцией / вероятный источник проблем массового перемещения, **не доказательство причины всех падений на S23**. Проверить на сценарии N успешных + 1 отказ MediaStore, затем исправить выдачу разрешений и повтор только для оставшихся; не терять выбор, не выполнять повторное перемещение уже перенесённых, сверять итоговые каталоги.

## Следующий конкретный шаг
**Проверить исходники 0.2.7/PR #2 на сценариях перемещения и сохранения кадрирования, воспроизвести падения/ошибки и собрать выпуск 0.2.8 с регрессионными тестами; затем проверить установку поверх 0.2.7 и USB/SD/GIF на реальном устройстве.** Не подменять настоящие исправления ещё одним косметическим APK.


## 0.2.8 implementation / verification — 2026-10-25

- **Live repository visibility: public**, permission `admin/push=true`. No private→public toggle required; public GitHub Actions minute availability still subject to actual workflow execution.
- New code branch `feature/fgallery-0.2.8-fixes`, draft PR #4: https://github.com/Mister-Mikhail/FGallery/pull/4 . Base is active app branch `feature/quickpic-masonry-mvp`; PR is **not merged**. VersionName 0.2.8, versionCode10, shared debug certificate retained.
- First code changes published to PR #4 (status **code in GitHub; device unverified**):
  - FG-01: `MainActivity` retries only failed move items after partial completion; consent requests select at most 2000 MediaStore URIs, and additional batches are requested when needed. Denial retains selection and refreshes results. Direct-file bulk move no longer blocks up to 20 seconds waiting for MediaScanner per file. `MoveDestinationScreen` now selects a folder before committing, and its keys distinguish exact volume/target.
  - FG-05: `GalleryViewModel` groups by physical folder/volume, preventing same-name directories across drives from merging into one album; album navigation uses stable keys.
  - FG-03: animated GIF tile overlay uses Android ImageDecoder/AnimatedImageDrawable with lifecycle cleanup.
  - FG-08: five requested sort categories (newest, oldest, name +/- , size +/- , type +/-), compact action buttons and settings, zero-gap normal album/mosaic/uniform grid, smaller folder title while preserving root FGallery size, USB/SD icon, removal of three redundant main-menu commands without disabling PDF/SVG or removable storage.
- New **JVM tests**: 2501-item MediaStore consent batching and cancellation state. New **Android instrumented test**: move 2501 tiny actual files on a simulated storage root, verify all bytes and collision preservation. These tests are test code, not yet acceptance evidence.
- Active validation: GitHub Actions PR run `40144802626` for head `82d6e1687aa5afe77fc35168616fc3d1f39de08c` was `pending` at first check. Do not mark CI passed until checked. Local build cannot be run in this session (no Android SDK or network in container); use remote GitHub Actions.
- This is **not a delivered APK** yet. Do not distribute the older 0.2.7 release as 0.2.8, do not claim user-device acceptance. Follow up on CI; fix compilation/tests; publish code through active branch only after verified checks to allow GitHub release pipeline to provide exact source SHA and direct APK.
