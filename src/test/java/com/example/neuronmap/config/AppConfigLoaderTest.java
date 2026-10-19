package com.example.neuronmap.config;

import com.example.neuronmap.ApplicationMetadata;
import com.example.neuronmap.persistence.ApplicationStorageLayout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Перевіряє безпечне завантаження базової конфігурації та порядок застосування операційних перевизначень. */
class AppConfigLoaderTest {

    @TempDir
    private Path temporaryDirectory;

    /** Перевіряє розбір базової XML-конфігурації без залежності від зовнішніх джерел. */
    @Test
    void parsesBaseConfiguration() throws ParserConfigurationException, SAXException, IOException {
        AppConfig config = AppConfigLoader.parse(stream(
                """
                <app>
                    <window width="1400" height="900" stateFile="state.properties"/>
                    <camera zoomFactor="1.25"/>
                    <simulation defaultTickMs="700" minTickMs="40" maxTickMs="3000"/>
                </app>
                """
        ));

        assertEquals(ApplicationMetadata.NAME, config.window().title());
        assertEquals(1400.0, config.window().defaultWidth());
        assertEquals(900.0, config.window().defaultHeight());
        assertEquals("state.properties", config.window().stateFile());
        assertEquals(1.25, config.camera().zoomFactor());
        assertEquals(700.0, config.simulation().defaultTickMillis());
        assertEquals(40.0, config.simulation().minTickMillis());
        assertEquals(3000.0, config.simulation().maxTickMillis());
    }

    /** Перевіряє, що без зовнішнього файла зберігаються вбудовані значення за замовчуванням. */
    @Test
    void loadsBundledApplicationConfigurationWhenOptionalFileIsMissing() {
        AppConfig config = loadWithIsolatedSources();

        assertEquals(ApplicationMetadata.NAME, config.window().title());
        assertEquals(1280.0, config.window().defaultWidth());
        assertEquals(720.0, config.window().defaultHeight());
        assertEquals(500.0, config.simulation().defaultTickMillis());
    }

    /** Перевіряє пріоритет system properties над змінними середовища й зовнішнім properties-файлом. */
    @Test
    void appliesOverrideSourcesInDocumentedPriorityOrder() throws IOException {
        Files.writeString(
                new ApplicationStorageLayout(temporaryDirectory).configurationOverrideFile(),
                "window.defaultWidth=1400\ncamera.zoomFactor=1.25\n",
                StandardCharsets.UTF_8
        );
        Properties systemProperties = new Properties();
        systemProperties.setProperty("neuronmap.config.window.defaultWidth", "1600");
        Map<String, String> environment = Map.of(
                "NEURONMAP_CONFIG_WINDOW_DEFAULT_WIDTH", "1500",
                "NEURONMAP_CONFIG_WINDOW_DEFAULT_HEIGHT", "850"
        );

        AppConfig config = AppConfigLoader.load(
                new ApplicationStorageLayout(temporaryDirectory), systemProperties, environment);

        assertEquals(1600.0, config.window().defaultWidth());
        assertEquals(850.0, config.window().defaultHeight());
        assertEquals(1.25, config.camera().zoomFactor());
    }

    /** Перевіряє, що невідомий ключ не порушує запуск і не змінює відомі параметри. */
    @Test
    void ignoresUnknownOverrideKeys() throws IOException {
        Files.writeString(
                new ApplicationStorageLayout(temporaryDirectory).configurationOverrideFile(),
                "window.defaultWidth=1400\nnotARealSetting=true\n",
                StandardCharsets.UTF_8
        );

        AppConfig config = loadWithIsolatedSources();

        assertEquals(1400.0, config.window().defaultWidth());
    }

    /** Перевіряє відхилення шляху до файла вікна, який намагається вийти за межі каталогу зберігання. */
    @Test
    void rejectsUnsafeStateFileOverride() throws IOException {
        Files.writeString(
                new ApplicationStorageLayout(temporaryDirectory).configurationOverrideFile(),
                "window.stateFile=../outside.properties\n",
                StandardCharsets.UTF_8
        );

        assertThrows(IllegalStateException.class, this::loadWithIsolatedSources);
    }

    /** Перевіряє відхилення некоректного відомого значення замість непомітного повернення до default. */
    @Test
    void rejectsInvalidExternalOverride() throws IOException {
        Files.writeString(
                new ApplicationStorageLayout(temporaryDirectory).configurationOverrideFile(),
                "window.defaultWidth=0\n",
                StandardCharsets.UTF_8
        );

        assertThrows(IllegalStateException.class, this::loadWithIsolatedSources);
    }

