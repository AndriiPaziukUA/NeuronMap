# Тестування NeuronMap

Тести розміщені в `src/test/java`. Вони згруповані за відповідальністю компонентів; перед зміною коду перевір насамперед тести того пакета, якого торкається зміна.

## Команди

Усі тести, з очищенням попередніх результатів:

```powershell
# Windows PowerShell
.\mvnw.cmd clean test

# Linux/macOS
./mvnw clean test
```

Якщо Maven встановлено окремо, можна запустити `mvn clean test`.

Окремий клас:

```bash
mvn -Dtest=NeuronServiceTest test
```

Кілька класів:

```bash
mvn -Dtest=NeuronServiceTest,SimulationSessionTest test
```

Під час `test` JaCoCo формує звіт покриття в `target/site/jacoco/`, якщо збірка дійшла до етапу звіту.

## Що перевіряють тести

### `app`

- `NeuronMapAppTest` — початковий вибір проєкту й запускову логіку застосунку.

### `application`

- `DemoMapFactoryTest` — створення демонстраційної карти.
- `EditorStateTest` — режими редактора, вибір об'єктів і скидання стану.
- `NeuronMapApplicationServiceTest` — створення служб і передавання операцій до потрібних компонентів.

### `config`

- `AppConfigLoaderTest` — читання XML-конфігурації та перевірка допустимих значень.

### `controller`

- `ConnectionCreationControllerTest` — сценарії створення зв'язків.
- `MainMenuControllerTest` — навігація меню та дії контролера.
- `NeuronInteractionControllerMouseButtonTest` — відмінності обробки кнопок миші.
- `NeuronMapControllerTest` — складання й керування редактором карти.
- `NeuronOverlayCoordinateTest` — обчислення координат меню та накладок відносно нейрона.
- `NeuronRotationMathTest` — математичні обчислення обертання.
- `PulseAnimationControllerTest` — керування анімацією імпульсів.
- `SettingsEditSessionLifecycleTest` — порядок початку, збереження й скасування учасників сесії налаштувань у головному меню.

### `coordinator`

- `EditorSaveCoordinatorTest` — послідовність збереження стану редактора.
- `MapPresentationCoordinatorLifecycleTest` — життєвий цикл компонентів представлення карти.
- `ProjectWorkflowCoordinatorActivationTest` — активація потрібного проєкту.
- `ProjectWorkflowCoordinatorRecoveryTest` — відновлення сценаріїв роботи з проєктами після помилок.
- `ProjectWorkflowCoordinatorTest` — основні сценарії взаємодії з проєктами.

### `i18n`

- `LocalizationServiceTest` — перемикання мови, слухачі, порядок доступних мов і збереження вибору.
- `LocalizationServicePreviewTransactionTest` — життєвий цикл попереднього перегляду мови.
- `LocalizationServicePreviewRollbackTest` — відновлення підтвердженої мови після скасування.

### `model`

- `NeuronMapModelTest` — цілісність карти, спрямовані зв'язки, унікальність і видалення об'єктів.
- `NeuronPresentationDirectionTest` — напрямок відображення нейрона.
- `NeuronPresentationTest` — положення, обертання й напрямок представлення.
- `NeuronTest` — семантичні дані нейрона та перевірка некоректних значень.

### `persistence`

- `DefaultMapRepositoryFactoryTest` — вибір репозиторію для різних типів проєктів.
- `FileSystemProjectCatalogRepositoryTest` — доступ до каталогу проєктів у файловій системі.
- `GlobalSettingsLanguagePreferenceStoreTest` — збереження налаштування мови в глобальних налаштуваннях.
- `GlobalSettingsStoreTest` — збереження та повторне читання загальних налаштувань.
- `NeuronDirectionPersistenceTest` — збереження напрямку нейрона.
- `ProjectDirectoryWatcherTest` — сповіщення про зміни папок і безпечне завершення спостереження.
- `SqliteMapRepositoryFailureTest` — обробка помилок під час роботи з SQLite-репозиторієм карти.
- `SqliteMapRepositoryTest` — запис і читання карти та вимоги до структури бази даних.
- `SqliteNeuronSchemaContractTest` — перелік і призначення полів нейрона в базі даних.
- `SqliteSchemaMigrationTest` — перенесення даних зі старої структури таблиць.
- `SqliteSimulationSettingsTest` — збереження параметрів симуляції та значень за замовчуванням.
- `TransientProjectRepositoryTest` — відкладене створення папки проєкту, обробка сторонніх файлів і захист наявних даних.
- `WindowStateStoreTest` — збереження геометрії вікна та обробка некоректних значень.

