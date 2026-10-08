# NeuronMap — довідник тестів

Тести дзеркалять production packages. Основна ідея: тестувати behavior та invariants, а не implementation details.

## Як запускати

```powershell
mvn clean test
```

Окремий клас:

```powershell
mvn -Dtest=ProjectCatalogServiceTest test
```

Кілька класів:

```powershell
mvn -Dtest=ProjectCatalogServiceTest,ProjectDirectoryWatcherTest test
```

## Що покриває кожен пакет

### `application`
- `EditorStateTest` — editor mode, selection та reset поведінка.
- `NeuronMapApplicationServiceTest` — service graph та делегування persistence.

### `application/history`
- `FieldHistoryTest` — undo/redo, branching, limit 30 кроків, ігнор runtime-only changes.
- `FieldStateSnapshotTest` — round-trip persistent state та виключення activation-only changes.

### `config`
- `AppConfigLoaderTest` — XML config, bundled config, validation simulation range.

### `controller`
- `NeuronInteractionControllerMouseButtonTest` — відмінність left/right click interaction.
- `NeuronOverlayCoordinateTest` — screen-space geometry меню та overlay.
- `NeuronRotationMathTest` — rotation convention та drag behavior.
- `PulseAnimationControllerTest` — animation survives removal of source connection view.

### `i18n`
- `LocalizationServiceTest` — sorting, listeners, preview/persistence semantics.

### `model`
- `NeuronMapModelTest` — directed connections, uniqueness, self-loop rejection, cleanup after deletion.
- `NeuronPresentationDirectionTest` — direction state.
- `NeuronPresentationTest` — position, rotation, direction.
- `NeuronTest` — semantic neuron state та validation.

### `persistence`
- `GlobalSettingsStoreTest` — properties round-trip.
- `LegacyProjectStorageMigratorTest` — old layout migration.
- `NeuronDirectionPersistenceTest` — direction survives SQLite round-trip.
- `ProjectDirectoryWatcherTest` — filesystem discovery callback та idempotent close.
- `SqliteMapRepositoryTest` — map round-trip, durable pragmas, schema separation.
- `SqliteNeuronSchemaContractTest` — persisted neuron columns contract.
- `SqliteSchemaMigrationTest` — legacy coordinate migration.
- `SqliteSimulationSettingsTest` — simulation speed persistence and fallback.
- `TransientProjectRepositoryTest` — lazy materialization, junk cleanup, pending settings, no overwrite.
- `WindowStateStoreTest` — window geometry persistence and invalid-state fallback.

### `service`
- `ConnectionServiceTest` — connection business logic lives outside controllers.
- `GroupServiceTest` — group movement.
- `NeuronClipboardServiceTest` — copying/pasting state and whole-group selection.
- `NeuronServiceTest` — service delegation for create/toggle/settings/remove.
- `ProjectCatalogServiceTest` — folder-based discovery, naming, rename, external discovery, ordering, last-opened preference isolation.

### `simulation`
- `SimulationSessionTest` — pending targets, cycles, batching manual starts, global tick continuity, single emission per tick.
- `SimulationSpeedTest` — valid range and rejection outside range.
- `SimulationStepTest` — tick representation beyond `long` maximum.
- `SimulationTickTest` — unbounded advancement and negative-value validation.

### `util`
- `CameraWorldCenterTest` — viewport center conversion and invalid camera validation.
- `GeometryUtilsTest` — zoom clamping.

### `view`
- `MainMenuViewTest` — menu structure, settings discard, pending rename.
- `NeuronInputSignalDisplayTest` — temporary input signal reset.
- `NeuronOutputTriangleAlignmentTest` — direction-specific output alignment.
- `NeuronOverlayPositionerTest` — screen-space offset geometry.
- `NeuronVisualGeometryTest` — visible ports, output triangle, connection anchors.
- `PulseAnimationViewTest` — geometry refresh from snapshot.
- `RotationHandleViewTest` — handle placement, orbit, zoom, hit area.
- `ToolbarViewTest` — numeric speed input and dynamic controls.

## Test naming convention

Назва тесту повинна пояснювати observable behavior:

```text
verb + condition + expected result
```

Наприклад:

```text
existingProjectDatabaseIsNeverOverwritten
```

краще за:

```text
materializeTest2
```

## Коли писати новий тест

Пиши тест, якщо:

- виправляєш bug;
- додаєш business rule;
- міняєш persistence format/schema;
- змінюєш interaction contract;
- змінюєш важливу geometric invariant;
- додаєш compatibility behavior.

Особливо цінні тести на edge cases. Саме вони не дають повернутися старим проблемам із transient projects, SQLite WAL, history branching або зовнішньою появою project folders.

## Чого не тестувати

Не тестуй локальні implementation details, якщо достатньо перевірити observable behavior.

Не роби тест залежним від абсолютного шляху користувача.

Persistence tests повинні використовувати temporary directories/databases.

JavaFX tests повинні перевіряти геометрію та behavior, а не внутрішній порядок приватних helper-викликів.
