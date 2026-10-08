# NeuronMap

NeuronMap — JavaFX-застосунок для створення, редагування та симуляції карт нейронів.
Проєкт побудований навколо чіткого розділення відповідальностей: доменна модель не знає про JavaFX, persistence не залежить від UI, а координатори лише з'єднують готові компоненти між собою.

Цей README зроблений не як маркетинговий опис, а як робочий довідник для розробника, який уперше відкрив репозиторій і хоче самостійно зрозуміти, куди йти з конкретною задачею.

## 1. З чого почати

Для першого знайомства читай файли в такому порядку:

1. `src/main/java/com/example/neuronmap/app/NeuronMapApp.java` — точка запуску та composition root.
2. `src/main/java/com/example/neuronmap/application/NeuronMapApplicationService.java` — збирає application/service graph.
3. `src/main/java/com/example/neuronmap/model/` — доменні об'єкти карти.
4. `src/main/java/com/example/neuronmap/service/` — операції над доменною моделлю.
5. `src/main/java/com/example/neuronmap/simulation/` — чиста логіка симуляції.
6. `src/main/java/com/example/neuronmap/persistence/` — SQLite та файлове збереження.
7. `src/main/java/com/example/neuronmap/controller/` і `view/` — JavaFX-взаємодія та відображення.
8. `src/main/java/com/example/neuronmap/coordinator/` — orchestration між UI, services та state.
9. `src/test/java/` — приклади очікуваної поведінки; тести часто є найкращою документацією для edge cases.

Детальні правила навігації описані в [ARCHITECTURE.md](docs/ARCHITECTURE.md), а список тестів — у [TESTING.md](docs/TESTING.md).

## 2. Архітектура в одному погляді

```text
app
 └─ application
     ├─ state / use cases
     └─ services
         ├─ model
         ├─ simulation
         └─ persistence

JavaFX side:
controller ──> service/application
view       <── controller/coordinator
coordinator ──> controller + service + state

persistence не імпортує JavaFX.
simulation не імпортує JavaFX.
model не імпортує JavaFX.
```

Головне правило: **не тягни відповідальність нижчого рівня в шар вищого рівня**. Якщо задача виглядає так, ніби для неї треба додати SQL у controller або JavaFX `Node` у service — майже напевно обраний неправильний шар.

## 3. Структура `src/main/java`

### `app`
Точка входу застосунку. Тут дозволено зібрати залежності, вибрати конкретні реалізації repository, відкрити JavaFX-вікно та підняти інфраструктурні компоненти.

**Лізь сюди, коли:** змінюється bootstrap, startup/shutdown або composition root.

### `application`
Стан редактора та application-level composition. Тут немає SQL і немає деталізованого JavaFX-коду.

**Лізь сюди, коли:** задача стосується editor state, application service graph, history snapshots.

### `config`
Завантаження конфігурації з `src/main/resources/app-config.xml`.

**Лізь сюди, коли:** змінюється формат або читання конфігурації застосунку.

### `controller`
Тонкі адаптери JavaFX: mouse/keyboard events, JavaFX controls, animation lifecycle, menu interaction.

**Лізь сюди, коли:** задача починається зі слів «натиснув кнопку», «клікнув мишкою», «перетягнув», «показати/сховати». Не клади сюди правила доменної моделі.

### `coordinator`
Оркестрація кількох компонентів. Coordinator не повинен ставати другим service layer.

**Лізь сюди, коли:** одна UI-операція проходить через декілька controller/service і потрібен порядок викликів.

### `i18n`
Локалізація, підтримувані мови, перемикання та збереження поточної мови.

**Лізь сюди, коли:** додається мова або змінюється механізм локалізації.

### `model`
Чисті доменні об'єкти: нейрони, зв'язки, групи, карта, presentation-стан нейрона.

**Лізь сюди, коли:** змінюється бізнес-правило карти. Зміни тут не повинні вимагати JavaFX.

### `persistence`
Контракти та реалізації збереження, SQLite, файловий watcher, глобальні налаштування, migration.

**Лізь сюди, коли:** задача стосується файлів, SQLite, project folder, settings, migration або lifecycle repository.

