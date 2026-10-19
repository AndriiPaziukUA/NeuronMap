package com.example.neuronmap.view;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє CSS AST, сформований бібліотекою парсингу, та власність класів компонентів. */
final class CssStylesheetParserTest {

    /** Перевіряє розділення списку селекторів і збереження декларацій зі складними функціями. */
    @Test
    void parsesSelectorListsAndFunctionValues() {
        CssStylesheetParser.Stylesheet stylesheet = CssStylesheetParser.parse("""
                .first,
                .second:not(.hidden, .disabled), [data-label="a,b"] {
                    -fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.5), 10, 0.2, 0, 2);
                    -fx-padding: 1 2 3 4;
                }
                """);

        assertEquals(1, stylesheet.rules().size());
        assertEquals(3, stylesheet.rules().getFirst().selectors().size());
        assertEquals(2, stylesheet.rules().getFirst().declarations().size());
        assertTrue(stylesheet.rules().getFirst().selectors().get(1).text().contains(":not("));
        assertEquals("first", stylesheet.rules().getFirst().selectors().getFirst().componentOwner());
    }

    /** Перевіряє, що парсер враховує стилеві правила у вкладеному at-rule. */
    @Test
    void collectsRulesInsideNestedAtRules() {
        CssStylesheetParser.Stylesheet stylesheet = CssStylesheetParser.parse("""
                @media screen {
                    .component {
                        -fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.5), 10, 0.2, 0, 2);
                    }
                }
                """);

        assertEquals(1, stylesheet.rules().size());
        assertEquals("component", stylesheet.rules().getFirst().selectors().getFirst().componentOwner());
        assertTrue(stylesheet.rules().getFirst().scope().contains("@media screen"));
    }

    /** Не змішує однакові селектори, оголошені для різних контекстів @media. */
    @Test
    void distinguishesRulesInDifferentMediaContexts() {
        CssStylesheetParser.Stylesheet stylesheet = CssStylesheetParser.parse("""
                @media screen { .component { -fx-padding: 1px; } }
                @media print { .component { -fx-padding: 1px; } }
                """);

        assertEquals(2, stylesheet.rules().size());
        assertNotEquals(stylesheet.rules().get(0).scope(), stylesheet.rules().get(1).scope());
    }

    /** Переконується, що позначення коментаря в лапках лишається частиною значення властивості. */
    @Test
    void preservesCommentMarkersInsideQuotedValues() {
        CssStylesheetParser.Stylesheet stylesheet = CssStylesheetParser.parse(
                ".component { -fx-text-fill: \"/* залишити */\"; /* прибрати */ -fx-font-size: 12px; }");

        assertEquals(2, stylesheet.rules().getFirst().declarations().size());
        assertTrue(stylesheet.rules().getFirst().declarations().getFirst().contains("/* залишити */"));
        assertFalse(stylesheet.rules().getFirst().declarations().getFirst().contains("/* прибрати */"));
    }

    /** Визначає власника за компонентним класом, а не за стандартними класами JavaFX або станами. */
    @Test
    void detectsComponentOwnerFromCssAst() {
        assertEquals(
                "main-menu-scroll",
                parseSingleSelectorOwner(".main-menu-scroll .scroll-bar:vertical .thumb:hover"));
        assertEquals("saved-project-row", parseSingleSelectorOwner(".saved-project-row.selected:hover"));
        assertEquals("root", parseSingleSelectorOwner(".root"));
        assertEquals("menu-button", parseSingleSelectorOwner(".menu-button"));
        assertEquals("menu-button", parseSingleSelectorOwner(".menu-button:hover"));
        assertEquals(
                "main-menu-overlay",
                parseSingleSelectorOwner(".main-menu-overlay .menu-button"));
        assertEquals(
                "window-icon-stroke",
                parseSingleSelectorOwner(
                        ".window-chrome-macos .window-control-button:hover .window-icon-stroke"));
        assertEquals("", parseSingleSelectorOwner(".scroll-bar:vertical .thumb:hover"));
    }

    /** Розпізнає локальний шлях, записаний у звичайній директиві @import. */
    @Test
    void readsImportsFromStylesheetAst() {
        CssStylesheetParser.Stylesheet stylesheet = CssStylesheetParser.parse(
                "@import \"components/toolbar.css\";\n@import url(\"theme.css\");");

        assertEquals(2, stylesheet.imports().size());
        assertEquals("components/toolbar.css", stylesheet.imports().getFirst().resourceUri());
        assertEquals("theme.css", stylesheet.imports().get(1).resourceUri());
        assertFalse(stylesheet.hasNonImportRules());
    }

    /** Відхиляє синтаксично неповний CSS замість мовчки пропускати пошкоджений блок. */
    @Test
    void rejectsMalformedCss() {
        assertThrows(IllegalArgumentException.class,
                () -> CssStylesheetParser.parse(".component { -fx-padding 2px; }"));
        assertThrows(IllegalArgumentException.class,
                () -> CssStylesheetParser.parse(".component { -fx-padding: 2px;"));
    }

    /** Переконується, що список селекторів з порожнім елементом відхиляється парсером. */
    @Test
    void rejectsEmptySelectorInSelectorList() {
        assertThrows(IllegalArgumentException.class,
                () -> CssStylesheetParser.parse(".first, { -fx-padding: 1px; }"));
    }

    /** Розбирає одиночний селектор і повертає визначений для нього компонентний клас. */
    private static String parseSingleSelectorOwner(String selector) {
        List<CssStylesheetParser.Selector> selectors = CssStylesheetParser.parse(
                selector + " { -fx-padding: 1px; }").rules().getFirst().selectors();
        return selectors.getFirst().componentOwner();
    }
}
