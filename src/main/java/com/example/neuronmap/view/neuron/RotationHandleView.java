package com.example.neuronmap.view.neuron;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.view.support.TooltipFactory;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Відображає інтерактивну ручку обертання та встановлює її положення й візуальний кут.
 *
 * <p>Область натискання обмежена круглою поверхнею, а не прямокутними межами кнопки.
 * Локалізована підказка прив'язується на час активної взаємодії з ручкою.</p>
 */
public final class RotationHandleView extends Button {

    public static final double SIZE = 22.0;
    /** Геометричний зазор між нейроном і ручкою обертання; використовується позиціонером накладок. */
    public static final double GAP = 8.0;

    private static final String TOOLTIP_BUNDLE = "i18n.rotationHandle";
    private static final String ROTATE_TOOLTIP_KEY = "rotate";

    private final StackPane graphicPane = new StackPane();
    private final Circle background = new Circle(SIZE / 2.0);
    private final Label glyph = new Label("↻");

    private LocalizationService localization;
    private Consumer<Locale> localizationListener;

    /** Створює круглу ручку та обмежує її інтерактивну область видимою формою кола. */
    public RotationHandleView() {
        getStyleClass().add("rotation-handle-button");

        setMinSize(SIZE, SIZE);
        setPrefSize(SIZE, SIZE);
        setMaxSize(SIZE, SIZE);
        setManaged(false);
        resize(SIZE, SIZE);

        setPadding(Insets.EMPTY);
        setAlignment(Pos.CENTER);
        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        setFocusTraversable(false);
        setPickOnBounds(false);
        setMouseTransparent(false);

        background.getStyleClass().add("rotation-handle");
        background.setMouseTransparent(true);

        glyph.getStyleClass().add("rotation-handle-glyph");
        glyph.setMouseTransparent(true);

        graphicPane.setMinSize(SIZE, SIZE);
        graphicPane.setPrefSize(SIZE, SIZE);
        graphicPane.setMaxSize(SIZE, SIZE);
        graphicPane.setAlignment(Pos.CENTER);
        graphicPane.setMouseTransparent(true);
        graphicPane.getChildren().addAll(background, glyph);

        setGraphic(graphicPane);
    }

    /**
     * Прив'язує до ручки локалізовану підказку та оновлює її під час перемикання мови.
     * Повторне прив'язування спочатку знімає попереднього слухача, щоб уникнути витоків ресурсів.
     *
     * @param localization спільна служба локалізації інтерфейсу.
     * @throws NullPointerException якщо службу локалізації не задано.
     */
    public void bindLocalizedTooltip(LocalizationService localization) {
        Objects.requireNonNull(localization, "localization");
        if (this.localization == localization && localizationListener != null) {
            refreshLocalizedTooltip();
            return;
        }

        unbindLocalizedTooltip();
        this.localization = localization;
        setTooltip(TooltipFactory.create(localization.textFrom(TOOLTIP_BUNDLE, ROTATE_TOOLTIP_KEY)));
        localizationListener = ignored -> refreshLocalizedTooltip();
        localization.addListener(localizationListener);
    }

    /**
     * Від'єднує слухача локалізації та прибирає підказку, звільняючи пов'язані ресурси.
     */
    public void unbindLocalizedTooltip() {
        if (localization != null && localizationListener != null) {
            localization.removeListener(localizationListener);
        }
        localization = null;
        localizationListener = null;
        setTooltip(null);
    }

    /**
     * Перевіряє, чи лежить точка у видимій круглій поверхні кнопки.
     *
     * <p>Перевірка спирається на геометрію кола та його рамку, а не на прямокутні межі кнопки.
     * Візуальні ефекти не розширюють інтерактивну область.</p>
     *
     * @param localX горизонтальна координата точки в локальній системі кнопки.
     * @param localY вертикальна координата точки в локальній системі кнопки.
     * @return {@code true}, якщо точка перетинає видиме коло.
     */
    @Override
    public boolean contains(double localX, double localY) {
        double center = SIZE / 2.0;
        double hitRadius = background.getRadius() + background.getStrokeWidth() / 2.0;
        double offsetX = localX - center;
        double offsetY = localY - center;
        return offsetX * offsetX + offsetY * offsetY <= hitRadius * hitRadius;
    }

    /**
     * Переміщує ручку обертання так, щоб її центр збігався з переданою точкою батьківської області.
     *
     * @param parentCenter центр ручки в локальних координатах батьківського контейнера.
     * @throws IllegalArgumentException якщо координату центру не задано.
     */
    public void placeCenterAt(Point2D parentCenter) {
        if (parentCenter == null) {
            throw new IllegalArgumentException("parentCenter must not be null");
        }

        setTranslateX(0.0);
        setTranslateY(0.0);
        setScaleX(1.0);
        setScaleY(1.0);
        setRotate(0.0);
        resize(SIZE, SIZE);

        relocate(
                parentCenter.getX() - SIZE / 2.0,
                parentCenter.getY() - SIZE / 2.0
        );
    }

    /**
     * Установлює візуальний кут графічного символу ручки.
     *
     * @param degrees кут повороту в градусах.
     */
    public void setVisualRotation(double degrees) {
        graphicPane.setRotate(degrees);
    }

    /**
     * Синхронізує текст підказки з поточною активною локаллю, якщо підказку прив'язано.
     */
    private void refreshLocalizedTooltip() {
        if (localization != null && getTooltip() != null) {
            getTooltip().setText(localization.textFrom(TOOLTIP_BUNDLE, ROTATE_TOOLTIP_KEY));
        }
    }

    /**
     * Скидає візуальний кут і від'єднує підказку перед повторним використанням або вилученням ручки.
     */
    public void dispose() {
        unbindLocalizedTooltip();
        graphicPane.setRotate(0.0);
    }
}
