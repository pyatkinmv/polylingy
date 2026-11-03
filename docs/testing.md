# Как проверяется Polylingy

Стандартный порядок проверки любой фичи или исправления. Большую часть делает `./gradlew check`; руками проверяется только то, что автоматизировать нельзя.

## Уровни тестов

| Уровень | Где | Что проверяет |
|---|---|---|
| Содержимое | `content/src/commonTest` | разметка, `validate()` каждого блока и типа упражнения, строгий разбор, туда-обратно |
| Правила | `core/src/commonTest` | файл курса (`CourseJson`), `CourseService`, `TextMatcher`, чекеры, каждая политика, сценарии `learning-model.md` (`ScenarioTest`), `StudyService` |
| Эталон SM-2 | `core/src/jvmTest/.../Sm2GoldenTest.kt` | 856 сдач из 0.1 через новый `replay()` |
| Хранилище | `client/src/desktopTest` | SQLite, нечитаемые курсы, импорт/экспорт JSON и zip, картинки, пример курса и курсы из `examples/` |
| ViewModel | `app/src/desktopTest/.../courses` | логика списка курсов без UI |
| Витрина | `app/src/desktopTest/.../ui/ShowcaseUiTest.kt` | каждый тип упражнения и страница теории из примера курса: ввод, проверка, узкое окно; скриншоты `ex-*.png`, `theory*.png` |
| **UI** | `app/src/desktopTest/.../ui/*UiTest.kt` | настоящие экраны: список курсов (`CourseListUiTest`), путь от курса через теорию и упражнения до «на сегодня всё» и повторения на следующий день (`StudyUiTest`), добавление, правка и удаление юнитов и топиков с превью (`EditUiTest`) |
| **Скриншоты** | те же UI-тесты, вызов `screenshot("имя")` | PNG в `app/build/screenshots/` для просмотра глазами |

## UI-тесты

Покрытие строк для `content`, `core` и `client` проверяет Kover: меньше 90 % — `check` падает. Отчёт: `./gradlew koverHtmlReport`.

Запускают настоящий `App` без окна, в сцене размером с окно приложения (832×720), на базе в памяти и с часами, которые можно двигать. Файловые диалоги заменены заглушкой `FakeFiles`. Всё нужное — в `UiTestSupport.kt`:

```kotlin
@Test fun theNextDayTheCourseShowsWhatIsDue() = appTest(seed) {
    click("Grammar in Use")
    click("Present simple")
    click("Continue →")
    fill(1, "works")                   // ввод в пропуск 1
    click("Check")
    click("Next →")
    text("That’s all for today").assertIsDisplayed()

    clock.now = LocalDateTime(2026, 1, 16, 9, 0)
    click("Courses")
    text("to do today").assertIsDisplayed()
    screenshot("course-list-due")
}
```

- `appTest(seed = …) { … }` — запуск приложения с подготовленными данными (`seedSample()`, `seedCourse(json)`).
- `files.open("имя.json", json)` — что «выберет» пользователь в диалоге импорта; `files.saved` — что приложение сохранило.
- `click("текст")`, `menu("пункт")` (меню «⋯»), `fill(n, "текст")` (пропуск n), `text("текст")` — поиск и действия по видимому тексту.
- `appTest(storage = { … })` — своя база, например файл с нечитаемым курсом.
- `clock.now = …` — сдвинуть время, например, чтобы тема вернулась на повторение.
- Случайность зафиксирована (`Random(7)`), поэтому прогоны повторяемы.

## Скриншоты

`screenshot("имя")` сохраняет всю сцену вместе с открытыми диалогами и меню в `app/build/screenshots/имя.png`. Снимки не сравниваются с эталоном автоматически — их смотрят глазами (человек или Claude) после изменений вёрстки: обрезанный текст, наложения, цвета, отступы.

## Порядок работы над фичей

1. Правила и логика — тесты в `core`/`client`.
2. Экран — сценарий в `*UiTest.kt`, с `screenshot(…)` для новых или изменённых экранов.
3. `./gradlew check` — всё зелёное.
4. Просмотреть свежие скриншоты в `app/build/screenshots/`.
5. Руками — только то, что ниже.

## Что проверяется руками

- системные диалоги открытия и сохранения файла;
- настоящее окно: размер, минимальный размер, ресайз;
- отрисовка шрифтов Windows в реальном окне;
- импорт настоящих курсов из `examples/` через диалог.

Установщик `.msi` пока не проверяем: собираем и запускаем локально, `scripts/relaunch.ps1` (или `./gradlew :app:run`).