    /** Перевіряє, що відомі числові параметри перевіряються моделлю навіть після успішного перетворення. */
    @Test
    void rejectsExternalOverrideOutsideValidatedRange() throws IOException {
        Files.writeString(
                new ApplicationStorageLayout(temporaryDirectory).configurationOverrideFile(),
                "simulation.minTickMillis=1000\nsimulation.defaultTickMillis=500\n",
                StandardCharsets.UTF_8
        );

        assertThrows(IllegalStateException.class, this::loadWithIsolatedSources);
    }

    /** Перевіряє, що ім'я файла стану з виходом за межі кореня сховища не проходить валідацію. */
    @Test
    void rejectsStateFilePathTraversal() {
        assertThrows(IllegalArgumentException.class,
                () -> new AppConfig.Window(ApplicationMetadata.NAME, 1280.0, 720.0, "../window.properties"));
        assertThrows(IllegalArgumentException.class,
                () -> new AppConfig.Window(ApplicationMetadata.NAME, 1280.0, 720.0, "/tmp/window.properties"));
    }

    /** Перевіряє відхилення конфігурації з некоректним діапазоном тривалості такту симуляції. */
    @Test
    void rejectsInvalidSimulationRange() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AppConfig.Simulation(10.0, 50.0, 500.0)
        );
    }

    /** Перевіряє, що синтаксично некоректний XML зберігає типізовану помилку парсера. */
    @Test
    void rejectsMalformedXml() {
        assertThrows(SAXException.class, () -> AppConfigLoader.parse(stream("<app><window></app>")));
    }

    /** Перевіряє, що відсутній обов'язковий розділ конфігурації чітко відхиляється. */
    @Test
    void rejectsMissingConfigurationSection() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AppConfigLoader.parse(stream(
                        "<app><window width=\"1280\" height=\"720\" stateFile=\"s\"/>"
                                + "<camera zoomFactor=\"1.2\"/></app>"
                ))
        );
    }

    /** Перевіряє, що відсутнє ім'я файла стану не замінюється прихованим значенням за замовчуванням. */
    @Test
    void rejectsMissingRequiredAttribute() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AppConfigLoader.parse(stream(
                        "<app><window width=\"1280\" height=\"720\"/>"
                                + "<camera zoomFactor=\"1.2\"/>"
                                + "<simulation defaultTickMs=\"500\" minTickMs=\"10\" maxTickMs=\"1000\"/>"
                                + "</app>"
                ))
        );
    }

    /** Перевіряє відхилення нечислового значення з атрибута числового параметра. */
    @Test
    void rejectsNonNumericAttribute() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AppConfigLoader.parse(stream(validConfiguration().replace("width=\"1280\"", "width=\"wide\"")))
        );
    }

    /** Перевіряє відхилення числового значення поза допустимим діапазоном моделі конфігурації. */
    @Test
    void rejectsDisallowedNumericValue() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AppConfigLoader.parse(stream(validConfiguration().replace("width=\"1280\"", "width=\"0\"")))
        );
    }

    /** Перевіряє, що налаштування XML-парсера забороняють оголошення DOCTYPE. */
    @Test
    void rejectsDocumentTypeDeclarations() {
        String xml = "<!DOCTYPE app [<!ENTITY external SYSTEM \"file:///etc/passwd\">]>"
                + validConfiguration();

        assertThrows(SAXException.class, () -> AppConfigLoader.parse(stream(xml)));
    }

    /** Завантажує конфігурацію з тимчасового кореня та порожнього середовища для ізоляції від машини розробника. */
    private AppConfig loadWithIsolatedSources() {
        return AppConfigLoader.load(new ApplicationStorageLayout(temporaryDirectory), new Properties(), Map.of());
    }

    /** Створює потік UTF-8, яким володіє тест, для перевірки контракту розбору. */
    private static ByteArrayInputStream stream(String xml) {
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }

    /** Повертає валідний мінімальний документ як основу для сценаріїв з одним пошкодженим значенням. */
    private static String validConfiguration() {
        return "<app><window width=\"1280\" height=\"720\" stateFile=\"s\"/>"
                + "<camera zoomFactor=\"1.2\"/>"
                + "<simulation defaultTickMs=\"500\" minTickMs=\"10\" maxTickMs=\"1000\"/>"
                + "</app>";
    }
}
