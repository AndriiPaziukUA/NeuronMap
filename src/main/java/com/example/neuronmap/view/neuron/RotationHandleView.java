package com.example.neuronmap.view.neuron;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.view.support.TooltipFactory;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Відображає інтерактивну ручку обертання та встановлює її положення й візуальний кут.
 *
 * <p>Область натискання обмежена круглою поверхнею, а не прямокутними межами кнопки.
 * Локалізована підказка може тимчасово від'єднуватися на час жесту обертання без зміни
 * доступної назви та з відновленням актуального перекладу.</p>
 */
public final class RotationHandleView extends Button {

    public static final double SIZE = 22.0;
    /** Геометричний зазор між нейроном і ручкою обертання; використовується позиціонером накладок. */
    public static final double GAP = 8.0;

    private static final String TOOLTIP_BUNDLE = "i18n.rotationHandle";
    private static final String ROTATE_TOOLTIP_KEY = "rotate";
    /** Радіус кругової частини значка, симетрично розміщеної всередині ручки. */
    private static final double ROTATION_ICON_RADIUS = 6.2;
    /** Довжина кожного штриха, що формує вістря кругової стрілки. */
    private static final double ROTATION_ARROW_HEAD_LENGTH = 3.3;

    private final StackPane graphicPane = new StackPane();
    private final Circle background = new Circle(SIZE / 2.0);
    private final Arc rotationArc = createRotationArc();
    private final Path arrowHead = createRotationArrowHead();

    private LocalizationService localization;
    private Consumer<Locale> localizationListener;
    private Tooltip localizedTooltip;
    private boolean tooltipSuspended;

    /** Створює круглу ручку та обмежує її інтерактивну область видимою формою кола. */
    public RotationHandleView() {
        getStyleClass().add("rotation-handle-button");

        setMinSize(SIZE, SIZE);
        setPrefSize(SIZE, SIZE);
        setMaxSize(SIZE, SIZE);
        setManaged(false);
        resize(SIZE, SIZE);

        setPadding(Insets.EMPTY);
        setText("");
        setAlignment(Pos.CENTER);
        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        setFocusTraversable(false);
        setPickOnBounds(false);
        setMouseTransparent(false);

        background.getStyleClass().add("rotation-handle");
        background.setMouseTransparent(true);

        rotationArc.getStyleClass().add("rotation-handle-glyph");
        rotationArc.setManaged(false);
        rotationArc.setMouseTransparent(true);
        arrowHead.getStyleClass().add("rotation-handle-glyph");
        arrowHead.setManaged(false);
        arrowHead.setMouseTransparent(true);

        graphicPane.setMinSize(SIZE, SIZE);
        graphicPane.setPrefSize(SIZE, SIZE);
        graphicPane.setMaxSize(SIZE, SIZE);
        graphicPane.setAlignment(Pos.CENTER);
        graphicPane.setMouseTransparent(true);
        graphicPane.getChildren().addAll(background, rotationArc, arrowHead);

        setGraphic(graphicPane);
    }

    /**
     * Прив'язує локалізовану підказку та доступну назву ручки й оновлює їх під час перемикання мови.
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
        String tooltipText = localization.textFrom(TOOLTIP_BUNDLE, ROTATE_TOOLTIP_KEY);
        localizedTooltip = TooltipFactory.create(tooltipText);
        setTooltip(localizedTooltip);
        setAccessibleText(tooltipText);
        localizationListener = ignored -> refreshLocalizedTooltip();
        localization.addListener(localizationListener);
    }

    /**
     * Тимчасово від'єднує спливаючу підказку від кнопки, зберігаючи її для подальшого відновлення.
     * Доступна назва залишається на кнопці, а переклад збереженої підказки й далі оновлюється.
     */
    public void suspendTooltip() {
        if (tooltipSuspended || localizedTooltip == null) {
            return;
        }
        tooltipSuspended = true;
        localizedTooltip.hide();
        setTooltip(null);
    }

    /**
     * Відновлює підказку після завершення взаємодії, якщо її раніше було призупинено.
     * Повторний виклик без попереднього призупинення нічого не змінює.
     */
    public void resumeTooltip() {
        if (!tooltipSuspended) {
            return;
        }
        tooltipSuspended = false;
        if (localizedTooltip != null) {
            setTooltip(localizedTooltip);
        }
    }

    /**
     * Від'єднує слухача локалізації та прибирає підказку й доступну назву, звільняючи пов'язані ресурси.
     */
    public void unbindLocalizedTooltip() {
        if (localization != null && localizationListener != null) {
            localization.removeListener(localizationListener);
        }
        localization = null;
        localizationListener = null;
        localizedTooltip = null;
        tooltipSuspended = false;
        setTooltip(null);
        setAccessibleText(null);
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
     * Установлює візуальний кут векторного значка ручки.
     *
     * @param degrees кут повороту в градусах.
     */
    public void setVisualRotation(double degrees) {
        graphicPane.setRotate(degrees);
    }

    /**
     * Створює кругову дугу зі сталим радіусом і центром у центрі ручки.
     * Завдяки цьому проміжки від самої кругової частини до країв ручки однакові з усіх боків;
     * вістря стрілки є окремою фігурою й не впливає на центрування дуги.
     *
     * @return відкрита дуга без власного кольору, який задає таблиця стилів.
     */
    private static Arc createRotationArc() {
        Arc arc = new Arc(
                SIZE / 2.0,
                SIZE / 2.0,
                ROTATION_ICON_RADIUS,
                ROTATION_ICON_RADIUS,
                45.0,
                270.0
        );
        arc.setType(ArcType.OPEN);
        return arc;
    }

    /**
     * Створює два штрихи вістря на початковій точці дуги, використовуючи її радіус і центр.
     *
     * @return контур вістря без заливки, оформлення якого задається CSS.
     */
    private static Path createRotationArrowHead() {
        double center = SIZE / 2.0;
        double offset = ROTATION_ICON_RADIUS / Math.sqrt(2.0);
        double tipX = center + offset;
        double tipY = center - offset;
        Path path = new Path(
                new MoveTo(tipX, tipY),
                new LineTo(tipX - ROTATION_ARROW_HEAD_LENGTH, tipY),
                new MoveTo(tipX, tipY),
                new LineTo(tipX, tipY - ROTATION_ARROW_HEAD_LENGTH)
        );
        return path;
    }

    /** Синхронізує текст збереженої підказки й доступну назву з активною локаллю. */
    private void refreshLocalizedTooltip() {
        if (localization != null && localizedTooltip != null) {
            String tooltipText = localization.textFrom(TOOLTIP_BUNDLE, ROTATE_TOOLTIP_KEY);
            localizedTooltip.setText(tooltipText);
            setAccessibleText(tooltipText);
        }
    }

    /** Скидає візуальний кут і від'єднує підказку та доступну назву перед вилученням ручки. */
    public void dispose() {
        unbindLocalizedTooltip();
        graphicPane.setRotate(0.0);
    }
}
