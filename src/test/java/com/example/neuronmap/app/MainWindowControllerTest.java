package com.example.neuronmap.app;

import com.example.neuronmap.config.AppConfig;
import javafx.scene.Scene;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Перевіряє архітектурний контракт контролера головного вікна та його незалежність від фасаду редактора. */
final class MainWindowControllerTest {

    /**
     * Перевіряє, що контролер отримує перевірену конфігурацію вікна як єдиний параметр налаштувань.
     */
    @Test
    void configuresWindowFromValidatedWindowSettings() {
        Method configure = Arrays.stream(MainWindowController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("configure"))
                .findFirst()
                .orElse(null);

        assertNotNull(configure);
        assertArrayEquals(
                new Class<?>[]{AppConfig.Window.class, Scene.class, Runnable.class},
                configure.getParameterTypes()
        );
    }

    /**
     * Перевіряє, що контролер не залежить безпосередньо від фасаду редактора й приймає лише дію його завершення.
     */
    @Test
    void dependsOnShutdownActionInsteadOfEditorFacade() {
        assertFalse(Arrays.stream(MainWindowController.class.getDeclaredFields())
                .map(Field::getType)
                .anyMatch(type -> type == NeuronMapEditor.class));
    }
}
