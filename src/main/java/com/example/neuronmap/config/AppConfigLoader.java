package com.example.neuronmap.config;

import com.example.neuronmap.ApplicationMetadata;
import com.example.neuronmap.persistence.ApplicationStorageLayout;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Завантажує базові XML-параметри й накладає необов'язкові операційні перевизначення з файлу, середовища та JVM.
 * Базові значення залишаються доступними без зовнішнього файла, а всі підсумкові значення проходять типізовану валідацію.
 */
public final class AppConfigLoader {

    /** Ресурс із вбудованими типовими налаштуваннями. */
    public static final String RESOURCE = "/app-config.xml";

    private static final Logger LOGGER = Logger.getLogger(AppConfigLoader.class.getName());
    private static final String SYSTEM_PROPERTY_PREFIX = "neuronmap.config.";
    private static final String ENVIRONMENT_PREFIX = "NEURONMAP_CONFIG_";
    private static final Set<String> SUPPORTED_KEYS = Set.of(
            "window.defaultWidth",
            "window.defaultHeight",
            "window.stateFile",
            "camera.zoomFactor",
            "simulation.defaultTickMillis",
            "simulation.minTickMillis",
            "simulation.maxTickMillis"
    );
    private static final Map<String, String> ENVIRONMENT_KEYS = Map.ofEntries(
            Map.entry("NEURONMAP_CONFIG_WINDOW_DEFAULT_WIDTH", "window.defaultWidth"),
            Map.entry("NEURONMAP_CONFIG_WINDOW_DEFAULT_HEIGHT", "window.defaultHeight"),
            Map.entry("NEURONMAP_CONFIG_WINDOW_STATE_FILE", "window.stateFile"),
            Map.entry("NEURONMAP_CONFIG_CAMERA_ZOOM_FACTOR", "camera.zoomFactor"),
            Map.entry("NEURONMAP_CONFIG_SIMULATION_DEFAULT_TICK_MILLIS", "simulation.defaultTickMillis"),
            Map.entry("NEURONMAP_CONFIG_SIMULATION_MIN_TICK_MILLIS", "simulation.minTickMillis"),
            Map.entry("NEURONMAP_CONFIG_SIMULATION_MAX_TICK_MILLIS", "simulation.maxTickMillis")
    );

    /** Забороняє створювати екземпляри утиліти завантаження конфігурації. */
    private AppConfigLoader() {
    }

    /**
     * Завантажує конфігурацію з типового каталогу даних, використовуючи джерела середовища поточного процесу.
     *
     * @return перевірена конфігурація застосунку.
     * @throws IllegalStateException якщо базовий ресурс, зовнішній файл або значення конфігурації некоректні.
     */
    public static AppConfig load() {
        return load(ApplicationStorageLayout.resolve());
    }

    /**
     * Завантажує базові налаштування й необов'язково накладає конфігурацію з каталогу даних.
     * Пріоритет зростає в такому порядку: XML за замовчуванням, {@code app-config.properties}, змінні середовища,
     * system properties. Відсутність зовнішнього файла є штатним сценарієм.
     *
     * @param storageLayout компонування файлового сховища, в якому розташовано файл перевизначень.
     * @return перевірена типізована конфігурація застосунку.
     * @throws NullPointerException якщо компонування сховища не задано.
     * @throws IllegalStateException якщо не вдалося завантажити базовий ресурс або налаштування мають помилки.
     */
    public static AppConfig load(ApplicationStorageLayout storageLayout) {
        return load(storageLayout, System.getProperties(), System.getenv());
    }

