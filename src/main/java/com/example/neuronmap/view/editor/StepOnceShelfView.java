package com.example.neuronmap.view.editor;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.beans.value.ChangeListener;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

import java.util.Objects;

/** Керує висувною кнопкою наступного такту та лінією, яка візуально групує її з кнопкою продовження. */
final class StepOnceShelfView {

    private static final double BUTTON_SIZE = 31.0;
    private static final double UNDERLINE_GAP = 5.0;
    private static final Duration ANIMATION_DURATION = Duration.millis(135.0);
    private static final Duration CLOSE_DELAY = Duration.millis(120.0);

    private final HBox toolbarRoot;
    private final Button continueButton;
    private final Button stopSignalsButton;
    private final Button stepOnceButton = createStepOnceButton();
    private final StackPane shelf = new StackPane(stepOnceButton);
    private final Region groupUnderline = new Region();
    private final Rectangle shelfClip = new Rectangle();
    private final Timeline animation = new Timeline();
    private final PauseTransition closeDelay = new PauseTransition(CLOSE_DELAY);
    private final EventHandler<ActionEvent> stepOnceActionHandler = event -> handleStepOnceAction();
    private final EventHandler<MouseEvent> continueEnteredHandler = event -> handleContinueEntered();
    private final EventHandler<MouseEvent> continueExitedHandler = event -> handleContinueExited();
    private final EventHandler<MouseEvent> shelfEnteredHandler = event -> handleShelfEntered();
    private final EventHandler<MouseEvent> shelfExitedHandler = event -> handleShelfExited();
    private final ChangeListener<Number> geometryListener = this::handleGeometryChanged;
    private final ChangeListener<Number> preferredWidthListener = this::handlePreferredWidthChanged;
    private final ChangeListener<Number> stopPositionListener = this::handleStopPositionChanged;

    private Runnable stepOnceAction = () -> { };
    private boolean simulationPaused;
    private boolean controlsVisible;
    private boolean singleStepModeActive;
    private boolean continueButtonHovered;
    private boolean shelfHovered;
    private boolean disposed;
    private double collapsedStopLayoutX = Double.NaN;

    /**
     * Створює висувну область і накладену лінію, не змінюючи ієрархію кнопок панелі.
     *
     * @param toolbarRoot кореневий контейнер панелі, відносно якого позиціонується лінія.
     * @param continueButton наявна кнопка паузи/продовження.
     * @param stopSignalsButton наявна кнопка зупинки сигналів, яку потрібно відсувати разом із шухлядкою.
     * @param resumeIconPath SVG-шлях піктограми продовження для однотактної кнопки.
     * @throws NullPointerException якщо контейнер, кнопки або шлях піктограми не задано.
     */
    StepOnceShelfView(
            HBox toolbarRoot,
            Button continueButton,
            Button stopSignalsButton,
            String resumeIconPath
    ) {
        this.toolbarRoot = Objects.requireNonNull(toolbarRoot, "toolbarRoot");
        this.continueButton = Objects.requireNonNull(continueButton, "continueButton");
        this.stopSignalsButton = Objects.requireNonNull(stopSignalsButton, "stopSignalsButton");
        String iconPath = Objects.requireNonNull(resumeIconPath, "resumeIconPath");

        shelf.getStyleClass().add("simulation-step-shelf");
        shelf.setAlignment(Pos.CENTER_LEFT);
        shelf.setMinWidth(0.0);
        shelf.setPrefWidth(0.0);
        shelf.setMaxWidth(BUTTON_SIZE);
        shelf.setMinHeight(BUTTON_SIZE);
        shelf.setPrefHeight(BUTTON_SIZE);
        shelf.setMaxHeight(BUTTON_SIZE);
        shelf.setClip(shelfClip);
        shelfClip.widthProperty().bind(shelf.widthProperty());
        shelfClip.heightProperty().bind(shelf.heightProperty());

        groupUnderline.getStyleClass().add("simulation-step-group-underline");
        groupUnderline.setMinWidth(0.0);
        groupUnderline.setPrefWidth(0.0);
        groupUnderline.setMaxWidth(Double.MAX_VALUE);
        groupUnderline.setMinHeight(1.0);
        groupUnderline.setPrefHeight(1.0);
        groupUnderline.setMaxHeight(1.0);
        groupUnderline.setMouseTransparent(true);
        groupUnderline.setManaged(false);
        groupUnderline.setVisible(false);
        groupUnderline.setOpacity(0.0);

        setManagedAndVisible(shelf, false);
        setManagedAndVisible(stepOnceButton, false);
        stepOnceButton.setGraphic(createStepOnceIcon(iconPath));
        stepOnceButton.addEventHandler(ActionEvent.ACTION, stepOnceActionHandler);
        continueButton.addEventHandler(MouseEvent.MOUSE_ENTERED, continueEnteredHandler);
        continueButton.addEventHandler(MouseEvent.MOUSE_EXITED, continueExitedHandler);
        shelf.addEventHandler(MouseEvent.MOUSE_ENTERED, shelfEnteredHandler);
        shelf.addEventHandler(MouseEvent.MOUSE_EXITED, shelfExitedHandler);

        continueButton.layoutXProperty().addListener(geometryListener);
        continueButton.layoutYProperty().addListener(geometryListener);
        continueButton.widthProperty().addListener(geometryListener);
        continueButton.heightProperty().addListener(geometryListener);
        shelf.layoutXProperty().addListener(geometryListener);
        shelf.layoutYProperty().addListener(geometryListener);
        shelf.widthProperty().addListener(geometryListener);
        shelf.heightProperty().addListener(geometryListener);
        shelf.prefWidthProperty().addListener(preferredWidthListener);
        stopSignalsButton.layoutXProperty().addListener(stopPositionListener);

        animation.setOnFinished(event -> finishCollapse());
        closeDelay.setOnFinished(event -> collapseIfPointerLeft());
        updateUnderlineGeometry();
    }

