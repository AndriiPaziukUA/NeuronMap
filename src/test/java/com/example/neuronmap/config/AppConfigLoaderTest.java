package com.example.neuronmap.config;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Перевіряє читання конфігурації та відхилення некоректних параметрів.
 */
class AppConfigLoaderTest {

    /**
     * Перевіряє очікувану поведінку: конфігурація.
     */
    @Test
    void parsesBaseConfiguration() throws Exception {
        AppConfig config = AppConfigLoader.parse(
                new ByteArrayInputStream(
                        """
                        <app>
                            <window title="Test" width="1400" height="900" stateFile="state.properties"/>
                            <camera zoomFactor="1.25"/>
                            <simulation defaultTickMs="700" minTickMs="40" maxTickMs="3000"/>
                        </app>
                        """.getBytes(StandardCharsets.UTF_8)
                )
        );

        assertEquals("Test", config.window().title());
        assertEquals(1400.0, config.window().defaultWidth());
        assertEquals(900.0, config.window().defaultHeight());
        assertEquals("state.properties", config.window().stateFile());
        assertEquals(1.25, config.camera().zoomFactor());
        assertEquals(700.0, config.simulation().defaultTickMillis());
        assertEquals(40.0, config.simulation().minTickMillis());
        assertEquals(3000.0, config.simulation().maxTickMillis());
    }

    /**
     * Перевіряє очікувану поведінку: завантажує конфігурація.
     */
    @Test
    void loadsBundledApplicationConfiguration() {
        AppConfig config = AppConfigLoader.load();

        assertEquals("NeuronMap", config.window().title());
        assertEquals(1280.0, config.window().defaultWidth());
        assertEquals(720.0, config.window().defaultHeight());
        assertEquals(650.0, config.simulation().defaultTickMillis());
    }

    /**
     * Перевіряє очікувану поведінку: відхиляє некоректний.
     */
    @Test
    void rejectsInvalidSimulationRange() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AppConfig.Simulation(10.0, 50.0, 500.0)
        );
    }
}