    /**
     * Завантажує конфігурацію з контрольованими джерелами перевизначень, що дозволяє перевіряти пріоритети без
     * зміни глобальних властивостей процесу в тестах.
     *
     * @param storageLayout компонування файлового сховища.
     * @param systemProperties властивості JVM, ключі конфігурації яких починаються з {@code neuronmap.config.}.
     * @param environment змінні середовища поточного процесу.
     * @return перевірена конфігурація застосунку.
     * @throws NullPointerException якщо будь-який аргумент не задано.
     * @throws IllegalStateException якщо файл конфігурації або ефективні значення некоректні.
     */
    static AppConfig load(
            ApplicationStorageLayout storageLayout,
            Properties systemProperties,
            Map<String, String> environment
    ) {
        Objects.requireNonNull(storageLayout, "storageLayout");
        Objects.requireNonNull(systemProperties, "systemProperties");
        Objects.requireNonNull(environment, "environment");

        AppConfig defaults = loadDefaults();
        Properties fileOverrides = loadOptionalOverrides(storageLayout.configurationOverrideFile());
        return applyOverrides(defaults, fileOverrides, environment, systemProperties);
    }

    /**
     * Читає та перевіряє вбудований XML-ресурс, який завжди є базовим джерелом налаштувань.
     *
     * @return базова конфігурація з ресурсу застосунку.
     * @throws IllegalStateException якщо ресурс відсутній, не читається або містить некоректний XML чи значення.
     */
    private static AppConfig loadDefaults() {
        InputStream input = AppConfigLoader.class.getResourceAsStream(RESOURCE);
        if (input == null) {
            throw new IllegalStateException("Missing application configuration: " + RESOURCE);
        }

        try (input) {
            return parse(input);
        } catch (ParserConfigurationException exception) {
            throw new IllegalStateException(
                    "Cannot configure secure XML parser for application configuration: " + RESOURCE,
                    exception
            );
        } catch (SAXException exception) {
            throw new IllegalStateException("Cannot parse application configuration XML: " + RESOURCE, exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read application configuration: " + RESOURCE, exception);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid application configuration: " + RESOURCE, exception);
        }
    }

