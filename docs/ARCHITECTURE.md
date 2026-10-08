# NeuronMap — архітектурний довідник

## Мета архітектури

Архітектура NeuronMap розділяє domain rules, application state, business operations, simulation, persistence та JavaFX presentation. Це робиться не заради великої кількості package, а щоб зміна в одному аспекті не змушувала переписувати всю систему.

## Шари

### `app`
Composition root. Єдине місце, де нормально зібрати весь object graph.

Залежить від усіх потрібних шарів. Сам майже не містить business logic.

### `application`
Стан редактора та application-level service graph.

`EditorState` тримає selection, mode, camera state та transient interaction state. `NeuronMapApplicationService` створює specialized services.

### `service`
Business operations над model: neurons, connections, groups, clipboard, history, map persistence facade, project catalog.

Service не повинен знати про JavaFX controls.

### `model`
Предметна область. Об'єкти тут мають бути максимально незалежними від інфраструктури.

### `simulation`
Чиста логіка поширення сигналів та глобального simulation tick. Simulation має бути тестованою без JavaFX runtime.

### `persistence`
Repository contracts, SQLite adapter, settings stores, filesystem watcher, project storage resolver, migration.

Persistence не керує UI.

### `controller`
JavaFX adapters. Контролери читають події UI і викликають application/service API.

### `view`
JavaFX visual components. View відображає стан і вміє керувати власним presentation lifecycle.

### `coordinator`
Оркестрація. Coordinator може сказати «спочатку pause simulation, потім зберегти state, потім refresh view», але не повинен містити database rules чи domain algorithms.

### `util`
Невеликі stateless helpers.

### `i18n`
Локалізація та вибір мови.

## Dependency rules

Безпечний напрямок залежностей:

```text
app
  ↓
application / coordinator
  ↓
controller / service
  ↓
model / simulation / persistence abstractions
```

Але це правило треба читати точніше:

- model не залежить від JavaFX;
- simulation не залежить від JavaFX;
- persistence не залежить від JavaFX;
- view не містить business decisions;
- controller не містить SQL;
- coordinator не стає альтернативою service layer.

## Project storage

Фізичний layout:

```text
%USERPROFILE%\Documents\NeuronMap\
    <Project Name>\project.db
    neuronmap-global.properties
    neuronmap-window.properties
    .legacy-storage-migrated
```

`project.db` — дані конкретного project.

`neuronmap-global.properties` — application-wide preferences, які не повинні належати одному project, наприклад last opened project.

`neuronmap-window.properties` — глобальний UI persistence для geometry вікна.

`.legacy-storage-migrated` — marker, що one-time migration зі старого layout уже була виконана.

## Project discovery

`ProjectCatalogService.listProjects()` не читає registry metadata. Він дивиться на immediate child directories storage root і вважає project saved лише тоді, коли:

```text
<folder>\project.db
```

є regular file.

Це дозволяє просто копіювати project folder на інший Windows PC та отримувати project автоматично після refresh.

## External changes

`ProjectDirectoryWatcher` використовує Java NIO `WatchService`.

Він стежить за root directory та вже відомими immediate project directories. Подія файлової системи приводить до callback, а `MainMenuController` запускає refresh у JavaFX thread.

Watcher не повинен містити logic, яка вирішує, «чи є це project». Він лише повідомляє про зміни. Правила discovery належать `ProjectCatalogService`.

## Transient project

Новий project не матеріалізується на диску просто через відкриття меню New Project.

```text
new project
  ↓
ProjectDescriptor(modifiedAt = null)
  ↓
користувач ще нічого не створив
  ↓
нічого не пишемо на диск
```

Коли зберігається непорожня карта:

```text
transient repository
  ↓
prepare project directory
  ↓
create SqliteMapRepository
  ↓
save model
```

Якщо target directory є, але `project.db` відсутній, directory можна очистити та перевикористати. Якщо `project.db` вже існує — materialization відмовляється, щоб ніколи не перезаписати чужий project.

## Save timestamp

SQLite працює з WAL, тому зміни можуть не оновлювати main `project.db` timestamp так, як очікує project catalog. Після успішного save/relevant settings save `SqliteMapRepository` явно оновлює mtime файлу.

Саме цей timestamp використовується для сортування saved projects.

## Rename

Rename project — це filesystem operation.

Порядок для активного project:

```text
save
↓
close current SQLite repository
↓
move project directory
↓
reopen SQLite repository на новому path
↓
refresh current descriptor
```

Rename project не повинен лише міняти name у memory, тому що folder name є частиною persistence identity.

## Migration

`LegacyProjectStorageMigrator` існує тільки для compatibility з layout старіших версій.

Він читає:

```text
neuronmap.db
neuronmap-projects\
neuronmap-projects.properties
```

і переносить знайдені project databases у новий folder layout.

Після успішної migration створюється marker. Це запобігає повторному автоматичному перенесенню на кожному старті.

Не змішуй migration code з поточним repository runtime behavior.

## Undo/Redo

`FieldStateSnapshot` описує persistent field state. Runtime-only animation/input signal state не повинен створювати зайві history points.

`FieldHistory` зберігає undo/redo stacks.

`HistoryService` — business facade над history.

`EditorHistoryCoordinator` займається UI cleanup після undo/redo: зупиняє simulation, прибирає transient interaction state і синхронізує views.

## Simulation

Simulation має глобальний `BigInteger` tick, тому система не покладається на `long` overflow.

`SimulationSession` збирає:

- manual starts;
- pending input signals;
- activated neurons;
- signals, які підуть на наступний tick.

Ключовий інваріант: neuron, активований одночасно manual start і incoming signal, випускає сигнал лише один раз за tick.

## Як розширювати архітектуру

Перед додаванням нового class запитай себе:

1. Це правило предметної області? → `model`.
2. Це операція над model? → `service`.
3. Це час/propagation simulation? → `simulation`.
4. Це SQL/filesystem? → `persistence`.
5. Це тільки JavaFX event? → `controller`.
6. Це visual composition? → `view`.
7. Це порядок взаємодії кількох уже готових компонентів? → `coordinator`.
8. Це збирання application graph? → `application`/`app`.
9. Це справді маленький stateless helper? → `util`.

Якщо відповідь «ні» на все — клас, імовірно, має занадто розмиту відповідальність.
