package com.example.neuronmap.view;

import com.example.neuronmap.i18n.LocalizationService;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/** Відображає кнопки головної сторінки меню та передає дії користувача контейнеру меню. */
public final class MainMenuHomeView extends VBox {

    private final LocalizationService localization;
    private final Consumer<Locale> localizationListener = ignored -> refreshTexts();
    private final Button continueButton = createMenuButton();
    private final Button newProjectButton = createMenuButton();
    private final Button loadButton = createMenuButton();
    private final Button settingsButton = createMenuButton();
    private final Button exitButton = createMenuButton();

    /**
     * Створює головну сторінку й готує її елементи до локалізації та налаштування callback-ів.
     *
     * @param localization служба перекладів написів головної сторінки.
     */
    public MainMenuHomeView(LocalizationService localization) {
        this.localization = Objects.requireNonNull(localization, "localization");
        setSpacing(10.0);
        setFillWidth(true);
        setAlignment(javafx.geometry.Pos.TOP_CENTER);
        setMinHeight(0.0);
        getChildren().setAll(continueButton, newProjectButton, loadButton, settingsButton, exitButton);
        localization.addListener(localizationListener);
        refreshTexts();
    }

    /**
     * Створює головну сторінку та одразу під'єднує п'ять дій, як це потрібно ізольованим тестам і повторному використанню.
     *
     * @param localization служба перекладів написів головної сторінки.
     * @param continueAction дія продовження роботи.
     * @param newProjectAction дія створення проєкту.
     * @param loadAction дія переходу до списку проєктів.
     * @param settingsAction дія переходу до налаштувань.
     * @param exitAction дія завершення застосунку.
     */
    public MainMenuHomeView(
            LocalizationService localization,
            Runnable continueAction,
            Runnable newProjectAction,
            Runnable loadAction,
            Runnable settingsAction,
            Runnable exitAction
    ) {
        this(localization);
        setActions(continueAction, newProjectAction, loadAction, settingsAction, exitAction);
    }

    /**
     * Під'єднує дії головної сторінки до навігації, яку організовує контролер.
     *
     * @param continueAction дія продовження роботи й закриття меню.
     * @param newProjectAction дія створення проєкту.
     * @param loadAction дія переходу до збережених проєктів.
     * @param settingsAction дія переходу до налаштувань.
     * @param exitAction дія завершення застосунку.
     */
    public void setActions(
            Runnable continueAction,
            Runnable newProjectAction,
            Runnable loadAction,
            Runnable settingsAction,
            Runnable exitAction
    ) {
        setAction(continueButton, continueAction);
        setAction(newProjectButton, newProjectAction);
        setAction(loadButton, loadAction);
        setAction(settingsButton, settingsAction);
        setAction(exitButton, exitAction);
    }

    /**
     * Повертає кнопку налаштувань для перевірки сценаріїв навігації без доступу до внутрішньої структури подання.
     *
     * @return кнопка переходу до сторінки налаштувань.
     */
    public Button settingsButtonForTest() {
        return settingsButton;
    }

    /** Оновлює написи головної сторінки після зміни активної мови. */
    public void refreshTexts() {
        continueButton.setText(localization.text("menu.continue"));
        newProjectButton.setText(localization.text("menu.new_project"));
        loadButton.setText(localization.text("menu.load"));
        settingsButton.setText(localization.text("menu.settings"));
        exitButton.setText(localization.text("menu.exit"));
    }

    /**
     * Повертає підписи кнопок у порядку їх відображення для регресійних тестів.
     *
     * @return незмінний список підписів кнопок головної сторінки.
     */
    public List<String> buttonTextsForTest() {
        return List.of(
                continueButton.getText(), newProjectButton.getText(), loadButton.getText(),
                settingsButton.getText(), exitButton.getText()
        );
    }

    /**
     * Повертає підписи головних кнопок через API, яким користуються тести сторінок меню.
     *
     * @return підписи кнопок у відображуваному порядку.
     */
    public List<String> buttonTexts() {
        return buttonTextsForTest();
    }

    /** Прибирає callback-и й від'єднує слухача локалізації після завершення життєвого циклу. */
    public void dispose() {
        continueButton.setOnAction(null);
        newProjectButton.setOnAction(null);
        loadButton.setOnAction(null);
        settingsButton.setOnAction(null);
        exitButton.setOnAction(null);
        localization.removeListener(localizationListener);
    }

    /**
     * Встановлює callback натискання кнопки, не переносячи навігаційну логіку в подання.
     *
     * @param button кнопка, для якої встановлюється дія.
     * @param action callback, що викликається після натискання.
     */
    private void setAction(Button button, Runnable action) {
        Runnable checkedAction = Objects.requireNonNull(action, "action");
        button.setOnAction(event -> {
            checkedAction.run();
            event.consume();
        });
    }

    /** Створює кнопку стандартного розміру та стилю головного меню. */
    private static Button createMenuButton() {
        Button button = new Button();
        button.getStyleClass().add("main-menu-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setMinHeight(46.0);
        button.setPrefHeight(46.0);
        button.setMaxHeight(46.0);
        return button;
    }
}
