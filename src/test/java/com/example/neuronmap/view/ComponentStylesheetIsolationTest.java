package com.example.neuronmap.view;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє синтаксис CSS, імпорти та межі володіння стилями незалежних компонентів. */
final class ComponentStylesheetIsolationTest {

    private static final String RESOURCES_DIRECTORY = "src/main/resources";
    private static final String AGGREGATE_STYLESHEET = "app.css";
    private static final Set<String> WINDOW_CHROME_STYLESHEETS = Set.of(
            "window-chrome.css", "window-chrome-linux.css", "window-chrome-macos.css");
    private static final Set<String> SHARED_WINDOW_CHROME_CLASSES = Set.of(
            "window-title-bar", "window-title-bar-host", "window-title", "window-control-group",
            "window-control-button", "window-control-minimize", "window-control-maximize",
            "window-control-close", "window-icon-stroke", "window-icon-outline");
    private static final Map<String, String> GLOBAL_JAVAFX_STYLE_OWNER_RESOURCES = Map.of(
            "root", "editor-shell.css",
            "menu-button", "neuron.css");

    /** Переконується, що кожен CSS-файл ресурсів присутній у зібраному classpath. */
    @Test
    void everyStylesheetResourceIsPackaged() throws IOException {
        for (StylesheetResource stylesheet : findStylesheets().values()) {
            String classpathResource = "/" + stylesheet.resourcePath();
            assertNotNull(
                    getClass().getResource(classpathResource),
                    "CSS-ресурс відсутній у classpath: " + classpathResource);
        }
    }

    /** Перевіряє всі CSS-файли CSS-парсером і виявляє відсутні імпорти та цикли залежностей. */
    @Test
    void stylesheetsParseAndImportsResolveWithoutCycles() throws IOException {
        Map<String, StylesheetResource> stylesheets = findStylesheets();
        assertFalse(stylesheets.isEmpty(), "Каталог ресурсів не містить CSS-файлів");
        Map<String, List<String>> importGraph = new HashMap<>();

        for (StylesheetResource stylesheet : stylesheets.values()) {
            CssStylesheetParser.Stylesheet parsed = parseStylesheet(stylesheet);
            List<String> resolvedImports = new ArrayList<>();
            for (CssStylesheetParser.CssImport cssImport : parsed.imports()) {
                String importedResource = resolveImport(stylesheet, cssImport.resourceUri());
                assertTrue(
                        stylesheets.containsKey(importedResource),
                        "CSS " + stylesheet.resourcePath() + ": імпортований ресурс не знайдено: "
                                + importedResource);
                resolvedImports.add(importedResource);
            }
            importGraph.put(stylesheet.resourcePath(), resolvedImports);
        }

        Set<String> visited = new HashSet<>();
        Set<String> active = new HashSet<>();
        for (String resource : importGraph.keySet()) {
            assertNoImportCycle(resource, importGraph, visited, active);
        }
    }

    /** Не дозволяє двом таблицям визначати стиль того самого компонентного класу. */
    @Test
    void componentStyleOwnersDoNotOverlapAcrossStylesheets() throws IOException {
        Map<String, SelectorOwner> owners = new HashMap<>();
        for (StylesheetResource stylesheet : findStylesheets().values()) {
            if (AGGREGATE_STYLESHEET.equals(stylesheet.file().getFileName().toString())) {
                continue;
            }
            CssStylesheetParser.Stylesheet parsed = parseStylesheet(stylesheet);
            for (CssStylesheetParser.CssRule rule : parsed.rules()) {
                for (CssStylesheetParser.Selector selector : rule.selectors()) {
                    String componentClass = selector.componentOwner();
                    assertFalse(
                            componentClass.isBlank(),
                            () -> "CSS-селектор '" + selector.text() + "' у " + stylesheet.resourcePath()
                                    + " не має визначеного класу-власника");
                    String expectedGlobalOwner = GLOBAL_JAVAFX_STYLE_OWNER_RESOURCES.get(componentClass);
                    if (expectedGlobalOwner != null) {
                        assertEquals(
                                expectedGlobalOwner,
                                stylesheet.resourcePath(),
                                "Глобальне перевизначення JavaFX-класу '" + componentClass
                                        + "' має залишатися у " + expectedGlobalOwner);
                    }
                    SelectorOwner owner = new SelectorOwner(stylesheet.resourcePath(), selector.text());
                    SelectorOwner previousOwner = owners.putIfAbsent(componentClass, owner);
                    if (previousOwner != null
                            && !previousOwner.resourcePath().equals(owner.resourcePath())) {
                        assertTrue(
                                sharedPlatformOverrideAllowed(componentClass, previousOwner, owner),
                                () -> "CSS-клас-власник '" + componentClass + "' визначено в кількох файлах: "
                                        + previousOwner.resourcePath() + " (селектор '" + previousOwner.selector()
                                        + "') та " + owner.resourcePath() + " (селектор '" + owner.selector() + "')");
                    }
                }
            }
        }
    }

