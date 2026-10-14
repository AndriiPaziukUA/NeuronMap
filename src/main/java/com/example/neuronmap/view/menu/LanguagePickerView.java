package com.example.neuronmap.view.menu;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.i18n.SupportedLanguage;
import javafx.scene.control.Button;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Відображає поточну мову та розгортний список альтернатив без окремого вікна popup.
 * Список є частиною звичайного компонування сторінки, тому JavaFX не змінює його бік відносно кнопки.
 */
public final class LanguagePickerView extends VBox {

    private static final String COLLAPSED_MARKER = "▾";
    private static final String EXPANDED_MARKER = "▴";

    private final LocalizationService localization;
    private final Button selectedLanguageButton = new Button();
    private final VBox options = new VBox(4.0);
    private final Map<SupportedLanguage, Button> optionButtons = new EnumMap<>(SupportedLanguage.class);
    private final Consumer<Locale> localizationListener = ignored -> refreshTexts();
    private final Consumer<SupportedLanguage> selectionAction;
    private boolean optionsExpanded;

    /**
     * Створює вбудований список підтримуваних мов.
     *
     * @param localization служба для читання активної мови та локалізованих назв.
     * @param selectionAction дія, яка передає вибір мови сторінці налаштувань.
     */
    public LanguagePickerView(
            LocalizationService localization,
            Consumer<SupportedLanguage> selectionAction
    ) {
        this.localization = Objects.requireNonNull(localization, "localization");
        this.selectionAction = Objects.requireNonNull(selectionAction, "selectionAction");
        setSpacing(4.0);
        setFillWidth(true);
        setMinHeight(0.0);

        selectedLanguageButton.setId("language-picker-toggle");
        selectedLanguageButton.getStyleClass().add("main-menu-button");
        selectedLanguageButton.setMaxWidth(Double.MAX_VALUE);
        selectedLanguageButton.setMinHeight(38.0);
        selectedLanguageButton.setPrefHeight(38.0);
        selectedLanguageButton.setOnAction(event -> {
            setOptionsExpanded(!optionsExpanded);
            event.consume();
        });

        options.setFillWidth(true);
        options.setVisible(false);
        options.setManaged(false);
        options.getStyleClass().add("language-picker-options");
        for (SupportedLanguage language : SupportedLanguage.values()) {
            Button option = new Button();
            option.setId("language-option-" + language.name().toLowerCase(Locale.ROOT));
            option.getStyleClass().addAll("main-menu-button", "language-picker-option");
            option.setMaxWidth(Double.MAX_VALUE);
            option.setMinHeight(34.0);
            option.setPrefHeight(34.0);
            option.setOnAction(event -> {
                setOptionsExpanded(false);
                if (language != this.localization.language()) {
                    this.selectionAction.accept(language);
                }
                event.consume();
            });
            optionButtons.put(language, option);
        }
        VBox.setVgrow(options, Priority.NEVER);
        getChildren().setAll(selectedLanguageButton, options);
        localization.addListener(localizationListener);
        refreshTexts();
    }

    /** Оновлює назви мов і порядок варіантів відповідно до поточної локалі. */
    public void refreshTexts() {
        SupportedLanguage currentLanguage = localization.language();
        selectedLanguageButton.setText(localization.displayName(currentLanguage) + "  "
                + (optionsExpanded ? EXPANDED_MARKER : COLLAPSED_MARKER));
        selectedLanguageButton.setAccessibleText(localization.text("menu.language") + ": "
                + localization.displayName(currentLanguage));
        for (Map.Entry<SupportedLanguage, Button> entry : optionButtons.entrySet()) {
            entry.getValue().setText(localization.displayName(entry.getKey()));
            entry.getValue().setAccessibleText(localization.displayName(entry.getKey()));
        }
        options.getChildren().setAll(localization.supportedLanguagesInDisplayOrder().stream()
                .map(optionButtons::get)
                .toList());
    }

    /** Синхронізує напис поточної мови після застосування або скасування попереднього перегляду. */
    public void refreshSelection() {
        refreshTexts();
    }

    /** Закриває список доступних мов без зміни вибору. */
    public void closeOptions() {
        setOptionsExpanded(false);
    }

    /** Звільняє слухача зміни локалі, зареєстрованого компонентом. */
    public void dispose() {
        localization.removeListener(localizationListener);
    }

    /** Повертає кнопку розгортання для регресійних тестів. */
    Button toggleButtonForTest() {
        return selectedLanguageButton;
    }

    /** Повертає кнопку вибору заданої мови для регресійних тестів. */
    Button languageOptionForTest(SupportedLanguage language) {
        return optionButtons.get(language);
    }

    /** Повертає стан вбудованого списку для регресійних тестів. */
    boolean optionsExpandedForTest() {
        return optionsExpanded;
    }

    /** Відкриває або закриває список і синхронізує його участь у компонуванні. */
    private void setOptionsExpanded(boolean expanded) {
        optionsExpanded = expanded;
        options.setVisible(expanded);
        options.setManaged(expanded);
        refreshTexts();
        requestLayout();
    }
}
