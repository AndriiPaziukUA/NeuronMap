package com.example.neuronmap.view.menu;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.view.support.SwitchToggleButton;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Відображає перемикач повноекранного режиму та передає тимчасову зміну власнику налаштувань. */
public final class FullScreenSettingsView extends HBox {

    private final LocalizationService localization;
    private final Label label = new Label();
    private final SwitchToggleButton toggle = new SwitchToggleButton(
            "fullscreen-switch-graphic", "fullscreen-switch-track", "fullscreen-switch-thumb");
    private final Consumer<Locale> localizationListener = ignored -> refreshTexts();

    private Supplier<Boolean> currentValue = () -> false;
    private Consumer<Boolean> selectedAction = ignored -> { };
    private boolean configured;

    /**
     * Створює стилізований перемикач, не прив'язуючи подання до сховища або об'єкта Stage.
     *
     * @param localization спільна служба локалізації застосунку.
     * @throws NullPointerException якщо службу локалізації не задано.
     */
    public FullScreenSettingsView(LocalizationService localization) {
        this.localization = Objects.requireNonNull(localization, "localization");
        getStyleClass().add("main-menu-fullscreen-settings");
        label.getStyleClass().add("main-menu-label");

        toggle.getStyleClass().add("main-menu-fullscreen-toggle");
        toggle.setAccessibleHelp(localization.textFrom("i18n.fullScreen", "description"));
        toggle.setDisable(true);
        toggle.setOnAction(event -> {
            if (configured) {
                selectedAction.accept(toggle.isSelected());
            }
            event.consume();
        });

        MainMenuSettingRow.configure(this, label, toggle);
        localization.addListener(localizationListener);
        refreshTexts();
    }

    /**
     * Підключає джерело поточного стану та callback попереднього перегляду.
     * Постійне збереження виконується спільною сесією налаштувань, а не цим поданням.
     *
     * @param currentValue постачальник фактично активного стану режиму.
     * @param selectedAction дія застосування тимчасового значення.
     * @throws NullPointerException якщо один із callback-ів не задано.
     */
    public void setActions(Supplier<Boolean> currentValue, Consumer<Boolean> selectedAction) {
        this.currentValue = Objects.requireNonNull(currentValue, "currentValue");
        this.selectedAction = Objects.requireNonNull(selectedAction, "selectedAction");
        configured = true;
        toggle.setDisable(false);
        refreshValue();
    }

    /** Оновлює вибір перемикача із джерела істинного стану без виклику callback-а. */
    public void refreshValue() {
        toggle.setSelected(Boolean.TRUE.equals(currentValue.get()));
    }

    /** Оновлює локалізований підпис і доступну допомогу після зміни мови інтерфейсу. */
    public void refreshTexts() {
        String text = localization.textFrom("i18n.fullScreen", "label");
        label.setText(text);
        toggle.setAccessibleText(text);
        toggle.setAccessibleHelp(localization.textFrom("i18n.fullScreen", "description"));
    }

    /** Від'єднує слухача локалізації та очищує callback-и під час звільнення сторінки налаштувань. */
    public void dispose() {
        toggle.dispose();
        selectedAction = ignored -> { };
        currentValue = () -> false;
        configured = false;
        localization.removeListener(localizationListener);
    }

    /** Надає сам перемикач тестам подання без оприлюднення його як частини публічного API. */
    ToggleButton toggleForTest() {
        return toggle;
    }
}
