# Тестування NeuronMap

Автоматизовані тести містяться в `src/test/java/com/example/neuronmap/`. Підпакети тестів відповідають підпакетам production-коду там, де код тематично розділений. Під час зміни компонента спочатку запускай найближчі тести, а перед передаванням змін — повний набір.

## Команди

Запуск усіх тестів із очищенням попередніх результатів:

**Windows PowerShell**

```powershell
.\mvnw.cmd clean test
```

**Linux/macOS**

```bash
./mvnw clean test
```

Якщо Maven установлено окремо, можна виконати `mvn clean test`.

GitHub Actions workflow `.github/workflows/ci.yml` має запускати `clean test` на кожному push до `develop`, у pull request до `develop` і вручну. Матриця перевіряє Windows, Linux (із Xvfb для JavaFX-тестів) та macOS на JDK 25. Коли додаєш або змінюєш цей workflow, перевір перший реальний запуск у вкладці **Actions** репозиторію; локальний запуск не замінює результат CI.

Окремий клас:

```bash
./mvnw -Dtest=NeuronServiceTest test
```

Регресійні тести життєвого циклу подань:

```bash
./mvnw -Dtest=MapPresentationWorkflowLifecycleTest test
```

Кілька класів:

```bash
./mvnw -Dtest=NeuronServiceTest,SimulationSessionTest test
```

У Windows використовуйте `.\mvnw.cmd` замість `./mvnw`. Maven Compiler налаштований на JDK 25. Проєкт використовує JavaFX 27; JavaFX-тести запускають toolkit і виконують перевірки графічних вузлів у JavaFX Application Thread, тому для них має бути доступне середовище з потрібними JavaFX native-залежностями.

JaCoCo налаштований формувати звіт у `target/site/jacoco/` на фазі `test`, якщо збірка дійшла до цього етапу.

## Каталог тестів

Наведений перелік зіставлено з тестовими файлами в гілці `develop`. Описи вказують на основні сценарії кожного класу, а не на вичерпний список окремих методів усередині нього.

### `app`

- `NeuronMapAppTest` — вибір початкового проєкту та перевірки життєвого циклу вікна: послідовність збереження геометрії й завершення редактора, одноразове виконання та збереження первинної помилки при кількох збоях.
- `MainWindowControllerTest` — контракт і життєвий цикл головного вікна: передавання перевіреної конфігурації, незалежність від фасаду редактора, делегування оформлення до `WindowChromeLifecycle`, одноразове завершення та очищення оформлення під час помилки завершення редактора.
- `WindowChromeLifecycleExtendedWindowTest` — єдиний шлях оформлення через `StageStyle.EXTENDED` і `HeaderBar`, системні ролі кнопок, виключення інтерактивних кнопок із перетягуваної області та явна відмова, коли `EXTENDED_WINDOW` не підтримується.
- `NeuronMapEditorTest` — контракт прикладного фасаду життєвого циклу редактора й перевірка, що він не утримує файлову інфраструктуру.

### `application`

- `DemoMapFactoryTest` — створення демонстраційної карти.
- `EditorStateTest` — режими редактора, вибір об'єктів і скидання стану.
- `EditorStateCameraValidationTest` — відхилення некоректних значень камери конструктором і сетерами зі збереженням попереднього стану.
- `InitialProjectInitializerTest` — початкове наповнення лише порожнього тимчасового проєкту; збережені та вже заповнені проєкти не змінюються.
- `NeuronMapApplicationServiceTest` — надання служб і передавання операцій відповідним компонентам.

### `config`

- `AppConfigLoaderTest` — читання XML-конфігурації та перевірка допустимості параметрів.

### `controller`

- `ConnectionCreationControllerTest` — сценарії створення зв'язків і керування їхнім прев'ю.
- `EditorKeyboardControllerTest` — маршрутизація гарячих клавіш і завершення реєстрації обробників.
- `MainMenuControllerTest` — навігація меню та життєвий цикл попереднього перегляду, підтвердження й відкату налаштувань.
- `NeuronDeletionControllerTest` — видалення нейронів через контролер взаємодії.
- `NeuronInteractionControllerMouseButtonTest` — поведінка взаємодії залежно від кнопки миші.
- `NeuronMenuControllerDirectionTest` — вибір напрямку нейрона через його меню.
- `NeuronOverlayCoordinateTest` — обчислення координат меню та накладок відносно нейрона.
- `PulseAnimationControllerTest` — керування анімаціями імпульсів.
- `SimulationControllerSpeedFormattingTest` — формат швидкості в локалізованому повідомленні після зміни швидкості активної симуляції.

### `coordinator`