### `service`
Бізнес-операції над model. Services повинні залишатися простими й без UI.

**Лізь сюди, коли:** потрібно створити/видалити/змінити нейрон, зв'язок, групу, clipboard або зберігання карти через repository abstraction.

### `simulation`
Чиста симуляція сигналів і часу. Тут немає JavaFX event loop.

**Лізь сюди, коли:** змінюється правило propagation, tick, activation threshold або semantics manual start.

### `util`
Маленькі загальні helpers. Якщо helper починає зберігати стан, керувати бізнес-правилами або координувати компоненти — це вже не `util`.

### `view`
Візуальні JavaFX-компоненти. View відповідає за представлення, а не за збереження та бізнес-правила.

**Лізь сюди, коли:** змінюється геометрія, layout, visual state, menu appearance або графічний компонент.

## 4. Куди йти з конкретною задачею

| Задача | Перший файл/шар |
|---|---|
| Додати властивість нейрона | `model/Neuron.java` → `service/NeuronService.java` → persistence тести |
| Змінити правила зв'язків | `model/NeuronMapModel.java` / `service/ConnectionService.java` |
| Змінити сигналізацію | `simulation/SimulationSession.java` та `simulation/SimulationStep.java` |
| Змінити швидкість симуляції | `simulation/SimulationSpeed.java` → `controller/SimulationController.java` |
| Змінити Undo/Redo | `application/history/` → `service/HistoryService.java` → `coordinator/EditorHistoryCoordinator.java` |
| Змінити меню проєктів | `controller/MainMenuController.java` → `view/MainMenuView.java` → `service/ProjectCatalogService.java` |
| Змінити формат project storage | `persistence/ProjectStorageDirectoryResolver.java`, `ProjectCatalogService.java`, repositories та migration |
| Додати реакцію на зовнішнє копіювання проєкту | `persistence/ProjectDirectoryWatcher.java` |
| Змінити SQLite schema | `persistence/SqliteSchema.java` + migration/contract tests |
| Змінити завантаження/збереження карти | `persistence/SqliteMapLoader.java` / `SqliteMapWriter.java` / `SqliteMapRepository.java` |
| Змінити JavaFX геометрію | `view/` або `util/GeometryUtils.java` |
| Змінити click/drag/keyboard behavior | відповідний `controller/*Controller.java` |
| Додати переклад | `src/main/resources/i18n/*.properties` + `i18n/LocalizationService.java` |
| Змінити startup | `app/NeuronMapApp.java` |

## 5. Як працюють проєкти та збереження

Файлова система є source of truth для списку збережених проєктів.

На Windows корінь storage:

```text
%USERPROFILE%\Documents\NeuronMap\
```

Кожен проєкт має власну папку:

```text
NeuronMap\
├─ Project A\
│  └─ project.db
├─ Project B\
│  └─ project.db
└─ Project C\
   └─ project.db
```

Папка без `project.db` не вважається saved project.

Новий transient project спочатку існує тільки в пам'яті. Папка створюється лише тоді, коли є що реально зберегти. Якщо папка цільового імені вже існує, але `project.db` немає, вона може бути очищена та перевикористана. Існуючий `project.db` ніколи не перезаписується при materialization нового transient project.

Останній відкритий project зберігається окремо в `neuronmap-global.properties`; це лише preference, а не registry збережених проєктів.

`ProjectDirectoryWatcher` стежить за root та project directories. Якщо інший Windows PC або інший процес додає/видаляє project folder, список може бути оновлений без перезапуску застосунку.

## 6. Життєвий цикл запуску

Спрощено startup виглядає так:

1. `NeuronMapApp` завантажує config.
2. Визначається `%USERPROFILE%\Documents\NeuronMap`.
3. Виконується one-time migration старого storage, якщо marker ще відсутній.
4. `ProjectCatalogService` читає project folders.
5. Обирається останній відкритий saved project, перший saved project або transient project.
6. Вибирається `SqliteMapRepository` або `TransientProjectRepository`.
7. Створюється `NeuronMapApplicationService`.
8. Створюється `NeuronMapController` та його coordinator graph.
9. Підіймається filesystem watcher.
10. JavaFX window створюється та відновлюється.

