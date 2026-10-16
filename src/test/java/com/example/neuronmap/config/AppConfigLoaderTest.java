package com.example.neuronmap.config;

import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Перевіряє читання XML-конфігурації, точність діагностики та безпечну відмову для некоректних даних. */
class AppConfigLoaderTest {

    /** Перевіряє розбір повної конфігурації з коректними значеннями. */
    @Test
    void parsesBaseConfiguration() throws ParserConfigurationException, SAXException, IOException {
        AppConfig config = AppConfigLoader.parse(stream(
                """
                <app>
                    <window title="Test" width="1400" height="900" stateFile="state.properties"/>
                    <camera zoomFactor="1.25"/>
                    <simulation defaultTickMs="700" minTickMs="40" maxTickMs="3000"/>
                </app>
                """
        ));

        assertEquals("Test", config.window().title());
        assertEquals(1400.0, config.window().defaultWidth());
        assertEquals(900.0, config.window().defaultHeight());
        assertEquals("state.properties", config.window().stateFile());
        assertEquals(1.25, config.camera().zoomFactor());
        assertEquals(700.0, config.simulation().defaultTickMillis());
        assertEquals(40.0, config.simulation().minTickMillis());
        assertEquals(3000.0, config.simulation().maxTickMillis());
    }

    /** Перевіряє завантаження конфігурації, що входить до ресурсів застосунку. */
    @Test
    void loadsBundledApplicationConfiguration() {
        AppConfig config = AppConfigLoader.load();

        assertEquals("NeuronMap", config.window().title());
        assertEquals(1280.0, config.window().defaultWidth());
        assertEquals(720.0, config.window().defaultHeight());
        assertEquals(500.0, config.simulation().defaultTickMillis());
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
                        "<app><window title=\"Test\" width=\"1280\" height=\"720\" stateFile=\"s\"/>"
                                + "<camera zoomFactor=\"1.2\"/></app>"
                ))
        );
    }

    /** Перевіряє, що відсутній обов'язковий атрибут вікна не замінюється значенням за замовчуванням. */
    @Test
    void rejectsMissingRequiredAttribute() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AppConfigLoader.parse(stream(
                        "<app><window width=\"1280\" height=\"720\" stateFile=\"s\"/>"
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

    /** Створює потік UTF-8, яким володіє тест, для перевірки контракту розбору. */
    private static ByteArrayInputStream stream(String xml) {
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }

    /** Повертає валідний мінімальний документ як основу для сценаріїв з одним пошкодженим значенням. */
    private static String validConfiguration() {
        return "<app><window title=\"Test\" width=\"1280\" height=\"720\" stateFile=\"s\"/>"
                + "<camera zoomFactor=\"1.2\"/>"
                + "<simulation defaultTickMs=\"500\" minTickMs=\"10\" maxTickMs=\"1000\"/>"
                + "</app>";
    }
}
