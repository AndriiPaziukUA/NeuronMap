package com.example.neuronmap.view;

import com.helger.css.ICSSWriteable;
import com.helger.css.decl.AbstractHasTopLevelRules;
import com.helger.css.decl.CSSDeclaration;
import com.helger.css.decl.CSSImportRule;
import com.helger.css.decl.CSSSelector;
import com.helger.css.decl.CSSSelectorSimpleMember;
import com.helger.css.decl.CSSStyleRule;
import com.helger.css.decl.CascadingStyleSheet;
import com.helger.css.decl.ICSSSelectorMember;
import com.helger.css.decl.ICSSTopLevelRule;
import com.helger.css.reader.CSSReader;
import com.helger.css.reader.CSSReaderSettings;
import com.helger.css.reader.errorhandler.ThrowingCSSParseErrorHandler;
import com.helger.css.writer.CSSWriterSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Надає архітектурним тестам типізований доступ до CSS AST, побудованого повноцінним CSS-парсером.
 * Статичні перевірки власності компонентів доповнюють синтаксичний розбір, але не імітують каскад JavaFX.
 */
final class CssStylesheetParser {

    /** Стандартні style classes JavaFX, які не можуть самостійно визначати власника компонента. */
    private static final Set<String> JAVAFX_STYLE_CLASSES = Set.of(
            "accordion", "arrow", "button", "cell", "check-box", "choice-box",
            "color-picker", "column-header", "column-header-background", "combo-box",
            "combo-box-popup", "context-menu", "corner", "date-picker", "decrement-arrow",
            "decrement-button", "filler", "hyperlink", "increment-arrow", "increment-button",
            "label", "list-cell", "list-view", "menu-button", "menu-item", "pagination",
            "progress-bar", "progress-indicator", "radio-button", "scroll-bar", "scroll-pane",
            "separator", "slider", "spinner", "split-menu-button", "split-pane", "tab",
            "tab-pane", "table-cell", "table-column", "table-row-cell", "table-view", "text",
            "text-area", "text-field", "text-input-control", "titled-pane", "toggle-button",
            "tool-bar", "tree-cell", "tree-table-cell", "tree-table-row-cell", "tree-table-view",
            "tree-view", "track", "thumb", "viewport", "virtual-flow", "root");

    /**
     * Винятки для глобальних перевизначень класів JavaFX. Такі селектори мають явного власника,
     * якщо не містять власнішого прикладного класу компонента.
     */
    private static final Set<String> GLOBAL_JAVAFX_STYLE_OWNERS = Set.of("root", "menu-button");

    /** Класи стану JavaFX, що уточнюють стиль, але не є окремими власниками компонента. */
    private static final Set<String> STATE_CLASS_MARKERS = Set.of(
            "active", "armed", "checked", "collapsed", "default", "disabled", "deselected",
            "determinate", "editable", "empty", "expanded", "filled", "focused", "hover",
            "indeterminate", "pressed", "selected", "unchecked", "unselected", "visited");

    private CssStylesheetParser() {
        // Допоміжний клас без екземплярів.
    }

    /**
     * Розбирає CSS з відхиленням помилок парсингу, що інакше могли б бути пропущені як відновлювані.
     *
     * @param source повний текст таблиці стилів.
     * @return AST таблиці стилів з імпортами та правилами.
     * @throws IllegalArgumentException якщо CSS має синтаксичну помилку або не вдалося створити AST.
     */
    static Stylesheet parse(String source) {
        if (source == null) {
            throw new IllegalArgumentException("Текст CSS не може бути null");
        }
        CascadingStyleSheet parsed;
        StringBuilder parseDiagnostic = new StringBuilder();
        try {
            CSSReaderSettings settings = new CSSReaderSettings()
                    .setCustomErrorHandler(new ThrowingCSSParseErrorHandler())
                    .setCustomExceptionHandler(exception -> parseDiagnostic.append(exception.getMessage()));
            parsed = CSSReader.readFromStringReader(source, settings);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Парсер відхилив CSS: " + exception.getMessage(), exception);
        }
        if (parsed == null) {
            String details = parseDiagnostic.isEmpty() ? "" : ": " + parseDiagnostic;
            throw new IllegalArgumentException("Парсер не зміг побудувати CSS AST" + details);
        }
        return new Stylesheet(parsed);
    }

    /**
     * Повертає назви CSS-класів, що є кандидатами на власника оформлення для селектора.
     *
     * @param selector AST одного CSS-селектора.
     * @return власник за останнім специфічним класом, дозволений глобальний клас JavaFX
     *         або порожній рядок, якщо власника не визначено.
     */
    private static String componentOwner(CSSSelector selector) {
        String owner = "";
        String globalFrameworkOwner = "";
        for (ICSSSelectorMember member : selector.getAllMembers()) {
            if (!(member instanceof CSSSelectorSimpleMember simpleMember) || !simpleMember.isClass()) {
                continue;
            }
            String className = simpleMember.getValue().substring(1);
            if (GLOBAL_JAVAFX_STYLE_OWNERS.contains(className)) {
                if (owner.isEmpty()) {
                    globalFrameworkOwner = className;
                }
            } else if (!JAVAFX_STYLE_CLASSES.contains(className) && !STATE_CLASS_MARKERS.contains(className)) {
                owner = className;
            }
        }
        return owner.isEmpty() ? globalFrameworkOwner : owner;
    }