- `EditorProjectSessionCoordinatorTest` — зупинка й відновлення редакторської сесії, перемикання проєктів, камера та швидкість симуляції.
- `EditorSaveCoordinatorTest` — узгоджене збереження стану редактора.
- `MapPresentationCoordinatorLifecycleTest` — створення, оновлення та очищення графічних подань карти й ручок обертання.
- `MapPresentationWorkflowLifecycleTest` — синхронізація реального дерева JavaFX після вставлення, undo/redo та перемикання проєктів.
- `ProjectWorkflowCoordinatorActivationTest` — послідовність активації проєкту.
- `ProjectWorkflowCoordinatorRecoveryTest` — відновлення після помилок під час операцій над проєктами.
- `ProjectWorkflowCoordinatorRenameLifecycleTest` — перевірка кількості викликів `close()` під час успішного перейменування активного проєкту та відновлення після помилки.
- `ProjectWorkflowCoordinatorTest` — основні сценарії створення, відкриття, перейменування та видалення проєктів.
- `SimulationSpeedCoordinatorTest` — валідація введеної швидкості, запис до активного репозиторію, застосування значення та пропуск оновлення метаданих для тимчасового проєкту.

### `i18n`

- `LocalizationServicePreviewRollbackTest` — повернення до підтвердженої мови після скасування попереднього перегляду.
- `LocalizationServicePreviewTransactionTest` — транзакційний життєвий цикл попереднього перегляду мови.
- `LocalizationServiceTest` — зміна локалі, слухачі, порядок мов і збереження вибору.

### `model`

- `CameraStateTest` — допустимий діапазон масштабу та відхилення нечислових, нескінченних і позадіапазонних значень камери.
- `NeuronMapModelTest` — цілісність карти, напрямлені зв'язки, унікальність і видалення.
- `NeuronPresentationDirectionTest` — напрямок візуального представлення нейрона.
- `NeuronPresentationTest` — положення, обертання й напрямок представлення.
- `NeuronTest` — параметри нейрона та перевірка некоректних значень.

### `persistence/map`

- `TransientProjectRepositoryTest` — відкладене створення каталогу проєкту та захист від сторонніх файлів і наявних даних.

### `persistence/project`

- `FileSystemProjectCatalogRepositoryTest` — каталог проєктів у файловій системі, перейменування та помилки файлових операцій.
- `FileSystemProjectCatalogMetadataTest` — збереження проєкту в каталозі з відсутньою датою та діагностика помилки читання часу файла.
- `ProjectDirectoryWatcherTest` — виявлення змін каталогу та безпечне завершення спостереження.

### `persistence/settings`

- `GlobalSettingsLanguagePreferenceStoreTest` — адаптер збереження вибраної мови.
- `GlobalSettingsStoreTest` — запис і повторне читання глобальних налаштувань.

### `persistence/sqlite`

- `DefaultMapRepositoryFactoryTest` — вибір репозиторію для типу проєкту.
- `NeuronDirectionPersistenceTest` — збереження напрямку нейрона.
- `SqliteCameraStateValidationTest` — відновлення типової камери та журналювання відсутніх, нечислових, нескінченних і позадіапазонних значень.
- `SqliteMapRepositoryFailureTest` — помилки репозиторію SQLite.
- `SqliteMapRepositoryTest` — збереження та читання карти.
- `SqliteNeuronSchemaContractTest` — контракт схеми даних нейрона.
- `SqliteSchemaMigrationTest` — міграція старої схеми SQLite.
- `SqliteSimulationSettingsTest` — збереження параметрів симуляції та значень за замовчуванням.

### `persistence/window`

- `WindowStateStoreTest` — збереження геометрії вікна й обробка некоректних значень.
- `WindowStateStoreDiagnosticsTest` — діагностика некоректних escape-послідовностей і чисел зі збереженням безпечного fallback; відсутність файла не створює попередження.

### `service`

- `ConnectionServiceTest` — правила створення та видалення зв'язків.
- `GroupServiceTest` — операції з групами й переміщення їхніх учасників.
- `MapServiceProjectActivationTest` — активація проєктного репозиторію та пов'язаний стан карти.
- `NeuronClipboardServiceTest` — копіювання й вставлення нейронів і груп.
- `NeuronServiceTest` — створення, пошук, зміна параметрів і видалення нейрона.
- `ProjectCatalogServiceTest` — каталог проєктів, доступні назви, перейменування, сортування й оновлення з файлової системи.

### `service/history`

- `FieldHistoryTest` — скасування, повторення, розгалуження історії та обмеження її розміру.
- `FieldStateSnapshotTest` — вміст знімків стану та відокремлення збережених даних від runtime-активації.

### `settings`

- `SettingsEditSessionTest` — реєстрація типізованих параметрів, початкові знімки, попередній перегляд, підтвердження, відкат, відновлення після помилок і перевірка дескрипторів.

### `simulation`

- `SimulationRuntimeStateTest` — застосування та очищення поточних активацій.
- `SimulationServiceTest` — створення сеансу симуляції.
- `SimulationSessionTest` — поширення сигналів, цикли, кілька джерел і спільний відлік тактів.
- `SimulationSpeedTest` — перевірка допустимих значень швидкості.
- `SimulationStepTest` — подання номера такту, більшого за межу `long`.
- `SimulationTickTest` — збільшення номера такту та перевірка від'ємних значень.