    /** Повертає контейнер висувної кнопки, який бере участь у компонуванні панелі. */
    StackPane node() {
        return shelf;
    }

    /** Повертає лінію-накладку, яка візуально підкреслює групу кнопок. */
    Region underline() {
        return groupUnderline;
    }

    /** Повертає кнопку однотактного продовження для підключення дії та перевірок. */
    Button button() {
        return stepOnceButton;
    }

    /**
     * Установлює дію, яку потрібно виконати після натискання кнопки наступного такту.
     *
     * @param action дія однотактного виконання.
     * @throws NullPointerException якщо дію не задано.
     */
    void setAction(Runnable action) {
        stepOnceAction = Objects.requireNonNull(action, "action");
    }

    /**
     * Задає локалізовану підказку та доступну назву кнопки.
     *
     * @param text текст підказки.
     * @throws NullPointerException якщо текст не задано.
     */
    void setTooltipText(String text) {
        String tooltipText = Objects.requireNonNull(text, "text");
        stepOnceButton.setTooltip(new Tooltip(tooltipText));
        stepOnceButton.setAccessibleText(tooltipText);
    }

    /**
     * Вмикає або вимикає групу керування та скидає стан шухлядки, коли симуляція неактивна.
     *
     * @param visible чи доступні елементи керування симуляцією.
     */
    void setSimulationControlsVisible(boolean visible) {
        controlsVisible = visible;
        if (!visible) {
            singleStepModeActive = false;
            continueButtonHovered = false;
            shelfHovered = false;
            closeImmediately();
            return;
        }
        updateVisibility();
    }

    /**
     * Оновлює стан звичайної паузи та завершує утримання шухлядки після відновлення симуляції.
     *
     * @param paused чи призупинена симуляція.
     */
    void setSimulationPaused(boolean paused) {
        simulationPaused = paused;
        if (!paused) {
            singleStepModeActive = false;
            continueButtonHovered = false;
            shelfHovered = false;
            closeDelay.stop();
        }
        updateVisibility();
    }

    /** Зупиняє анімацію та видаляє обробники подій і слухачі під час звільнення панелі. */
    void dispose() {
        if (disposed) return;
        disposed = true;
        stepOnceButton.removeEventHandler(ActionEvent.ACTION, stepOnceActionHandler);
        continueButton.removeEventHandler(MouseEvent.MOUSE_ENTERED, continueEnteredHandler);
        continueButton.removeEventHandler(MouseEvent.MOUSE_EXITED, continueExitedHandler);
        shelf.removeEventHandler(MouseEvent.MOUSE_ENTERED, shelfEnteredHandler);
        shelf.removeEventHandler(MouseEvent.MOUSE_EXITED, shelfExitedHandler);
        continueButton.layoutXProperty().removeListener(geometryListener);
        continueButton.layoutYProperty().removeListener(geometryListener);
        continueButton.widthProperty().removeListener(geometryListener);
        continueButton.heightProperty().removeListener(geometryListener);
        shelf.layoutXProperty().removeListener(geometryListener);
        shelf.layoutYProperty().removeListener(geometryListener);
        shelf.widthProperty().removeListener(geometryListener);
        shelf.heightProperty().removeListener(geometryListener);
        shelf.prefWidthProperty().removeListener(preferredWidthListener);
        stopSignalsButton.layoutXProperty().removeListener(stopPositionListener);
        stopSignalsButton.setTranslateX(0.0);
        animation.stop();
        closeDelay.stop();
        shelfClip.widthProperty().unbind();
        shelfClip.heightProperty().unbind();
    }

