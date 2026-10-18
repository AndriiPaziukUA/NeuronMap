package com.example.neuronmap.view.menu;

import com.example.neuronmap.i18n.LocalizationService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;

import java.net.URL;
import java.util.Objects;

/** Відображає модальний діалог підтвердження перед втратою незбережених змін. */
public final class UnsavedChangesView extends StackPane {

    private static final String STYLESHEET_RESOURCE = "/unsaved-changes.css";

    private final LocalizationService localization;
    private final VBox card = new VBox(12.0);
    private final Label title = new Label();
    private final Label message = new Label();
    private final Button yesButton = new Button();
    private final Button noButton = new Button();
    private Runnable pendingSaveAction;
    private Runnable pendingDiscardAction;

    /**
     * Створює накладку підтвердження з локалізованими текстами й незалежним стилем кнопок.
     *
     * @param localization служба перекладів діалогу.
     * @throws NullPointerException якщо службу локалізації не задано.
     * @throws IllegalStateException якщо таблиця стилів діалогу відсутня в classpath.
     */
    public UnsavedChangesView(LocalizationService localization) {
        this.localization = Objects.requireNonNull(localization, "localization");
        URL stylesheet = getClass().getResource(STYLESHEET_RESOURCE);
        if (stylesheet == null) {
            throw new IllegalStateException(STYLESHEET_RESOURCE + " is missing");
        }
        getStylesheets().add(stylesheet.toExternalForm());

        setManaged(false);
        setVisible(false);
        setPickOnBounds(true);
        getStyleClass().add("unsaved-overlay");

        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(22.0));
        card.setMinSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        card.setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        card.setMaxWidth(Region.USE_PREF_SIZE);
        card.setMaxHeight(Region.USE_PREF_SIZE);
        card.getStyleClass().add("unsaved-card");

        title.getStyleClass().add("unsaved-title");
        title.setWrapText(true);
        title.setAlignment(Pos.CENTER);
        title.setTextAlignment(TextAlignment.CENTER);
        title.setMaxWidth(360.0);

        message.getStyleClass().add("unsaved-message");
        message.setWrapText(true);
        message.setMaxWidth(360.0);
        message.setAlignment(Pos.CENTER);
        message.setTextAlignment(TextAlignment.CENTER);

        HBox buttons = new HBox(10.0, yesButton, noButton);
        buttons.setAlignment(Pos.CENTER);
        // Обидві дії мають один клас і однакову візуальну роль у межах цього діалогу.
        yesButton.getStyleClass().add("unsaved-action-button");
        noButton.getStyleClass().add("unsaved-action-button");
        card.getChildren().addAll(title, message, buttons);
        getChildren().add(card);
        StackPane.setAlignment(card, Pos.CENTER);
        refreshTexts();
    }

    /** Перевіряє, чи відображається діалог підтвердження. */
    public boolean isShowing() {
        return isVisible();
    }

    /**
     * Показує діалог і запам'ятовує дії збереження або відхилення змін.
     *
     * @param saveAction дія збереження та продовження.
     * @param discardAction дія відхилення змін і продовження.
     */
    public void show(Runnable saveAction, Runnable discardAction) {
        pendingSaveAction = Objects.requireNonNull(saveAction, "saveAction");
        pendingDiscardAction = Objects.requireNonNull(discardAction, "discardAction");
        yesButton.setOnAction(event -> {
            runPendingAction(pendingSaveAction);
            event.consume();
        });
        noButton.setOnAction(event -> {
            runPendingAction(pendingDiscardAction);
            event.consume();
        });
        refreshTexts();
        setManaged(true);
        setVisible(true);
        applyCss();
        card.applyCss();
        sizeButtonsToLargestContent();
        requestFocus();
    }

    /** Запускає сценарій відхилення змін, якщо діалог відкрито. */
    public void discardAndContinue() {
        if (isShowing()) {
            runPendingAction(pendingDiscardAction);
        }
    }

    /** Ховає діалог та звільняє callback-и без виконання будь-якої дії. */
    public void hide() {
        setVisible(false);
        setManaged(false);
        yesButton.setOnAction(null);
        noButton.setOnAction(null);
        pendingSaveAction = null;
        pendingDiscardAction = null;
    }

    /** Оновлює написи діалогу відповідно до активної мови. */
    public void refreshTexts() {
        title.setText(localization.text("menu.unsaved.title"));
        message.setText(localization.text("menu.unsaved.message"));
        yesButton.setText(localization.text("common.yes"));
        noButton.setText(localization.text("common.no"));
        if (isShowing() && getScene() != null) {
            sizeButtonsToLargestContent();
        }
    }

    /**
     * Повертає кнопку «Ні» для перевірки відхилення незбережених змін.
     *
     * @return кнопка відхилення змін у діалозі.
     */
    Button discardButtonForTest() {
        return noButton;
    }

    /**
     * Повертає кнопку «Так» для перевірки підтвердження змін.
     *
     * @return кнопка збереження змін у діалозі.
     */
    Button saveButtonForTest() {
        return yesButton;
    }

    /** Повертає максимальну висоту картки для тестів адаптивного компонування. */
    double cardMaxHeightForTest() {
        return card.getMaxHeight();
    }

    /** Повертає фактичну ширину картки після компонування. */
    double cardWidthForTest() {
        return card.getWidth();
    }

    /** Повертає фактичну висоту картки після компонування. */
    double cardHeightForTest() {
        return card.getHeight();
    }

    /** Повертає вирівнювання тексту діалогу для регресійних перевірок. */
    TextAlignment messageTextAlignmentForTest() {
        return message.getTextAlignment();
    }

    /** Повертає правило максимальної ширини картки. */
    double cardMaxWidthForTest() {
        return card.getMaxWidth();
    }

    /** Вимірює кнопки за текстом і вирівнює їх за найбільшим природним розміром. */
    private void sizeButtonsToLargestContent() {
        resetButtonDimensions(yesButton);
        resetButtonDimensions(noButton);
        yesButton.applyCss();
        noButton.applyCss();

        double width = Math.max(yesButton.prefWidth(-1.0), noButton.prefWidth(-1.0));
        double height = Math.max(yesButton.prefHeight(width), noButton.prefHeight(width));
        applyButtonDimensions(yesButton, width, height);
        applyButtonDimensions(noButton, width, height);
    }

    /** Скидає розміри кнопки перед вимірюванням її природного розміру. */
    private static void resetButtonDimensions(Button button) {
        button.setMinSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        button.setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        button.setMaxSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
    }

    /** Призначає обом кнопкам виміряні розміри найбільшої кнопки пари. */
    private static void applyButtonDimensions(Button button, double width, double height) {
        // Мінімальний розмір відповідає найбільшому підпису; preferred/max не мають фіксованих значень.
        button.setMinSize(width, height);
        button.setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        button.setMaxSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
    }

    /** Ховає діалог перед виконанням збереженої дії. */
    private void runPendingAction(Runnable action) {
        Runnable actionToRun = action;
        hide();
        if (actionToRun != null) {
            actionToRun.run();
        }
    }
}