    /**
     * Збирає стилеві правила також із вкладених @media, @supports та вкладених правил селекторів.
     *
     * @param sourceRules правила поточного CSS-контейнера.
     * @param target список для накопичення знайдених правил.
     */
    private static void collectStyleRules(
            Iterable<?> sourceRules,
            List<CssRule> target,
            CSSWriterSettings writerSettings,
            String scope) {
        for (Object sourceRule : sourceRules) {
            if (sourceRule instanceof CSSStyleRule styleRule) {
                List<Selector> selectors = styleRule.getAllSelectors().stream()
                        .map(selector -> new Selector(
                                selector.getAsCSSString(writerSettings, 0), componentOwner(selector)))
                        .toList();
                List<String> declarations = styleRule.getAllDeclarations().stream()
                        .map(declaration -> declaration.getAsCSSString(writerSettings, 0))
                        .toList();
                target.add(new CssRule(selectors, declarations, scope));
                if (styleRule.hasRules()) {
                    String nestedScope = scope + "/selector["
                            + selectors.stream().map(Selector::text).reduce((left, right) -> left + "," + right)
                                    .orElse("")
                            + "]";
                    collectStyleRules(styleRule.getAllRules(), target, writerSettings, nestedScope);
                }
            } else if (sourceRule instanceof AbstractHasTopLevelRules nestedRuleContainer) {
                String nestedScope = scope + "/" + ruleScope(sourceRule, writerSettings);
                collectStyleRules(nestedRuleContainer.getAllRules(), target, writerSettings, nestedScope);
            }
        }
    }

    /**
     * Повертає заголовок контейнерного at-rule без тіла, щоб не змішувати різні CSS-контексти.
     *
     * @param rule контейнерне правило.
     * @param writerSettings налаштування канонічної серіалізації.
     * @return стабільний текст заголовка правила або назва типу, якщо серіалізація недоступна.
     */
    private static String ruleScope(Object rule, CSSWriterSettings writerSettings) {
        if (rule instanceof ICSSWriteable writeable) {
            String serialized = writeable.getAsCSSString(writerSettings, 0);
            int bodyStart = serialized.indexOf('{');
            return bodyStart < 0 ? serialized.trim() : serialized.substring(0, bodyStart).trim();
        }
        return rule.getClass().getSimpleName();
    }

    /**
     * Подання розібраної таблиці стилів.
     *
     * @param stylesheet CSS AST, створений ph-css.
     */
    record Stylesheet(CascadingStyleSheet stylesheet) {

        /**
         * Повертає посилання на локальні чи зовнішні ресурси з директив @import.
         *
         * @return імпорти в порядку їхнього оголошення.
         */
        List<CssImport> imports() {
            List<CssImport> result = new ArrayList<>();
            for (CSSImportRule rule : stylesheet.getAllImportRules()) {
                result.add(new CssImport(rule.getLocationString()));
            }
            return List.copyOf(result);
        }

        /**
         * Повертає всі стилеві правила, включно з правилами у вкладених контейнерах.
         *
         * @return правила у порядку обходу AST.
         */
        List<CssRule> rules() {
            List<CssRule> result = new ArrayList<>();
            collectStyleRules(stylesheet.getAllRules(), result, new CSSWriterSettings(true), "top-level");
            return List.copyOf(result);
        }

        /**
         * Перевіряє, чи містить таблиця що-небудь крім імпортів: стилеві/at-rule або namespace.
         *
         * @return true, якщо присутні сторонні для агрегатора CSS-конструкції.
         */
        boolean hasNonImportRules() {
            return stylesheet.hasRules() || stylesheet.hasNamespaceRules();
        }

        /**
         * Повертає кількість локально оброблених директив @import.
         *
         * @return кількість директив імпорту.
         */
        int importCount() {
            return stylesheet.getImportRuleCount();
        }
    }

    /**
     * Один селектор зі стабільним CSS-представленням та визначеним класом-власником.
     *
     * @param text серіалізований CSS-селектор.
     * @param componentOwner назва класу-власника або порожній рядок.
     */
    record Selector(String text, String componentOwner) {
    }

    /**
     * Стилеве правило із селекторами та нормалізованими деклараціями.
     *
     * @param selectors селектори правила.
     * @param declarations декларації у їхньому вихідному порядку.
     * @param scope шлях контейнерних правил, що визначає контекст каскаду.
     */
    record CssRule(List<Selector> selectors, List<String> declarations, String scope) {
    }

    /**
     * Адреса ресурсу, указаного в директиві @import.
     *
     * @param resourceUri адреса ресурсу без розбору файлової системи.
     */
    record CssImport(String resourceUri) {
    }
}