    /** Відкриває шухлядку лише якщо вказівник наведено на кнопку, що зараз продовжує симуляцію. */
    private void handleContinueEntered() {
        continueButtonHovered = controlsVisible && simulationPaused;
        if (continueButtonHovered) expand();
    }

    /** Відкладає згортання, щоб курсор міг перейти через проміжок до висувної кнопки. */
    private void handleContinueExited() {
        continueButtonHovered = false;
        scheduleCollapse();
    }

    /** Не дозволяє ховати панель, поки курсор перебуває на самій висувній кнопці. */
    private void handleShelfEntered() {
        shelfHovered = controlsVisible && simulationPaused;
        closeDelay.stop();
        if (shelfHovered) expand();
    }

    /** Планує згортання після виходу курсора з висувної кнопки. */
    private void handleShelfExited() {
        shelfHovered = false;
        scheduleCollapse();
    }

    /** Фіксує однотактний режим до зняття ручної паузи та запускає передану дію. */
    private void handleStepOnceAction() {
        if (controlsVisible && simulationPaused) {
            singleStepModeActive = true;
            expand();
        }
        stepOnceAction.run();
    }

    /** Показує кнопку в ручній паузі й розгортає шухлядку, якщо її утримує наведення або однотактний режим. */
    private void updateVisibility() {
        if (controlsVisible && simulationPaused) {
            setManagedAndVisible(stepOnceButton, true);
            if (continueButton.isHover()) continueButtonHovered = true;
            if (singleStepModeActive || continueButtonHovered || shelfHovered) expand();
            else scheduleCollapse();
            return;
        }
        setManagedAndVisible(stepOnceButton, false);
        if (!controlsVisible) {
            closeImmediately();
            return;
        }
        animateTo(0.0);
    }

    /** Висуває обрізану область і лінію, не змінюючи розмір кнопки наступного такту. */
    private void expand() {
        if (disposed || !controlsVisible || !simulationPaused) return;
        closeDelay.stop();
        setManagedAndVisible(shelf, true);
        groupUnderline.setVisible(true);
        animateTo(BUTTON_SIZE);
    }

    /** Планує згортання, якщо шухлядку не утримує наведення або однотактний режим. */
    private void scheduleCollapse() {
        if (disposed || singleStepModeActive || continueButtonHovered || shelfHovered) return;
        closeDelay.playFromStart();
    }

    /** Згортає шухлядку лише тоді, коли вказівник залишив обидві кнопки. */
    private void collapseIfPointerLeft() {
        if (disposed || !controlsVisible || !simulationPaused || singleStepModeActive) return;
        if (continueButton.isHover()) continueButtonHovered = true;
        if (shelf.isHover()) shelfHovered = true;
        if (continueButtonHovered || shelfHovered) expand();
        else animateTo(0.0);
    }

    /**
     * Анімує ширину шухлядки та прозорість лінії, щоб кнопка «Стоп» зсувалася разом із нею.
     *
     * @param width цільова ширина шухлядки в пікселях.
     */
    private void animateTo(double width) {
        closeDelay.stop();
        if (width > 0.0) {
            setManagedAndVisible(shelf, true);
            groupUnderline.setVisible(true);
        }
        animation.stop();
        double opacity = width > 0.0 ? 1.0 : 0.0;
        if (Double.compare(shelf.getPrefWidth(), width) == 0
                && Double.compare(groupUnderline.getOpacity(), opacity) == 0) {
            finishCollapse();
            return;
        }
        animation.getKeyFrames().setAll(new KeyFrame(
                ANIMATION_DURATION,
                // Мінімальна ширина не дозволяє HBox накласти наступну кнопку на шухлядку.
                new KeyValue(shelf.minWidthProperty(), width, Interpolator.EASE_OUT),
                new KeyValue(shelf.prefWidthProperty(), width, Interpolator.EASE_OUT),
                new KeyValue(groupUnderline.opacityProperty(), opacity, Interpolator.EASE_OUT)
        ));
        animation.playFromStart();
    }

    /** Прибирає повністю згорнуту шухлядку й лінію, не залишаючи місця в компонуванні. */
    private void finishCollapse() {
        if (shelf.getPrefWidth() > 0.0 || groupUnderline.getOpacity() > 0.0) return;
        setManagedAndVisible(shelf, false);
        groupUnderline.setVisible(false);
        if (!controlsVisible || !simulationPaused) setManagedAndVisible(stepOnceButton, false);
        updateUnderlineGeometry();
    }

