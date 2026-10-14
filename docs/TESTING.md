# Тестування NeuronMap

Автоматизовані тести містяться в `src/test/java/com/example/neuronmap/`. Вони згруповані відповідно до відповідальності production-коду. Під час зміни компонента спочатку запускай найближчі тести, а перед передаванням змін — повний набір.

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

У Windows використовуйте `.\mvnw.cmd` замість `./mvnw`. Maven Compiler налаштований на Java 21. JavaFX-тести запускають toolkit і виконують перевірки графічних вузлів у JavaFX Application Thread; для них має бути доступне середовище з потрібними JavaFX native-залежностями.

JaCoCo налаштований формувати звіт у `target/site/jacoco/` на фазі `test`, якщо збірка дійшла до цього етапу.

## Каталог тестів

Наведений перелік зіставлено з тестовими файлами в гілці `develop`. Описи вказують на основні сценарії кожного класу, а не на вичерпний список окремих методів усередині нього.

### `app`

- `NeuronMapAppTest` — вибір початкового проєкту та запускова логіка застосунку.

### `application`

- `DemoMapFactoryTest` — створення демонстраційної карти.
- `EditorStateTest` — режими редактора, вибір об'єктів і скидання стану.
- `NeuronMapApplicationServiceTest` — надання служб і передавання операцій відповідним компонентам.

### `config`

- `AppConfigLoaderTest` — читання XML-конфігурації та перевірка допустимості параметрів.

### `controller`

- `ConnectionCreationControllerTest` — сценарії створення зв'язків і керування їхнім прев'ю.
- `MainMenuControllerTest` — навігація меню та життєвий цикл попереднього перегляду, підтвердження й відкату налаштувань.
- `NeuronInteractionControllerMouseButtonTest` — поведінка взаємодії залежно від кнопки миші.
- `NeuronMapControllerTest` — складання й керування редактором карти.
- `NeuronOverlayCoordinateTest` — обчислення координат меню та накладок відносно нейрона.
- `NeuronRotationMathTest` — обчислення кутів обертання.
- `PulseAnimationControllerTest` — керування анімаціями імпульсів.

### `coordinator`

- `EditorProjectSessionCoordinatorTest` — зупинка й відновлення редакторської сесії, перемикання проєктів, камера та швидкість симуляції.
- `EditorSaveCoordinatorTest` — узгоджене збереження стану редактора.
- `MapPresentationCoordinatorLifecycleTest` — створення, оновлення та очищення графічних подань карти й ручок обертання.
- `MapPresentationWorkflowLifecycleTest` — синхронізація реального дерева JavaFX після вставлення, undo/redo та перемикання проєктів.
- `ProjectWorkflowCoordinatorActivationTest` — послідовність активації проєкту.
- `ProjectWorkflowCoordinatorRecoveryTest` — відновлення після помилок під час операцій над проєктами.
- `ProjectWorkflowCoordinatorTest` — основні сценарії створення, відкриття, перейменування та видалення проєктів.

### `i18n`

- `LocalizationServicePreviewRollbackTest` — повернення до підтвердженої мови після скасування попереднього перегляду.
- `LocalizationServicePreviewTransactionTest` — транзакційний життєвий цикл попереднього перегляду мови.
- `LocalizationServiceTest` — зміна локалі, слухачі, порядок мов і збереження вибору.

### `model`

- `NeuronMapModelTest` — цілісність карти, напрямлені зв'язки, унікальність і видалення.
- `NeuronPresentationDirectionTest` — напрямок візуального представлення нейрона.
- `NeuronPresentationTest` — положення, обертання й напрямок представлення.
- `NeuronTest` — параметри нейрона та перевірка некоректних значень.

### `persistence`

- `DefaultMapRepositoryFactoryTest` — вибір репозиторію для типу проєкту.
- `FileSystemProjectCatalogRepositoryTest` — каталог проєктів у файловій системі.
- `GlobalSettingsLanguagePreferenceStoreTest` — адаптер збереження вибраної мови.
- `GlobalSettingsStoreTest` — запис і повторне читання глобальних налаштувань.
- `NeuronDirectionPersistenceTest` — збереження напрямку нейрона.
- `ProjectDirectoryWatcherTest` — виявлення змін каталогу та безпечне завершення спостереження.
- `SqliteMapRepositoryFailureTest` — помилки репозиторію SQLite.
- `SqliteMapRepositoryTest` — збереження та читання карти.
- `SqliteNeuronSchemaContractTest` — контракт схеми даних нейрона.
- `SqliteSchemaMigrationTest` — міграція старої схеми SQLite.
- `SqliteSimulationSettingsTest` — збереження параметрів симуляції та значень за замовчуванням.
- `TransientProjectRepositoryTest` — відкладене створення каталогу проєкту та захист від сторонніх файлів/наявних даних.
- `WindowStateStoreTest` — збереження геометрії вікна й обробка некоректних значень.

### `service`

- `ConnectionServiceTest` — правила створення та видалення зв'язків.
- `GroupServiceTest` — операції з групами й переміщення їхніх учасників.
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
- `GeometryUtilsTest` — обмеження масштабу.
- `WorldCoordinateMapperTest` — перетворення координат між простором світу та екраном.

### `view`

- `ConnectionHitTesterTest` — визначення зв'язку під курсором.
- `ConnectionPreviewViewTest` — геометрія, показ і приховування прев'ю зв'язку.
- `JavaFxNodeLookupTest` — пошук вузлів і предків у дереві JavaFX.
- `MainMenuPageViewsTest` — поведінка окремих подань сторінок головного меню.
- `MainMenuPagesCompositionTest` — компонування сторінок у контейнері меню.
- `MainMenuViewTest` — навігація, адаптивне компонування, незбережені зміни та поведінка сторінок меню.
- `NeuronContextMenuViewTest` — склад і поведінка контекстного меню нейрона.
- `NeuronInputSignalDisplayTest` — відображення вхідних сигналів та їх скидання.
- `NeuronMenuViewTest` — подання меню нейрона.
- `NeuronOutputTriangleAlignmentTest` — вирівнювання графічного індикатора виходу.
- `NeuronOverlayPositionerTest` — розрахунок положення накладок.
- `NeuronViewDirectionRefreshTest` — оновлення вигляду після зміни напрямку нейрона.
- `NeuronVisualGeometryTest` — геометрія візуального представлення нейрона.
- `PulseAnimationViewTest` — рух імпульсу з урахуванням геометрії зв'язку.
- `RotationHandleViewTest` — положення та поведінка ручки обертання.
- `StatusMessagePresenterTest` — прив'язка повідомлень до конкретного рядка стану та звільнення його ресурсів.
- `ToolbarViewTest` — склад і порядок елементів панелі інструментів.

## Принципи написання тестів

Нові тести мають перевіряти очікувану поведінку, а не повторювати алгоритм реалізації. За потреби включай:

- типовий успішний сценарій;
- граничні та некоректні вхідні значення;
- поведінку під час помилки, особливо там, де потрібно звільнити ресурс або зберегти початковий стан;
- регресійний сценарій, який доводить, що попередній дефект не повернувся.

Для моделі, служб, історії та обчислень віддавай перевагу тестам без JavaFX-вікна. Для UI-компонентів перевіряй структуру й поведінку на JavaFX Application Thread. Не залеж від випадкового порядку тестів або зовнішніх даних. Не позначай перевірку успішною, якщо команда фактично не виконувалася.
