package com.example.neuronmap.view;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє наявність, ізоляцію та цілісність окремих таблиць стилів компонентів. */
final class IndependentStylesheetArchitectureTest {

    /**
     * Переконується, що таблиці стилів редактора, меню й заголовка присутні у classpath.
     */
    @Test
    void componentStylesheetsArePackaged() {
        List<String> resources = List.of(
                "/app.css",
                "/editor-shell.css",
                "/toolbar.css",
                "/neuron.css",
                "/connection.css",
                "/connection-preview.css",
                "/editor-overlays.css",
                "/group.css",
                "/pulse-animation.css",
                "/menu-enhancements.css",
                "/main-menu-settings-save.css",
                "/project-list.css",
                "/unsaved-changes.css",
                "/window-chrome.css",
                "/window-chrome-linux.css",
                "/window-chrome-macos.css");

        resources.forEach(path -> assertNotNull(
                IndependentStylesheetArchitectureTest.class.getResource(path),
                "Не знайдено CSS-ресурс " + path));
    }

    /**
     * Переконується, що app.css лише підключає модулі й не конкурує з власниками стилів меню.
     *
     * @throws IOException якщо будь-яку потрібну таблицю стилів неможливо прочитати.
     */
    @Test
    void appStylesheetDelegatesComponentRulesToDedicatedFiles() throws IOException {
        String appCss = readResource("/app.css");
        String toolbarCss = readResource("/toolbar.css");
        String neuronCss = readResource("/neuron.css");
        String connectionCss = readResource("/connection.css");
        String connectionPreviewCss = readResource("/connection-preview.css");
        String menuCss = readResource("/menu-enhancements.css");
        String projectListCss = readResource("/project-list.css");

        assertTrue(appCss.contains("@import \"editor-shell.css\";"));
        assertTrue(appCss.contains("@import \"toolbar.css\";"));
        assertTrue(appCss.contains("@import \"neuron.css\";"));
        assertTrue(appCss.contains("@import \"connection-preview.css\";"));
        assertFalse(appCss.contains(".main-menu-scroll"));
        assertFalse(appCss.contains(".saved-project-row"));
        assertFalse(appCss.contains(".window-control-minimize"));
        assertFalse(menuCss.contains(".main-menu-scroll"));
        assertFalse(menuCss.contains(".saved-project-row"));
        assertTrue(projectListCss.contains(".main-menu-scroll > .viewport"));
        assertTrue(projectListCss.contains(".project-list-empty-hint"));
        assertFalse(projectListCss.contains(".main-menu-hint"));
        assertTrue(menuCss.contains(".main-menu-overlay .main-menu-backdrop"));
        assertFalse(menuCss.contains(".neuron-card"));
        assertFalse(menuCss.contains(".connection-preview-line"));
        assertTrue(toolbarCss.contains(".toolbar-speed-field"));
        assertFalse(toolbarCss.contains(".neuron-card"));
        assertFalse(toolbarCss.contains(".saved-project-row"));
        assertTrue(neuronCss.contains(".neuron-card"));
        assertFalse(neuronCss.contains(".toolbar-speed-field"));
        assertFalse(neuronCss.contains(".main-menu-button"));
        assertTrue(connectionCss.contains(".connection-menu"));
        assertFalse(connectionCss.contains(".neuron-card"));
        assertTrue(connectionPreviewCss.contains(".connection-preview-line"));
        assertFalse(connectionPreviewCss.contains(".toolbar-speed-field"));
    }

    /**
     * Виявляє дублікати правил на основі розібраного CSS AST, не плутаючи коми у функціях із роздільниками.
     *
     * @throws IOException якщо одна з таблиць стилів недоступна.
     */
    @Test
    void componentStylesheetsContainNoDuplicateRuleBlocksAndKeepInteractiveStates() throws IOException {
        List<String> resources = List.of(
                "/editor-shell.css",
                "/toolbar.css",
                "/neuron.css",
                "/connection.css",
                "/connection-preview.css",
                "/editor-overlays.css",
                "/group.css",
                "/menu-enhancements.css",
                "/main-menu-settings-save.css",
                "/project-list.css",
                "/unsaved-changes.css",
                "/window-chrome.css",
                "/window-chrome-linux.css",
                "/window-chrome-macos.css");
        for (String resource : resources) {
            assertNoDuplicateRuleBlocks(resource, readResource(resource));
        }

        String toolbarCss = readResource("/toolbar.css");
        String menuCss = readResource("/menu-enhancements.css");
        String projectListCss = readResource("/project-list.css");
        assertTrue(toolbarCss.contains(".toolbar-speed-field:hover"));
        assertTrue(toolbarCss.contains(".toolbar-speed-field:focused"));
        assertTrue(toolbarCss.contains(".tool-button:hover"));
        assertTrue(toolbarCss.contains(".simulation-icon-button-danger:hover"));
        assertTrue(menuCss.contains(".main-menu-button:hover"));
        assertTrue(menuCss.contains(".main-menu-button:focused"));
        assertTrue(menuCss.contains(".main-menu-button:focused:hover"));
        assertTrue(projectListCss.contains(".saved-project-row:hover"));
        assertTrue(projectListCss.contains(".saved-project-row.selected:hover"));
        assertTrue(projectListCss.contains(".saved-project-delete-button:hover"));
    }

    /**
     * Перевіряє відсутність повторного селектора з тим самим набором декларацій.
     *
     * @param resourcePath шлях ресурсу для повідомлення про помилку.
     * @param css текст таблиці стилів.
     */
    private static void assertNoDuplicateRuleBlocks(String resourcePath, String css) {
        Set<String> seenBlocks = new HashSet<>();
        for (CssStylesheetParser.CssRule rule : CssStylesheetParser.parse(css).rules()) {
            String declarations = String.join(";", rule.declarations());
            for (CssStylesheetParser.Selector selector : rule.selectors()) {
                String normalizedRule = rule.scope() + "|" + selector.text() + "{" + declarations + "}";
                assertTrue(
                        seenBlocks.add(normalizedRule),
                        () -> "Повторюється CSS-правило " + selector.text() + " у " + resourcePath);
            }
        }
    }

    /**
     * Читає повний текст ресурсу в UTF-8 та закриває потік незалежно від результату читання.
     *
     * @param path абсолютний шлях ресурсу у classpath.
     * @return вміст таблиці стилів.
     * @throws IOException якщо читання ресурсу не вдалося.
     */
    private String readResource(String path) throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(path)) {
            assertNotNull(stream, "Не знайдено ресурс " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
