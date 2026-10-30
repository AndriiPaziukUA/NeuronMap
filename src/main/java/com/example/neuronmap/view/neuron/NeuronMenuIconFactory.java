package com.example.neuronmap.view.neuron;

import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.shape.ArcTo;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.CubicCurveTo;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.SVGPath;

/**
 * Створює незалежні від шрифту векторні значки дій контекстного меню нейрона.
 * Геометрія значків має власний фіксований розмір, а товщина й колір ліній задаються CSS.
 */
final class NeuronMenuIconFactory {

    /** Розмір квадратного полотна значка в логічних пікселях. */
    private static final double ICON_SIZE = 20.0;

    /** Забороняє створення екземплярів фабрики статичних векторних значків. */
    private NeuronMenuIconFactory() {
    }

    /**
     * Створює компактний заповнений силует блискавки для активації нейрона.
     * Геометрія блискавки вужча за попередній варіант, оскільки її основна товщина визначається заливкою,
     * а не шириною контуру CSS.
     *
     * @return значок імпульсу у фіксованому графічному контейнері.
     */
    static Node createFireIcon() {
        Path bolt = new Path(
                new MoveTo(11.3, 1.5),
                new LineTo(7.6, 9.2),
                new LineTo(9.8, 9.2),
                new LineTo(8.9, 18.5),
                new LineTo(12.4, 7.4),
                new LineTo(10.4, 7.4),
                new ClosePath()
        );
        bolt.getStyleClass().add("neuron-menu-icon-fill");
        return iconCanvas("neuron-menu-icon-fire", bolt);
    }

    /**
     * Створює стрілку вгору-вправо для дії додавання зв’язку.
     *
     * @return векторний значок додавання зв’язку.
     */
    static Node createAddConnectionIcon() {
        SVGPath arrow = createPath("M5 15 15 5M8 5h7v7");
        return iconCanvas("neuron-menu-icon-add-connection", arrow);
    }

    /**
     * Створює переплетені ланки для дії видалення зв’язку.
     *
     * @return векторний значок видалення зв’язку.
     */
    static Node createDeleteConnectionIcon() {
        SVGPath firstLink = createPath(
                "M7.5 12.5 5.9 14.1a3 3 0 0 1-4.2-4.2L4.8 6.8a3 3 0 0 1 4.2 0");
        SVGPath secondLink = createPath(
                "M12.5 7.5l1.6-1.6a3 3 0 0 1 4.2 4.2L15.2 13.2a3 3 0 0 1-4.2 0");
        SVGPath connector = createPath("M7.3 12.7l5.4-5.4");
        return iconCanvas("neuron-menu-icon-delete-connection", firstLink, secondLink, connector);
    }

    /**
     * Створює одну стрілку, спрямовану проти поточного напрямку нейрона.
     *
     * @param directionReversed {@code true}, якщо вихідний порт нейрона розташований ліворуч.
     * @return векторний значок протилежного напрямку.
     */
    static Node createDirectionIcon(boolean directionReversed) {
        String arrowPath = directionReversed
                ? "M4 10h12M12 6l4 4-4 4"
                : "M16 10H4M8 6l-4 4 4 4";
        SVGPath arrow = createPath(arrowPath);
        return iconCanvas("neuron-menu-icon-direction", arrow);
    }

    /**
     * Створює значок шухлядки зі стрілкою, яка відповідає наступній дії над її станом.
     * Для закритої шухлядки стрілка спрямована всередину, а для відкритої — назовні.
     *
     * @param expanded {@code true}, якщо шухлядка зараз відкрита.
     * @return векторний значок відкриття або закриття шухлядки.
     */
    static Node createStructureDrawerToggleIcon(boolean expanded) {
        SVGPath drawer = createPath("M3 9h14M4 9v7h12V9");
        String arrowPath = expanded
                ? "M10 14V11m-2 2 2-2 2 2"
                : "M10 2v5m-2-2 2 2 2-2";
        SVGPath arrow = createPath(arrowPath);
        String stateClass = expanded
                ? "neuron-menu-icon-structure-drawer-expanded"
                : "neuron-menu-icon-structure-drawer-collapsed";
        return iconCanvas(stateClass, drawer, arrow);
    }

    /**
     * Створює дві зустрічні стрілки для перемикання типу нейрона.
     *
     * @return векторний значок перемикання типу.
     */
    static Node createToggleTypeIcon() {
        SVGPath arrows = createPath("M3 6h12M11 2l4 4-4 4M17 14H5M9 10l-4 4 4 4");
        return iconCanvas("neuron-menu-icon-toggle-type", arrows);
    }