## 7. Типовий flow зміни карти

Наприклад, користувач додає нейрон:

```text
JavaFX event
   ↓
controller
   ↓
coordinator (якщо треба orchestration)
   ↓
NeuronService
   ↓
NeuronMapModel
   ↓
saveNow()
   ↓
MapService
   ↓
MapRepository
   ↓
SQLite / transient materialization
```

Не потрібно викликати `SqliteMapWriter` безпосередньо з controller.

## 8. Як додати нову функцію без архітектурного боргу

Спочатку сформулюй бізнес-правило без JavaFX. Якщо правило можна перевірити unit-тестом без створення `Scene`, воно майже напевно належить у model/service/simulation.

Потім додай persistence лише якщо стан має пережити перезапуск.

Після цього під'єднай UI через controller/view.

Якщо одна дія торкається кількох компонентів, координуй порядок у coordinator, але не перенось туди самі правила.

Наприкінці додай regression test на щойно зафіксовану поведінку.

## 9. Тести

Повний набір запускається:

```powershell
mvn clean test
```

Після build Maven також генерує JaCoCo report.

Для локальної роботи з окремим тестом зручно:

```powershell
mvn -Dtest=ProjectCatalogServiceTest test
mvn -Dtest=SimulationSessionTest test
```

Тести поділені за тими самими пакетами, що й production code. Це навмисно: шукаючи `Foo.java`, спочатку перевіряй `src/test/java/.../FooTest.java`.

Повний довідник тестів: [TESTING.md](docs/TESTING.md).

## 10. Як читати exception

Починай з найнижчого осмисленого шару:

- `PersistenceException` → дивись `persistence` та source `cause`.
- `IllegalArgumentException` → шукай порушене правило в model/service.
- JavaFX exception → дивись controller/view lifecycle.
- помилка migration → перевіряй legacy files та target project folder.

Не маскуй першопричину новим `catch` лише для того, щоб «не падало». Якщо помилка важлива для діагностики, її треба зберегти через cause.

## 11. Що не робити

Не створюй глобальний `GodService` або `GodController`.

Не додавай JDBC/SQLite у `service`, `controller`, `model` або `simulation`.

Не додавай JavaFX `Node`, `Scene`, `Stage`, `Canvas` у `model`, `service`, `simulation` або persistence.

Не додавай project registry-файл назад у архітектуру. Список saved projects визначає файлова структура.

Не видаляй `LegacyProjectStorageMigrator.java`: це не leftover, а одноразова compatibility migration для старих даних.

Не перезаписуй існуючий `project.db` при materialization нового project.

Не додавай дубльовані helper-и в coordinator, якщо відповідальність уже існує в service/controller.

## 12. Документація коду

Усередині Java-файлів документація має бути українською, короткою та орієнтованою на відповідальність, а не на переказ кожного рядка коду.

Для методу документація повинна відповісти на три питання:

1. Що метод робить?
2. Який важливий побічний ефект або інваріант?
3. Що означає результат/параметри?

Не дублюй очевидний код словами на кшталт «збільшує `x` на один». Пиши про сенс операції для системи.

Скрипт `scripts/Apply-NeuronMapDocumentation.ps1` призначений для підтримки українських Javadocs після появи нових класів/методів. Перед застосуванням рекомендується зробити чистий git working tree.

## 13. Перед commit

Перевір:

```powershell
mvn clean test
git status
git diff --check
```

Якщо змінювалася архітектура, перевір також README/architecture documentation та відповідні regression tests.

## 14. Поточний cleanup після storage refactor

У репозиторії знайдено залишки старого runtime storage:

```text
neuronmap.db
neuronmap-projects\
neuronmap-projects.properties
neuronmap-global.properties
neuronmap-window.properties
.idea/dataSources.xml
```

Для них підготовлено окремий безпечний script: `scripts/Remove-NeuronMapLegacyArtifacts.ps1`. Він перевіряє, що datasource справді посилається на старий `neuronmap.db`, перш ніж видаляти його.

Після cleanup:

```powershell
mvn clean test
git status
```
