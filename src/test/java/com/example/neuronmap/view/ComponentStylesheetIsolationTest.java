package com.example.neuronmap.view;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє одноосібне володіння CSS-селекторами.
 * Ізоляція компонентів перевіряється за їхніми таблицями стилів.
 */
final class ComponentStylesheetIsolationTest {

    /**
     * Знаходить прості CSS-блоки без вкладених фігурних дужок.
     */
    private static final Pattern CSS_RULE_PATTERN = Pattern.compile("(?s)([^{}]+)\\{[^{}]*}");

    /**
     * Перевіряє пакування таблиць стилів разом із застосунком.
     *
     * @throws IOException якщо перелік ресурсів неможливо прочитати.
     */
    @Test
    void everyStylesheetResourceIsPackaged() throws IOException {
        for (Path stylesheet : findStylesheets()) {
            String resource = "/" + stylesheet.getFileName();
            assertNotNull(
                    getClass().getResource(resource),
                    "CSS-ресурс відсутній у classpath: " + resource);
        }
    }

    /**
     * Забороняє селектору мати кількох власників-таблиць стилів.
     *
     * @throws IOException якщо каталог або таблиця стилів недоступні.
     */
    @Test
    void componentSelectorsHaveSingleStylesheetOwner() throws IOException {
        Map<String, String> owners = new HashMap<>();
        List<Path> stylesheets = findStylesheets();

        assertTrue(
                stylesheets.size() > 1,
                "Очікується кілька незалежних таблиць стилів");
        for (Path stylesheet : stylesheets) {
            if ("app.css".equals(stylesheet.getFileName().toString())) {
                continue;
            }

            String css = stripComments(Files.readString(stylesheet, StandardCharsets.UTF_8));
            Matcher matcher = CSS_RULE_PATTERN.matcher(css);
            while (matcher.find()) {
                String selectorGroup = matcher.group(1).trim();
                if (selectorGroup.startsWith("@")) {
                    continue;
                }

                for (String selector : splitSelectorList(selectorGroup)) {
                    String normalized = normalizeSelector(selector);
                    if (normalized.isEmpty()) {
                        continue;
                    }
                    String previousOwner = owners.putIfAbsent(normalized, stylesheet.getFileName().toString());
                    assertTrue(
                            previousOwner == null || previousOwner.equals(stylesheet.getFileName().toString()),
                            () -> "CSS-селектор '" + normalized + "' належить одночасно "
                                    + previousOwner + " та " + stylesheet.getFileName());
                }
            }
        }
    }

    /**
     * Перевіряє, що app.css лише імпортує стилі компонентів.
     *
     * @throws IOException якщо агрегатор неможливо прочитати.
     */
    @Test
    void aggregateStylesheetContainsOnlyImports() throws IOException {
        Path aggregate = Path.of("src", "main", "resources", "app.css");
        assertTrue(Files.isRegularFile(aggregate), "Не знайдено агрегатор стилів editor");

        String content = stripComments(Files.readString(aggregate, StandardCharsets.UTF_8));
        String remaining = content.replaceAll("(?m)^\\s*@import\\s+[^;]+;\\s*$", "").trim();
        assertEquals(
                "",
                remaining,
                "app.css повинен містити лише імпорти "
                        + "компонентних таблиць стилів");
    }

    /**
     * Повертає CSS-файли ресурсів у відсортованому порядку.
     *
     * @return відсортований перелік компонентних таблиць стилів.
     * @throws IOException якщо каталог ресурсів недоступний.
     */
    private static List<Path> findStylesheets() throws IOException {
        Path resources = Path.of("src", "main", "resources");
        try (Stream<Path> entries = Files.list(resources)) {
            return entries
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".css"))
                    .sorted()
                    .toList();
        }
    }

    /**
     * Вилучає коментарі, які не беруть участі в аналізі селекторів.
     *
     * @param css повний текст таблиці стилів.
     * @return текст без коментарів.
     */
    private static String stripComments(String css) {
        return css.replaceAll("(?s)/\\*.*?\\*/", "");
    }

    /**
     * Розділяє групу селекторів за комами поза дужками.
     *
     * @param selectorGroup група селекторів із одного CSS-правила.
     * @return окремі селектори зі збереженням вкладених псевдокласів.
     */
    private static List<String> splitSelectorList(String selectorGroup) {
        List<String> selectors = new ArrayList<>();
        int parenthesisDepth = 0;
        int bracketDepth = 0;
        char quote = '\0';
        int start = 0;

        for (int index = 0; index < selectorGroup.length(); index++) {
            char current = selectorGroup.charAt(index);
            if (quote != '\0') {
                if (current == quote && (index == 0 || selectorGroup.charAt(index - 1) != '\\')) {
                    quote = '\0';
                }
                continue;
            }
            if (current == '\'' || current == '"') {
                quote = current;
            } else if (current == '(') {
                parenthesisDepth++;
            } else if (current == ')') {
                parenthesisDepth--;
            } else if (current == '[') {
                bracketDepth++;
            } else if (current == ']') {
                bracketDepth--;
            } else if (current == ',' && parenthesisDepth == 0 && bracketDepth == 0) {
                selectors.add(selectorGroup.substring(start, index));
                start = index + 1;
            }
        }
        selectors.add(selectorGroup.substring(start));
        return selectors;
    }

    /**
     * Нормалізує пробіли, зберігаючи рядкові значення селектора.
     *
     * @param selector CSS-селектор для зіставлення між файлами.
     * @return нормалізований селектор.
     */
    private static String normalizeSelector(String selector) {
        StringBuilder normalized = new StringBuilder();
        char quote = '\0';
        boolean pendingSpace = false;
        for (int index = 0; index < selector.length(); index++) {
            char current = selector.charAt(index);
            if (quote != '\0') {
                normalized.append(current);
                if (current == quote && (index == 0 || selector.charAt(index - 1) != '\\')) {
                    quote = '\0';
                }
            } else if (current == '\'' || current == '\"') {
                if (pendingSpace && normalized.length() > 0) {
                    normalized.append(' ');
                }
                pendingSpace = false;
                quote = current;
                normalized.append(current);
            } else if (Character.isWhitespace(current)) {
                pendingSpace = true;
            } else {
                if (pendingSpace && normalized.length() > 0) {
                    normalized.append(' ');
                }
                pendingSpace = false;
                normalized.append(current);
            }
        }
        return normalized.toString().trim();
    }
}
