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

### CI implementation follow-up (2026-10-25)
- First 0.2.8 run `40144888882` at SHA `11ce1418138088d1ce4fc75e9f7f8d1f0b5027c2` **failed Kotlin compilation**: malformed import `MediaStoreWriteBatchs`, duplicate `StorageFolder` import, missing `StorageFolders`. **No APK generated**. This is a confirmed CI build blocker, not a user-visible functional result.
- Published correction commit `6e3e730d17c2322f14981d421853fbffef868250` restores the correct imports. New CI runs: push `40145295085` and PR `40145299051`, pending/in progress when last checked.
- Keep FG-04 **open** until exact-corrected-SHA CI build/device tests pass, artifact signature matches existing 0.2.7 shared test key, and actual 0.2.8 APK is released; actual S23 acceptance remains separate.

### 0.2.8 validation / gesture regression follow-up — 2026-10-25
- Commit `6e3e730d17c2322f14981d421853fbffef868250`: `build` passed (unit tests, APK, signing checks). PR Android 16 instrumented run `40145299051` produced **36 tests, 35 pass, 1 failure**. Both new tests (actual move of 2501 simulated files; animated 2-frame GIF decode) **passed**. Failed old test `TiffImageTest.tiffThumbnailAndViewerSupportTheSameZoomCycle`, at second double-tap assertion line 63.
- Direct inspection in `ViewerScreen.kt` found `state.isAnimationRunning` in double-tap guard, which silently drops a second gesture while the 260ms zoom transition runs. The 0.2.8 branch now remembers prior tap target and continues cycle without dropping the tap. Test now advances Compose animation clock and waits for each expected zoom stop.
- Correction pushed in `ddc2cc890b05bbf7e63eb3855f998b16cd441a4a`; new PR run `40145975230` was **in progress** at checkpoint. Never call 0.2.8 verified/publishable before all checks pass.

### FGallery 0.2.8 test APK delivered — 2026-10-25

**Status:** implemented in GitHub + **CI passed**, **not yet tested/accepted by user**. Not merged into the active app branch or `main`. No public GitHub Release v0.2.8 has been created yet; the tested APK was attached directly in the ChatGPT conversation.

- VersionName `0.2.8`, versionCode `10`, package `com.mistermikhail.fgallery`, ABI `arm64-v8a`.
- PR #4 (draft) https://github.com/Mister-Mikhail/FGallery/pull/4 and exact head SHA `ddc2cc890b05bbf7e63eb3855f998b16cd441a4a`.
- GitHub CI https://github.com/Mister-Mikhail/FGallery/actions/runs/40145975230 , completed **success**. Gradle JVM test/build and both arm64/universal signing checks passed. **36/36 Android 16 emulator instrumented tests passed** (includes actual simulated-file move 2501 items and animated GIF decode).
- ARM64 workflow artifact `FGallery-debug` id `12580472470`, extracted `app-arm64-v8a-debug.apk` and delivered as `FGallery-0.2.8-arm64-v8a.apk` in ChatGPT; size `81,501,072` bytes; APK SHA-256 `57884cee1306ba7828e80de43cf20c7277fec0536471e514e2fd5ea14064fae6`.
- APK signing certificate SHA-256 verified by CI on arm64/universal: `ce2293752c0a4a8c844caff57fa3bc28ee9206e927ad311dbcf3a5e71c803eb2`, matching 0.2.7 development key. Manual installation should update 0.2.7 in place, but the phone install is **not tested**.
- Compared to previous failed CI `40145299051`, the TIFF double-tap during animation is now handled without discarding the second tap; test synchronization explicitly waits for its zoom targets.
- The release checker will **not** offer 0.2.8 until a GitHub Release is eventually published; the chat APK is a direct test distribution.

**Next step:** Samsung Galaxy S23 / Android 16 tests for move grant/deny/cancel and selection, 1 and many (including large batches), physical USB/SD mounts and bins, GIF/video preview during scrolling, photo and video crop copy/replace and source preservation, TIFF/IIQ/P65+ 60MP/350MB decoding and zoom, SVG/PDF/360, installation over 0.2.7. Record each direct result here and in `docs/OPEN_BUGS.md`, then coordinate publication/PR integration if approved.

## QA — Samsung Galaxy S23 / Android 16, 0.2.8 (direct user report 2026-10-25)
**High priority / user verified**: RAW DNG/NEF/CR2 cropping **works**, but post-crop save loses EXIF, generates random `_crop_xxxxxxxx` copy names, and delayed gallery placement/refresh; external USB/SD storage is **NOT TESTED**. New issues below are OPEN until next user validation; emulators cannot close them.