    /** Перевіряє, що кожен дозволений глобальний стиль JavaFX існує лише у своєму ресурсі-власнику. */
    @Test
    void designatedGlobalJavaFxOverridesRemainInTheirOwnerStylesheets() throws IOException {
        Map<String, StylesheetResource> stylesheets = findStylesheets();
        for (Map.Entry<String, String> entry : GLOBAL_JAVAFX_STYLE_OWNER_RESOURCES.entrySet()) {
            String className = entry.getKey();
            String resourcePath = entry.getValue();
            StylesheetResource stylesheet = stylesheets.get(resourcePath);
            assertNotNull(stylesheet, "Не знайдено CSS-власника глобального класу " + className);

            boolean hasBaseOverride = parseStylesheet(stylesheet).rules().stream()
                    .flatMap(rule -> rule.selectors().stream())
                    .anyMatch(selector -> selector.text().equals("." + className)
                            && selector.componentOwner().equals(className));
            assertTrue(
                    hasBaseOverride,
                    "Очікувалося явне глобальне перевизначення ." + className + " у " + resourcePath);
        }
    }

    /** Переконується, що app.css лишається агрегатором без власних правил і сторонніх at-rule. */
    @Test
    void aggregateStylesheetContainsOnlyImports() throws IOException {
        StylesheetResource aggregate = findStylesheets().get(AGGREGATE_STYLESHEET);
        assertNotNull(aggregate, "Не знайдено агрегатор стилів редактора");
        CssStylesheetParser.Stylesheet parsed = parseStylesheet(aggregate);

        assertFalse(parsed.hasNonImportRules(), "app.css не повинен містити власних CSS-правил або namespace");
        assertTrue(parsed.importCount() > 0, "app.css повинен підключати компонентні таблиці стилів");
    }

    /**
     * Забороняє точні дублікати селектора з тим самим списком декларацій в межах однієї таблиці.
     *
     * @throws IOException якщо файл стилів неможливо прочитати.
     */
    @Test
    void selectorsWithIdenticalDeclarationsAreNotRepeatedWithinAStylesheet() throws IOException {
        for (StylesheetResource stylesheet : findStylesheets().values()) {
            if (AGGREGATE_STYLESHEET.equals(stylesheet.file().getFileName().toString())) {
                continue;
            }
            Set<String> seenRules = new HashSet<>();
            for (CssStylesheetParser.CssRule rule : parseStylesheet(stylesheet).rules()) {
                String declarations = String.join(";", rule.declarations());
                for (CssStylesheetParser.Selector selector : rule.selectors()) {
                    String normalizedRule = rule.scope() + "|" + selector.text() + "{" + declarations + "}";
                    assertTrue(
                            seenRules.add(normalizedRule),
                            () -> "Повторюється CSS-правило '" + selector.text() + "' у "
                                    + stylesheet.resourcePath());
                }
            }
        }
    }

    /**
     * Читає файл і будує AST, додаючи шлях ресурсу до діагностики синтаксичних помилок.
     *
     * @param stylesheet опис CSS-ресурсу.
     * @return розібрана таблиця стилів.
     * @throws IOException якщо читання файла не вдалося.
     */
    private static CssStylesheetParser.Stylesheet parseStylesheet(StylesheetResource stylesheet)
            throws IOException {
        String css = Files.readString(stylesheet.file(), StandardCharsets.UTF_8);
        try {
            return CssStylesheetParser.parse(css);
        } catch (IllegalArgumentException exception) {
            throw new AssertionError("Некоректна CSS-таблиця " + stylesheet.resourcePath()
                    + ": " + exception.getMessage(), exception);
        }
    }