    /** Негайно згортає шухлядку, коли керування симуляцією стає недоступним. */
    private void closeImmediately() {
        animation.stop();
        closeDelay.stop();
        shelf.setMinWidth(0.0);
        shelf.setPrefWidth(0.0);
        groupUnderline.setOpacity(0.0);
        setManagedAndVisible(shelf, false);
        groupUnderline.setVisible(false);
        setManagedAndVisible(stepOnceButton, false);
        stopSignalsButton.setTranslateX(0.0);
        toolbarRoot.requestLayout();
        updateUnderlineGeometry();
    }

    /** Оновлює геометрію лінії після зміни розміру або позиції пов'язаного вузла. */
    private void handleGeometryChanged(
            javafx.beans.value.ObservableValue<? extends Number> observable,
            Number oldValue,
            Number newValue
    ) {
        updateUnderlineGeometry();
        updateStopButtonOffset();
    }

    /** Запитує нове компонування панелі після зміни цільової ширини висувної області. */
    private void handlePreferredWidthChanged(
            javafx.beans.value.ObservableValue<? extends Number> observable,
            Number oldValue,
            Number newValue
    ) {
        toolbarRoot.requestLayout();
        updateStopButtonOffset();
    }

    /** Оновлює компенсацію зсуву кнопки «Стоп» після компонування її батьківського HBox. */
    private void handleStopPositionChanged(
            javafx.beans.value.ObservableValue<? extends Number> observable,
            Number oldValue,
            Number newValue
    ) {
        if (disposed) return;
        if (!shelf.isManaged()) {
            stopSignalsButton.setTranslateX(0.0);
            collapsedStopLayoutX = stopSignalsButton.getLayoutX();
            return;
        }
        updateStopButtonOffset();
    }

    /**
     * Підтримує видимий зсув кнопки «Стоп» навіть до наступного layout-проходу JavaFX.
     * Якщо HBox уже зсунув кнопку, компенсація дорівнює нулю; якщо ні, застосовується translateX.
     */
    private void updateStopButtonOffset() {
        if (disposed || stopSignalsButton.getParent() != toolbarRoot || !shelf.isManaged()) return;
        double width = Math.max(0.0, Math.min(BUTTON_SIZE, shelf.getWidth()));
        double fraction = width / BUTTON_SIZE;
        double desiredOffset = width + toolbarRoot.getSpacing() * fraction;
        if (!Double.isFinite(collapsedStopLayoutX)) {
            collapsedStopLayoutX = stopSignalsButton.getLayoutX() - desiredOffset;
        }
        double actualOffset = stopSignalsButton.getLayoutX() - collapsedStopLayoutX;
        stopSignalsButton.setTranslateX(desiredOffset - actualOffset);
    }

    /** Позиціонує накладену лінію від лівого краю кнопки продовження до правого краю шухлядки. */
    private void updateUnderlineGeometry() {
        if (disposed) return;
        double left = continueButton.getLayoutX();
        double right = shelf.getLayoutX() + shelf.getWidth();
        double top = continueButton.getLayoutY() + continueButton.getHeight() + UNDERLINE_GAP;
        groupUnderline.resizeRelocate(left, top, Math.max(0.0, right - left), 1.0);
        updateStopButtonOffset();
    }

    /** Задає visible та managed разом, щоб прихований вузол не займав місця у компонуванні. */
    private static void setManagedAndVisible(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    /** Створює векторну піктограму продовження з накладеною цифрою «1». */
    private static StackPane createStepOnceIcon(String resumeIconPath) {
        SVGPath triangle = new SVGPath();
        triangle.getStyleClass().add("simulation-icon");
        triangle.setContent(resumeIconPath);
        Label number = new Label("1");
        number.getStyleClass().add("simulation-step-number");
        number.setTranslateX(-2.0);
        StackPane icon = new StackPane(triangle, number);
        icon.getStyleClass().add("simulation-step-icon");
        icon.setMinSize(15.0, 15.0);
        icon.setPrefSize(15.0, 15.0);
        icon.setMaxSize(15.0, 15.0);
        return icon;
    }

    /** Створює кнопку однотактного продовження з базовим стилем інших кнопок симуляції. */
    private static Button createStepOnceButton() {
        Button button = new Button();
        button.setText("");
        button.getStyleClass().addAll("simulation-icon-button", "simulation-step-button");
        return button;
    }
}