| ID | Priority | Required behavior / acceptance | Current status |
|---|---|---|---|
| FG-10 | P0 | Long filename rename dialog starts scrolled to the end with cursor **before extension**; on extension change warn "Файл может некорректно отображаться" and require separate confirmation; no content overwrite or unintentional change. | USER REQUEST, OPEN |
| FG-11 | P0 | Batch rename all selected items with common input stem + `_1`, `_2`, etc. **Each retains its own original extension**; stable gallery order, protect duplicates, no partial accidental overwrite; include files on SAF/volumes. | USER REQUEST, OPEN |
| FG-12 | P1 | Long-press an item to anchor selection, then long-press distant item to select inclusive range of media between endpoints in current displayed sort order, including across scrolled pages; taps still toggle. | USER REQUEST, OPEN |
| FG-13 | P0 | RAW crop save must preserve EXIF **including camera, exposure, date, GPS, orientation semantics and custom metadata when representable**; choose JPEG copy for RAW, originals immutable; naming `_1`, `_2` deterministically; new file visibly adjacent to original without manual gallery refresh. | USER REPORT, OPEN |
| FG-14 | P1 | After confirm replace crop, viewer should immediately show cropped preview while disk replacement completes; on failure show original and error without data loss. | USER REQUEST, OPEN |
| FG-15 | P1 | Still/document double tap: fit → 2x fit → **85% toward maximum** → fit, pinch retains ability to reach 100% maximum. | USER REQUEST, OPEN |
| FG-16 | P0 | Video UI auto-hide **3.0 s** on playback, **never** hide while paused; only filename under scrubber to right of time, no upper EXIF; landscape scrubber lower, portrait time labels above bottom edge. | USER REPORT, OPEN |
| FG-17 | P0 | Cleanup double-tap PDF sends to physical bin without zoom; orange broom icon beside search exits cleanup; remove visible delay after double tap where safe. | USER REPORT, OPEN |
| FG-18 | P1 | External deletion/changes in another app reflected quickly in open folder without manual refresh, preserving scroll position. | USER REPORT, OPEN |
| FG-19 | P0 | Media opened **inside recycle bin** has visible Restore action without returning to grid; restore one item, do not alter others. | USER REQUEST, OPEN |

Rules: do not turn 0.2.8 CI into user acceptance; treat this S23 report as newest truth. New version planned **0.2.9/code11**, reuse 0.2.7+ debug key; keep code separate in an unmerged PR against existing 0.2.8 source. During batch rename collisions or MediaStore prompts, preserve originals and re-selection; no overwrites. If EXIF cannot be transferred safely, do not silently claim it was preserved.

### 0.2.9 in GitHub / awaiting CI (2026-10-25)
- Code branch `feature/fgallery-0.2.9-s23-qa`, draft PR #5 https://github.com/Mister-Mikhail/FGallery/pull/5 , based on 0.2.8 head `ddc2cc890b05bbf7e63eb3855f998b16cd441a4a`, **not merged**.
- Initial head `58f5eb1aa053603a4f4fb74689c328a66c1d111f`, version `0.2.9`/code `11`, test certificate unchanged. CI runs push `40152445434`, PR `40152461435` — active at first verification. Full compilation and Android acceptance results not yet available.
- FG-10/11: shared RenameDialog in gallery/viewer; pre-extension cursor, warning on extension modification, multi-rename `base_1.ext` etc with per-file extension, preflight duplicates and on-disk collision prevention, partial failure selection retained.
- FG-12: selection by repeated long press adds the inclusive range in current sort order; JVM range regression.
- FG-13/14: rename copies `_1`, `_2`; copy EXIF camera/time/GPS/exposure to JPEG from original RAW where readable and verify critical fields before committing; fail explicitly instead of silently stripping, keep originals; put optimistic new item next to source, preserve date and original folder, reveal JPEG from RAW filter; cropped preview shown over viewer while replacement runs, staged backups retained.
- FG-15/16: double-tap 85% of configured range; video controller auto-hides after 3s while playing, stays visible during pause, video inline metadata filename only, progress/time positions tweaked per orientation.
- FG-17/18/19: cleanup double tap for PDF/SVG now bins rather than zoom, broom shortcut, immediate deletion fade; native FileObserver for quick opened-folder changes; in-bin viewer Restore button for image and menu for video.
- New tests: `RenameSelectionTest`, `CropCopyNamesTest`, `MediaRenameRegressionTest`, `CropExifPreservationTest` (EXIF fixture synthetic JPEG carrying RAW extension; physical source samples need acceptance).
- Caution: full manufacturer-specific MakerNotes or proprietary RAW metadata may not map to writable JPEG EXIF; explicit metadata retention check is implemented for common transferable tags. Do not claim complete MakerNotes preservation.
- **Next:** inspect final exact-SHA build & emulator logs, signing and artifact, deliver arm64 APK, update status to CI passed, then wait for user S23 test and external-disk test. No merge without approval.
