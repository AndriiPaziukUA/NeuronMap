# Конфігурація NeuronMap

## Кореневий каталог даних

Типовий каталог даних — `Documents/NeuronMap` у домашньому каталозі поточного користувача. Для portable-розгортання, перенаправленого каталогу або спільного керування файлами корінь можна перевизначити:

1. JVM system property `-Dneuronmap.storage.directory=/absolute/path`;
2. змінною середовища `NEURONMAP_STORAGE_DIRECTORY`;
3. якщо обидва джерела відсутні — типовим шляхом `~/Documents/NeuronMap`.

Якщо задано обидва перевизначення, system property має пріоритет. Відносні значення перевизначення інтерпретуються відносно робочого каталогу процесу й перетворюються на абсолютний нормалізований шлях. Каталог створюється відповідним компонентом збереження під час першої операції запису, а не самим резолвером.

У цьому корені застосунок розміщує глобальні налаштування `global.properties`, необов'язковий файл операційних перевизначень `app-config.properties`, файл геометрії вікна (типово `window.properties`) та каталоги збережених проєктів. Імена runtime-файлів належать `ApplicationStorageLayout`, а корінь — `ProjectStorageDirectoryResolver`.

## Операційні перевизначення

`src/main/resources/app-config.xml` містить базові значення, потрібні для запуску. Додатковий файл `app-config.properties` у кореневому каталозі даних необов'язковий: його відсутність не є помилкою, він автоматично не генерується й не перезаписується застосунком.

Підтримуються лише наведені нижче ключі:

| Ключ у `app-config.properties` | Відповідна змінна середовища | JVM system property | Призначення |
|---|---|---|---|
| `window.defaultWidth` | `NEURONMAP_CONFIG_WINDOW_DEFAULT_WIDTH` | `-Dneuronmap.config.window.defaultWidth=...` | Початкова ширина головного вікна |
| `window.defaultHeight` | `NEURONMAP_CONFIG_WINDOW_DEFAULT_HEIGHT` | `-Dneuronmap.config.window.defaultHeight=...` | Початкова висота головного вікна |
| `window.stateFile` | `NEURONMAP_CONFIG_WINDOW_STATE_FILE` | `-Dneuronmap.config.window.stateFile=...` | Ім'я файла стану вікна |
| `camera.zoomFactor` | `NEURONMAP_CONFIG_CAMERA_ZOOM_FACTOR` | `-Dneuronmap.config.camera.zoomFactor=...` | Множник масштабування за крок |
| `simulation.defaultTickMillis` | `NEURONMAP_CONFIG_SIMULATION_DEFAULT_TICK_MILLIS` | `-Dneuronmap.config.simulation.defaultTickMillis=...` | Типова тривалість такту симуляції |
| `simulation.minTickMillis` | `NEURONMAP_CONFIG_SIMULATION_MIN_TICK_MILLIS` | `-Dneuronmap.config.simulation.minTickMillis=...` | Мінімальна тривалість такту |
| `simulation.maxTickMillis` | `NEURONMAP_CONFIG_SIMULATION_MAX_TICK_MILLIS` | `-Dneuronmap.config.simulation.maxTickMillis=...` | Максимальна тривалість такту |

Значення застосовуються з таким пріоритетом (від нижчого до вищого):

1. вбудований `app-config.xml`;
2. файл `app-config.properties`;
3. змінні середовища `NEURONMAP_CONFIG_*`;
4. JVM system properties `neuronmap.config.*`.

Можна перевизначити лише потрібні параметри — решта зберігає значення нижчого рівня. Числові значення перевіряються після накладання, зокрема перевіряються додатність розмірів і масштабування та узгодженість меж симуляції. Некоректне ефективне значення зупиняє запуск із помилкою, а невідомі ключі ігноруються з попередженням у журналі.

`window.stateFile` має містити лише одне ім'я файла, наприклад `window.properties`. Абсолютні шляхи, імена з роздільниками каталогів, `.`/`..` і схожі спроби виходу за межі сховища заборонені.

## Незмінні метадані та правила сховища

`NeuronMap` є назвою бренду, що визначається єдиною константою `ApplicationMetadata.NAME` і використовується для заголовка вікна та панелі інструментів. Назва не належить до операційних перевизначень.

Інваріанти формату даних і надійності сховища, зокрема версія схеми SQLite, ім'я `project.db`, параметри надійності SQLite та межі масштабу камери, залишаються в коді відповідних власників. `busy_timeout` SQLite вже має одного власника в `SqliteConnectionFactory`; без підтвердженої потреби в операційному tuning він не дублюється в репозиторіях і не виноситься до конфігурації. Не слід переносити інваріанти в зовнішню конфігурацію лише через наявність числового або текстового літерала.

Візуальні параметри залишаються локальними для компонентів: кольори нейронів описані в `neuron.css`, палітра зв'язків — у `ConnectionStyle`, а геометрія та колір координатної сітки — у `GridRenderer.GridStyle`. Вони не об'єднуються в глобальний runtime-конфіг, щоб зміна одного компонента не впливала на інші.