    /**
     * Створює шестерню з вісьмома зубцями й прозорим центральним отвором для налаштувань нейрона.
     * Окремий центральний отвір та зубчастий зовнішній контур роблять значок схожим саме на шестерню,
     * а не на сонце з променями.
     *
     * @return векторний значок шестерні у фіксованому графічному контейнері.
     */
    static Node createSettingsIcon() {
        Path gear = new Path(
                new MoveTo(8.3, 1.4),
                new LineTo(11.7, 1.4),
                new LineTo(12.2, 3.15),
                new CubicCurveTo(12.68, 3.29, 13.12, 3.48, 13.51, 3.73),
                new LineTo(15.2, 2.76),
                new LineTo(17.24, 4.8),
                new LineTo(16.27, 6.49),
                new CubicCurveTo(16.52, 6.88, 16.71, 7.32, 16.85, 7.8),
                new LineTo(18.6, 8.3),
                new LineTo(18.6, 11.7),
                new LineTo(16.85, 12.2),
                new CubicCurveTo(16.71, 12.68, 16.52, 13.12, 16.27, 13.51),
                new LineTo(17.24, 15.2),
                new LineTo(15.2, 17.24),
                new LineTo(13.51, 16.27),
                new CubicCurveTo(13.12, 16.52, 12.68, 16.71, 12.2, 16.85),
                new LineTo(11.7, 18.6),
                new LineTo(8.3, 18.6),
                new LineTo(7.8, 16.85),
                new CubicCurveTo(7.32, 16.71, 6.88, 16.52, 6.49, 16.27),
                new LineTo(4.8, 17.24),
                new LineTo(2.76, 15.2),
                new LineTo(3.73, 13.51),
                new CubicCurveTo(3.48, 13.12, 3.29, 12.68, 3.15, 12.2),
                new LineTo(1.4, 11.7),
                new LineTo(1.4, 8.3),
                new LineTo(3.15, 7.8),
                new CubicCurveTo(3.29, 7.32, 3.48, 6.88, 3.73, 6.49),
                new LineTo(2.76, 4.8),
                new LineTo(4.8, 2.76),
                new LineTo(6.49, 3.73),
                new CubicCurveTo(6.88, 3.48, 7.32, 3.29, 7.8, 3.15),
                new ClosePath(),
                new MoveTo(10.0, 6.25),
                new ArcTo(3.75, 3.75, 0.0, 10.0, 13.75, false, true),
                new ArcTo(3.75, 3.75, 0.0, 10.0, 6.25, false, true),
                new ClosePath()
        );
        gear.setFillRule(FillRule.EVEN_ODD);
        gear.getStyleClass().add("neuron-menu-icon-fill");
        return iconCanvas("neuron-menu-icon-settings", gear);
    }

    /**
     * Створює значок закриття з двох діагональних ліній для видалення нейрона.
     *
     * @return векторний значок видалення нейрона.
     */
    static Node createDeleteIcon() {
        SVGPath cross = createPath("M5 5l10 10M15 5 5 15");
        return iconCanvas("neuron-menu-icon-delete", cross);
    }

    /**
     * Створює контурний SVG-значок і призначає CSS-клас кольору його лінії.
     *
     * @param content геометрія шляху у форматі SVG path.
     * @return графічний шлях із CSS-класом контуру.
     */
    private static SVGPath createPath(String content) {
        SVGPath path = new SVGPath();
        path.setContent(content);
        path.getStyleClass().add("neuron-menu-icon-stroke");
        return path;
    }

    /**
     * Розміщує графічні елементи на прозорому полотні незмінного розміру та робить його прозорим для миші.
     *
     * @param iconClass клас, що ідентифікує призначення значка для перевірок.
     * @param shapes векторні фігури, з яких складається значок.
     * @return полотно розміром {@value #ICON_SIZE} на {@value #ICON_SIZE} логічних пікселів.
     */
    private static Pane iconCanvas(String iconClass, Node... shapes) {
        Pane canvas = new Pane(shapes);
        canvas.getStyleClass().addAll("neuron-menu-icon", iconClass);
        canvas.setMinSize(ICON_SIZE, ICON_SIZE);
        canvas.setPrefSize(ICON_SIZE, ICON_SIZE);
        canvas.setMaxSize(ICON_SIZE, ICON_SIZE);
        canvas.setMouseTransparent(true);
        return canvas;
    }
}
