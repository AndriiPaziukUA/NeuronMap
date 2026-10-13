package com.example.neuronmap.view;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.i18n.SupportedLanguage;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/** Відображає вибір мови; зміну локалі передає контролеру через callback. */
public final class LanguageSettingsView extends VBox {

    private final LocalizationService localization;
    private final Consumer<Locale> localizationListener = ignored -> refreshTexts();
    private final Label languageLabel = new Label();
    private final ComboBox<SupportedLanguage> languageSelector = new ComboBox<>();
    private Consumer<SupportedLanguage> languageSelected = ignored -> { };
    private boolean updatingSelection;

    /**
     * Створює сторінку налаштувань мови без самостійної зміни активної локалі.
     *
     * @param localization служба отримання перекладів і підтримуваних мов.
     */
    public LanguageSettingsView(LocalizationService localization) {
        this.localization = Objects.requireNonNull(localization, "localization");
        setMinHeight(0.0);
        languageLabel.getStyleClass().add("main-menu-label");
        languageSelector.setMaxWidth(Double.MAX_VALUE);
        languageSelector.setPrefWidth(250.0);
        languageSelector.setCellFactory(list -> languageCell());
        languageSelector.setButtonCell(languageCell());
        languageSelector.setConverter(new StringConverter<>() {
            /** Повертає локалізовану назву вибраної мови. */
            @Override
            public String toString(SupportedLanguage language) {
                return language == null ? "" : localization.displayName(language);
            }

            /** Перетворення довільного тексту на мову не підтримується. */
            @Override
            public SupportedLanguage fromString(String value) {
                return null;
            }
        });
        languageSelector.setOnAction(event -> {
            SupportedLanguage selected = languageSelector.getValue();
            if (!updatingSelection && selected != null && selected != localization.language()) {
                languageSelector.hide();
                languageSelected.accept(selected);
            }
            event.consume();
        });
        HBox languageRow = new HBox(12.0, languageLabel, languageSelector);
        languageRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(languageSelector, Priority.ALWAYS);
        getChildren().setAll(languageRow);
        localization.addListener(localizationListener);
        refreshTexts();
    }

    /**
     * Встановлює дію контролера для попереднього перегляду вибраної мови.
     *
     * @param action callback, який обробляє вибір мови поза межами подання.
     */
    public void setLanguageSelectedAction(Consumer<SupportedLanguage> action) {
        languageSelected = Objects.requireNonNull(action, "action");
    }

    /** Оновлює підпис, доступні варіанти та вибране значення після зміни локалі. */
    public void refreshTexts() {
        languageLabel.setText(localization.text("menu.language"));
        updatingSelection = true;
        try {
            languageSelector.getItems().setAll(localization.supportedLanguagesInDisplayOrder());
            languageSelector.setValue(localization.language());
        } finally {
            updatingSelection = false;
        }
    }

    /**
     * Надає компонент вибору мови тестам сценаріїв налаштування.
     *
     * @return комбінований список підтримуваних мов.
     */
    public ComboBox<SupportedLanguage> languageSelectorForTest() {
        return languageSelector;
    }

    /** Прибирає обробник вибору перед остаточним звільненням сторінки. */
    public void dispose() {
        languageSelector.setOnAction(null);
        languageSelected = ignored -> { };
        localization.removeListener(localizationListener);
    }

    /** Створює комірку списку, яка показує локалізовану назву мови. */
    private ListCell<SupportedLanguage> languageCell() {
        return new ListCell<>() {
            /** Оновлює текст комірки для поточного елемента списку. */
            @Override
            protected void updateItem(SupportedLanguage item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : localization.displayName(item));
            }
        };
    }
}