    /**
     * Знаходить CSS-файли рекурсивно, зберігаючи стабільні шляхи від каталогу ресурсів.
     *
     * @return CSS-ресурси, упорядковані за відносним шляхом.
     * @throws IOException якщо каталог ресурсів недоступний.
     */
    private static Map<String, StylesheetResource> findStylesheets() throws IOException {
        Path resources = Path.of(RESOURCES_DIRECTORY).toAbsolutePath().normalize();
        Map<String, StylesheetResource> stylesheets = new TreeMap<>();
        try (Stream<Path> entries = Files.walk(resources)) {
            entries.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".css"))
                    .forEach(path -> {
                        String relative = resources.relativize(path.toAbsolutePath().normalize())
                                .toString().replace(path.getFileSystem().getSeparator(), "/");
                        stylesheets.put(relative, new StylesheetResource(relative, path));
                    });
        }
        return stylesheets;
    }

    /**
     * Перевіряє безпечний шлях імпортованого ресурсу відносно каталогу ресурсів.
     *
     * @param importer таблиця стилів, що містить імпорт.
     * @param importedUri URI з директиви @import.
     * @return нормалізований шлях ресурсу з роздільниками '/'.
     */
    private static String resolveImport(StylesheetResource importer, String importedUri) {
        assertFalse(
                importedUri.matches("(?i)^[a-z][a-z0-9+.-]*:.*") || importedUri.startsWith("//"),
                "CSS " + importer.resourcePath() + ": зовнішні URL в @import не підтримуються: " + importedUri);
        assertFalse(
                importedUri.contains("?") || importedUri.contains("#"),
                "CSS " + importer.resourcePath() + ": query і fragment у шляху @import не підтримуються: "
                        + importedUri);
        try {
            Path resources = Path.of(RESOURCES_DIRECTORY).toAbsolutePath().normalize();
            Path importedPath = Path.of(importedUri);
            Path resolved = importedUri.startsWith("/")
                    ? resources.resolve(importedUri.substring(1)).normalize()
                    : importer.file().toAbsolutePath().normalize().getParent().resolve(importedPath).normalize();
            assertTrue(
                    resolved.startsWith(resources),
                    "CSS " + importer.resourcePath() + ": @import виходить за межі каталогу ресурсів: "
                            + importedUri);
            return resources.relativize(resolved).toString().replace(resolved.getFileSystem().getSeparator(), "/");
        } catch (InvalidPathException exception) {
            throw new AssertionError("Некоректний шлях @import у " + importer.resourcePath()
                    + ": " + importedUri, exception);
        }
    }

    /** Виявляє цикли в орієнтованому графі імпортів CSS. */
    private static void assertNoImportCycle(
            String resource,
            Map<String, List<String>> graph,
            Set<String> visited,
            Set<String> active) {
        if (visited.contains(resource)) {
            return;
        }
        assertTrue(active.add(resource), "Виявлено цикл імпорту CSS біля ресурсу " + resource);
        for (String dependency : graph.getOrDefault(resource, List.of())) {
            assertNoImportCycle(dependency, graph, visited, active);
        }
        active.remove(resource);
        visited.add(resource);
    }

    /** Дозволяє спільні класи лише в таблицях стилів заголовка вікна. */
    private static boolean sharedPlatformOverrideAllowed(
            String componentClass,
            SelectorOwner first,
            SelectorOwner second) {
        return SHARED_WINDOW_CHROME_CLASSES.contains(componentClass)
                && WINDOW_CHROME_STYLESHEETS.contains(Path.of(first.resourcePath()).getFileName().toString())
                && WINDOW_CHROME_STYLESHEETS.contains(Path.of(second.resourcePath()).getFileName().toString());
    }

    /**
     * Ресурс CSS на диску.
     *
     * @param resourcePath шлях відносно каталогу ресурсів.
     * @param file шлях до файла.
     */
    private record StylesheetResource(String resourcePath, Path file) {
    }

    /**
     * Перше оголошення CSS-власника в таблиці стилів.
     *
     * @param resourcePath шлях до таблиці стилів.
     * @param selector селектор, що оголосив власника.
     */
    private record SelectorOwner(String resourcePath, String selector) {
    }
}