### `util`

- `CameraWorldCenterTest` — перетворення центра видимої області в координати карти.
- `GeometryUtilsNonFiniteZoomTest` — відхилення `NaN` та нескінченностей і обмеження скінченного масштабу.
- `GeometryUtilsTest` — обмеження масштабу.
- `NeuronRotationMathTest` — обчислення кутів обертання.
- `NumberDisplayFormatterTest` — компактний формат числа, допуск для цілих значень і незалежність від системної локалі.
- `WorldCoordinateMapperTest` — перетворення координат між простором світу та екраном.

### `view/connection`

- `ConnectionHitTesterTest` — визначення зв'язку під курсором.
- `ConnectionPreviewViewTest` — геометрія, показ і приховування прев'ю зв'язку.

### `view/editor`

- `ToolbarViewTest` — склад панелі інструментів, введення та відображення швидкості симуляції.

### `view/menu`

- `MainMenuPageViewsTest` — поведінка окремих подань сторінок головного меню.
- `MainMenuPagesCompositionTest` — компонування сторінок у контейнері меню.
- `MainMenuViewTest` — навігація, адаптивне компонування, незбережені зміни та поведінка сторінок меню.
- `MainMenuUnsavedChangesWorkflowTest` — координація збереження й відхилення перейменувань та налаштувань під час навігації й закриття меню, зокрема відновлення після помилки збереження.

### `view/neuron`

- `NeuronContextMenuViewTest` — склад і поведінка контекстного меню нейрона.
- `NeuronInputSignalDisplayTest` — відображення вхідних сигналів та їх скидання.
- `NeuronMenuViewTest` — подання меню нейрона.
- `NeuronOutputTriangleAlignmentTest` — вирівнювання графічного індикатора виходу.
- `NeuronOverlayPositionerTest` — розрахунок положення накладок.
- `NeuronViewDirectionRefreshTest` — оновлення вигляду після зміни напрямку нейрона.
- `NeuronVisualGeometryTest` — геометрія візуального представлення нейрона.
- `RotationHandleViewTest` — положення та поведінка ручки обертання.

### `view/simulation`

- `PulseAnimationViewTest` — рух імпульсу з урахуванням геометрії зв'язку.

### `view/status`

- `StatusMessagePresenterTest` — прив'язка повідомлень до конкретного рядка стану та звільнення його ресурсів.

### `view/support`

- `JavaFxNodeLookupTest` — пошук вузлів і предків у дереві JavaFX.

### `view/window`

- `WindowChromeIconFactoryTest` — геометрія, стилі та розміри векторних значків окремо від життєвого циклу вікна.
- `WindowChromeLocalizationTest` — локалізовані доступні назви кнопок, вибір платформного стилю, фактичне геометричне центрування й обмеження довгої назви macOS після CSS/layout, відповідний повноекранний значок, компонування Windows/Linux, збереження сторонньої заміни кореня, очищення таблиць стилів і заборона повторного встановлення після `dispose()`.
- `WindowChromeLifecycleExtendedWindowTest` — перевірка інтеграції заголовка з API JavaFX у єдиному режимі `EXTENDED`.

## Принципи написання тестів

Нові тести мають перевіряти очікувану поведінку, а не повторювати алгоритм реалізації. За потреби включай:

- типовий успішний сценарій;
- граничні та некоректні вхідні значення;
- поведінку під час помилки, особливо там, де потрібно звільнити ресурс або зберегти початковий стан;
- регресійний сценарій, який доводить, що попередній дефект не повернувся.

Для моделі, служб, історії та обчислень віддавай перевагу тестам без JavaFX-вікна. Для UI-компонентів перевіряй структуру й поведінку на JavaFX Application Thread. Не залеж від випадкового порядку тестів або зовнішніх даних. Не позначай перевірку успішною, якщо команда фактично не виконувалася.

## Додаткові регресійні перевірки

Для змін, пов'язаних зі швидкістю симуляції та меню мови, додатково перевіряються:

- `SimulationSpeedCoordinatorTest` — некоректне введення не змінює швидкість і не записується до сховища;
- `SimulationControllerSpeedFormattingTest` — локалізоване повідомлення про зміну швидкості використовує спільний формат числового значення;
- `EditorProjectSessionCoordinatorTest` — відсутнє або некоректне значення швидкості нового проєкту замінюється типовим значенням конфігурації, а не успадковується від попереднього проєкту;
- `MainMenuViewTest` — використовується єдиний контракт callback-ів налаштування мови;
- `MapPresentationWorkflowLifecycleTest` — перемикання проєкту працює з актуальною сигнатурою `EditorProjectSessionCoordinator`, без постачальника швидкості попереднього проєкту.

Запускай `./mvnw clean test` (у Windows — `.\mvnw.cmd clean test`) перед передаванням змін. Не вважай тести успішними, якщо команда фактично не виконувалася.
