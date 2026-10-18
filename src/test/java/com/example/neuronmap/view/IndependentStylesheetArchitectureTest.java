package com.example.neuronmap.view;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє пакування модульних таблиць стилів і відсутність дубльованих правил меню в app.css.
 */
final class IndependentStylesheetArchitectureTest {

    /**
     * Переконується, що всі стилі незалежних редакторських компонентів присутні в classpath.
     */
    @Test
    void componentStylesheetsArePackaged() {
        List<String> resources = List.of(
                "/editor-shell.css",
                "/toolbar.css",
                "/neuron.css",
                "/connection.css",
                "/connection-preview.css",
                "/editor-overlays.css",
                "/group.css",
                "/pulse-animation.css",
                "/menu-enhancements.css",
                "/project-list.css",
                "/unsaved-changes.css"
        );

        resources.forEach(path -> assertNotNull(
                IndependentStylesheetArchitectureTest.class.getResource(path),
                "Не знайдено CSS-ресурс " + path));
    }

    /**
     * Переконується, що app.css лише підключає модулі й не конкурує з власником стилів меню.
     */
    @Test
    void appStylesheetDelegatesComponentRulesToDedicatedFiles() throws IOException {
        String appCss = readResource("/app.css");
        String menuCss = readResource("/menu-enhancements.css");
        String projectListCss = readResource("/project-list.css");

        assertTrue(appCss.contains("@import \"neuron.css\";"));
        assertTrue(appCss.contains("@import \"connection-preview.css\";"));
        assertFalse(appCss.contains(".main-menu-scroll"));
        assertFalse(appCss.contains(".saved-project-row"));
        assertFalse(menuCss.contains(".main-menu-scroll"));
        assertFalse(menuCss.contains(".saved-project-row"));
        assertTrue(projectListCss.contains(".main-menu-scroll > .viewport"));
        assertTrue(projectListCss.contains(".project-list-empty-hint"));
        assertFalse(projectListCss.contains(".main-menu-hint"));
        assertTrue(menuCss.contains(".main-menu-overlay .main-menu-backdrop"));
        assertFalse(menuCss.contains(".neuron-card"));
        assertFalse(menuCss.contains(".connection-preview-line"));
    }

    /**
     * Читає повний текст ресурсу в UTF-8 та закриває потік незалежно від результату читання.
     *
     * @param path абсолютний шлях у classpath.
     * @return текст ресурсу.
     * @throws IOException якщо читання не вдалося.
     */
    private String readResource(String path) throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(path)) {
            assertNotNull(stream, "Не знайдено ресурс " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