### `service`

- `ConnectionServiceTest` — правила створення й видалення зв'язків.
- `GroupServiceTest` — операції з групами та переміщення їхніх елементів.
- `NeuronClipboardServiceTest` — копіювання й вставлення нейронів і груп.
- `NeuronServiceTest` — створення, пошук, зміна параметрів і видалення нейрона.
- `ProjectCatalogServiceTest` — пошук проєктів за папками, назви, перейменування, порядок списку та зміни з боку файлової системи.

### `service/history`

- `FieldHistoryTest` — збереження станів, скасування й повторення змін, розгалуження історії та її обмеження.
- `FieldStateSnapshotTest` — вміст знімка стану й відмінності між постійними даними та поточним станом активації.

### `settings`

- `SettingsEditSessionTest` — попередній перегляд типізованих налаштувань, підтвердження змін і відкат початкових значень без запису до сховища.

### `simulation`

- `SimulationRuntimeStateTest` — застосування та очищення runtime-активацій нейронів.
- `SimulationServiceTest` — створення сеансу симуляції.
- `SimulationSessionTest` — поширення сигналів, цикли, кілька джерел і спільний відлік тактів.
- `SimulationSpeedTest` — допустимі значення швидкості.
- `SimulationStepTest` — представлення номера такту, більшого за межу `long`.
- `SimulationTickTest` — збільшення номера такту та перевірка від'ємних значень.

### `util`

- `CameraWorldCenterTest` — переведення центра видимої області в координати карти.
- `GeometryUtilsTest` — обмеження масштабу.
- `WorldCoordinateMapperTest` — перетворення координат між просторами світу та екрана.

### `view`

- `ConnectionHitTesterTest` — визначення зв'язку під курсором.
- `ConnectionPreviewViewTest` — попередній перегляд створення зв'язку.
- `JavaFxNodeLookupTest` — пошук вузлів JavaFX у дереві подання.
- `MainMenuPageViewsTest` — окремі сторінки головного меню.
- `MainMenuPagesCompositionTest` — склад сторінок меню та зв'язок між ними.
- `MainMenuViewTest` — структура меню, скасування редагування та введення назви.
- `NeuronContextMenuViewTest` — вміст і поведінка контекстного меню нейрона.
- `NeuronInputSignalDisplayTest` — відображення суми вхідних сигналів і її скидання.
- `NeuronMenuViewTest` — подання меню нейрона.
- `NeuronOutputTriangleAlignmentTest` — вирівнювання трикутника виходу нейрона.
- `NeuronOverlayPositionerTest` — розрахунок положення накладок.
- `NeuronViewDirectionRefreshTest` — оновлення вигляду після зміни напрямку нейрона.
- `NeuronVisualGeometryTest` — геометрія графічного представлення нейрона.
- `PulseAnimationViewTest` — рух імпульсу відповідно до змін геометрії зв'язку.
- `RotationHandleViewTest` — положення та поведінка ручки обертання.
- `StatusMessagePresenterTest` — показ статусних повідомлень.
- `ToolbarViewTest` — порядок і склад елементів панелі інструментів.

## Як додавати тести

Під час зміни поведінки додай перевірку в тестовий клас того самого пакета або створи окремий тест для нової відповідальності. Тест має описувати очікувану поведінку, а не повторювати рядки реалізації.

Корисний набір зазвичай містить:

- звичайний коректний сценарій;
- відсутній або невідомий об'єкт, якщо це можливо;
- граничні значення та некоректні параметри;
- регресійний сценарій для помилки, яку виправляємо.

Для симуляції, моделі, служб і збереження перевагу слід віддавати тестам без запуску JavaFX-вікна. Для подань JavaFX перевіряй структуру й поведінку UI окремо від бізнес-правил.