    /**
     * Читає необов'язковий файл перевизначень у UTF-8, не створюючи й не перезаписуючи його автоматично.
     *
     * @param path шлях, визначений компонуванням сховища.
     * @return властивості зовнішньої конфігурації або порожній набір, якщо файл відсутній.
     * @throws IllegalStateException якщо наявний файл неможливо прочитати.
     */
    private static Properties loadOptionalOverrides(Path path) {
        Properties properties = new Properties();
        if (Files.notExists(path)) {
            return properties;
        }

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
            return properties;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read optional configuration override file: " + path, exception);
        }
    }

    /**
     * Розбирає XML-потік, перевіряє структуру документа й створює типізовану базову конфігурацію.
     * XML-парсер не завантажує зовнішні сутності й DTD та не розгортає XInclude.
     *
     * @param input відкритий потік XML-конфігурації; метод не закриває потік, бо його власник визначає життєвий цикл.
     * @return конфігурація з базовими параметрами вікна, камери та симуляції.
     * @throws IllegalArgumentException якщо потік не задано або структура/значення конфігурації некоректні.
     * @throws ParserConfigurationException якщо XML-парсер неможливо налаштувати із заданими обмеженнями безпеки.
     * @throws SAXException якщо XML-документ має синтаксичну помилку або порушує обмеження парсера.
     * @throws IOException якщо під час читання XML-потоку сталася помилка введення-виведення.
     */
    static AppConfig parse(InputStream input)
            throws ParserConfigurationException, SAXException, IOException {
        if (Objects.isNull(input)) {
            throw new IllegalArgumentException("input must not be null");
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        Document document = factory.newDocumentBuilder().parse(input);
        Element root = document.getDocumentElement();
        if (root == null || !"app".equals(root.getTagName())) {
            throw new IllegalArgumentException("Root element must be <app>");
        }

        Element window = child(root, "window");
        Element camera = child(root, "camera");
        Element simulation = child(root, "simulation");

        return new AppConfig(
                new AppConfig.Window(
                        ApplicationMetadata.NAME,
                        number(window, "width"),
                        number(window, "height"),
                        required(window, "stateFile")
                ),
                new AppConfig.Camera(number(camera, "zoomFactor")),
                new AppConfig.Simulation(
                        number(simulation, "defaultTickMs"),
                        number(simulation, "minTickMs"),
                        number(simulation, "maxTickMs")
                )
        );
    }

    /**
     * Накладає дозволені перевизначення на базову конфігурацію у визначеному порядку пріоритетів.
     * Невідомі ключі ігноруються з попередженням, а некоректне ефективне значення зупиняє запуск із діагностикою.
     *
     * @param defaults перевірена конфігурація з classpath.
     * @param fileOverrides властивості з необов'язкового зовнішнього файла.
     * @param environment змінні середовища.
     * @param systemProperties властивості JVM.
     * @return конфігурація після накладання та повторної валідації перевизначень.
     */
    private static AppConfig applyOverrides(
            AppConfig defaults,
            Properties fileOverrides,
            Map<String, String> environment,
            Properties systemProperties
    ) {
        Map<String, String> values = new LinkedHashMap<>();
        Map<String, String> sources = new LinkedHashMap<>();
        Map<String, String> fileLayer = new LinkedHashMap<>();
        for (String key : fileOverrides.stringPropertyNames()) {
            fileLayer.put(key, fileOverrides.getProperty(key));
        }
        applyLayer(fileLayer, "app-config.properties", values, sources);
        applyEnvironment(environment, values, sources);
        applySystemProperties(systemProperties, values, sources);

        double width = numberOverride(values, sources, "window.defaultWidth", defaults.window().defaultWidth());
        double height = numberOverride(values, sources, "window.defaultHeight", defaults.window().defaultHeight());
        String stateFile = values.getOrDefault("window.stateFile", defaults.window().stateFile());
        double zoom = numberOverride(values, sources, "camera.zoomFactor", defaults.camera().zoomFactor());
        double defaultTick = numberOverride(
                values, sources, "simulation.defaultTickMillis", defaults.simulation().defaultTickMillis());
        double minTick = numberOverride(
                values, sources, "simulation.minTickMillis", defaults.simulation().minTickMillis());
        double maxTick = numberOverride(
                values, sources, "simulation.maxTickMillis", defaults.simulation().maxTickMillis());

        try {
            return new AppConfig(
                    new AppConfig.Window(ApplicationMetadata.NAME, width, height, stateFile),
                    new AppConfig.Camera(zoom),
                    new AppConfig.Simulation(defaultTick, minTick, maxTick)
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Invalid effective application configuration; check the selected override values and ranges. "
                            + "Applied override sources: " + sources,
                    exception
            );
        }
    }

    /**
     * Додає один шар перевизначень, застосовуючи лише відомі ключі та зберігаючи походження ефективного значення.
     *
     * @param layer ключі й значення поточного джерела.
     * @param source опис джерела для діагностики.
     * @param values набір ефективних значень, який оновлюється.
     * @param sources джерело останнього значення кожного ключа.
     */
    private static void applyLayer(
            Map<String, String> layer,
            String source,
            Map<String, String> values,
            Map<String, String> sources
    ) {
        layer.forEach((key, value) -> {
            if (!SUPPORTED_KEYS.contains(key)) {
                LOGGER.log(Level.WARNING, "Ignoring unknown application configuration key ''{0}'' from {1}",
                        new Object[]{key, source});
                return;
            }
            values.put(key, value);
            sources.put(key, source);
        });
    }

    /**
     * Перетворює відомі змінні середовища на ключі конфігурації й ігнорує невідомі з попередженням.
     *
     * @param environment змінні середовища.
     * @param values набір ефективних значень, який оновлюється.
     * @param sources джерело останнього значення кожного ключа.
     */
    private static void applyEnvironment(
            Map<String, String> environment,
            Map<String, String> values,
            Map<String, String> sources
    ) {
        Map<String, String> layer = new LinkedHashMap<>();
        environment.forEach((environmentKey, value) -> {
            if (!environmentKey.startsWith(ENVIRONMENT_PREFIX)) {
                return;
            }
            String key = ENVIRONMENT_KEYS.get(environmentKey);
            if (key == null) {
                LOGGER.log(Level.WARNING, "Ignoring unknown application configuration environment variable ''{0}''",
                        environmentKey);
                return;
            }
            layer.put(key, value);
        });
        applyLayer(layer, "environment", values, sources);
    }

    /**
     * Застосовує JVM-властивості з префіксом конфігурації як найпріоритетніше джерело.
     *
     * @param systemProperties властивості JVM.
     * @param values набір ефективних значень, який оновлюється.
     * @param sources джерело останнього значення кожного ключа.
     */
    private static void applySystemProperties(
            Properties systemProperties,
            Map<String, String> values,
            Map<String, String> sources
    ) {
        Map<String, String> layer = new LinkedHashMap<>();
        for (String propertyName : systemProperties.stringPropertyNames()) {
            if (propertyName.startsWith(SYSTEM_PROPERTY_PREFIX)) {
                layer.put(propertyName.substring(SYSTEM_PROPERTY_PREFIX.length()),
                        systemProperties.getProperty(propertyName));
            }
        }
        applyLayer(layer, "JVM system properties", values, sources);
    }

    /**
     * Перетворює одне ефективне числове значення та повідомляє, з якого джерела надійшло некоректне перевизначення.
     *
     * @param values ефективні рядкові перевизначення.
     * @param sources джерела перевизначень.
     * @param key ключ параметра.
     * @param defaultValue базове числове значення, якщо перевизначення відсутнє.
     * @return перевірюване числове значення; діапазон додатково контролює модель {@link AppConfig}.
     */
    private static double numberOverride(
            Map<String, String> values,
            Map<String, String> sources,
            String key,
            double defaultValue
    ) {
        String value = values.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(
                    "Invalid numeric application configuration key '" + key + "' from " + sources.get(key),
                    exception
            );
        }
    }

    /**
     * Знаходить безпосередній дочірній XML-елемент за назвою.
     *
     * @param parent батьківський елемент, серед безпосередніх дочірніх вузлів якого шукають секцію.
     * @param name точна назва обов'язкового дочірнього елемента.
     * @return знайдений дочірній елемент.
     * @throws IllegalArgumentException якщо дочірній елемент із заданою назвою відсутній.
     */
    private static Element child(Element parent, String name) {
        for (int index = 0; index < parent.getChildNodes().getLength(); index++) {
            if (parent.getChildNodes().item(index) instanceof Element element
                    && name.equals(element.getTagName())) {
                return element;
            }
        }
        throw new IllegalArgumentException("Missing <" + name + "> configuration section");
    }

    /**
     * Читає непорожнє обов'язкове значення атрибута XML-елемента.
     *
     * @param element XML-елемент, у якому перевіряють атрибут.
     * @param name назва обов'язкового атрибута.
     * @return значення атрибута без додаткового перетворення.
     * @throws IllegalArgumentException якщо атрибут відсутній або містить лише пробіли.
     */
    private static String required(Element element, String name) {
        String value = element.getAttribute(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing attribute '" + name + "' in <" + element.getTagName() + ">"
            );
        }
        return value.trim();
    }

    /**
     * Читає обов'язковий XML-атрибут і перетворює його на число подвійної точності.
     *
     * @param element XML-елемент, у якому міститься числовий атрибут.
     * @param name назва числового атрибута.
     * @return значення атрибута.
     * @throws IllegalArgumentException якщо атрибут відсутній або не є числом.
     */
    private static double number(Element element, String name) {
        String value = required(element, name);
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Attribute '" + name + "' must be numeric", exception);
        }
    }
}
