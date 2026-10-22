package com.example.neuronmap.view.menu;

import com.example.neuronmap.i18n.LocalizationService;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Відображає перемикач повноекранного режиму та передає тимчасову зміну власнику налаштувань. */
public final class FullScreenSettingsView extends HBox {

    private final LocalizationService localization;
    private final Label label = new Label();
    private final ToggleButton toggle = new ToggleButton();
    private final Circle thumb = new Circle(8.0);
    private final ChangeListener<Boolean> selectionListener =
            (observable, wasSelected, isSelected) -> thumb.setTranslateX(isSelected ? 9.0 : -9.0);
    private final Consumer<Locale> localizationListener = ignored -> refreshTexts();

    private Supplier<Boolean> currentValue = () -> false;
    private Consumer<Boolean> selectedAction = ignored -> { };
    private boolean updatingSelection;
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
        toggle.setGraphic(createSwitchGraphic());
        toggle.setText("");
        toggle.setAccessibleHelp(localization.textFrom("i18n.fullScreen", "description"));
        toggle.setDisable(true);
        toggle.setOnAction(event -> {
            if (!updatingSelection && configured) {
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
        updatingSelection = true;
        try {
            toggle.setSelected(Boolean.TRUE.equals(currentValue.get()));
        } finally {
            updatingSelection = false;
        }
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
        toggle.setOnAction(null);
        toggle.setDisable(true);
        toggle.selectedProperty().removeListener(selectionListener);
        selectedAction = ignored -> { };
        currentValue = () -> false;
        configured = false;
        localization.removeListener(localizationListener);
    }

    /** Надає сам перемикач тестам подання без оприлюднення його як частини публічного API. */
    ToggleButton toggleForTest() {
        return toggle;
    }

    /** Створює графічну доріжку та рухому кнопку індикатора стану перемикача. */
    private StackPane createSwitchGraphic() {
        Rectangle track = new Rectangle(42.0, 24.0);
        track.getStyleClass().add("fullscreen-switch-track");
        track.setArcWidth(24.0);
        track.setArcHeight(24.0);

        thumb.getStyleClass().add("fullscreen-switch-thumb");
        thumb.setTranslateX(-9.0);
        toggle.selectedProperty().addListener(selectionListener);

        StackPane graphic = new StackPane(track, thumb);
        graphic.getStyleClass().add("fullscreen-switch-graphic");
        graphic.setMinSize(42.0, 24.0);
        graphic.setPrefSize(42.0, 24.0);
        graphic.setMaxSize(42.0, 24.0);
        graphic.setMouseTransparent(true);
        return graphic;
    }
}
