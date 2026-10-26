package com.example.neuronmap.view.neuron;

import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Labeled;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.Objects;

/** Відповідає за вимірювання вмісту та адаптивне компонування модальної накладки налаштувань. */
final class NeuronSettingsLayout {

    static final double MAX_PANEL_WIDTH = 920.0;
    static final double MAX_PANEL_HEIGHT = 430.0;
    static final double MAX_VIEWPORT_HEIGHT = 260.0;
    static final double PANEL_EDGE_SPACING = 32.0;

    private final StackPane owner;
    private final VBox panel;
    private final VBox settingsRows;
    private final ScrollPane settingsScroll;

    /** Природна ширина картки, зафіксована після первинного CSS/layout проходу. */
    private double cachedPreferredWidth = Double.NaN;

    /** Природна висота картки для конкретної фактичної ширини. */
    private double cachedPreferredHeight = Double.NaN;

    /** Ширина, для якої було виміряно кешовану природну висоту. */
    private double cachedPreferredHeightWidth = Double.NaN;

    /** Тексти, які впливають на природний розмір картки, без вибраного значення ComboBox. */
    private String cachedLayoutTextSignature;

    /**
     * Створює компонувальник для вузлів одного подання налаштувань.
     *
     * @param owner корінь накладки, розмір якого визначає доступний простір.
     * @param panel картка налаштувань, яку потрібно компонувати.
     * @param settingsRows контейнер рядків налаштувань.
     * @param settingsScroll прокручувана область рядків.
     * @throws NullPointerException якщо обов'язковий вузол не задано.
     */
    NeuronSettingsLayout(
            StackPane owner,
            VBox panel,
            VBox settingsRows,
            ScrollPane settingsScroll
    ) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.panel = Objects.requireNonNull(panel, "panel");
        this.settingsRows = Objects.requireNonNull(settingsRows, "settingsRows");
        this.settingsScroll = Objects.requireNonNull(settingsScroll, "settingsScroll");
        // Зміна реального вмісту скидає природні розміри; зміна псевдостанів ComboBox — ні.
        this.settingsRows.getChildren().addListener((ListChangeListener<Node>) _ -> invalidatePreferredSize());
    }

    /** Обчислює природну висоту списку, обмежуючи viewport, а не рядки налаштувань. */
    void updateViewportHeight() {
        Insets insets = settingsRows.getInsets();
        double scrollbarWidth = verticalScrollbarWidth();
        double rowWidth = settingsScroll.getWidth() > 0.0
                ? Math.max(0.0, settingsScroll.getWidth() - scrollbarWidth)
                : Math.max(0.0, MAX_PANEL_WIDTH - panel.getInsets().getLeft()
                        - panel.getInsets().getRight() - scrollbarWidth);
        double contentHeight = insets.getTop() + insets.getBottom();
        int visibleRows = 0;
        for (Node row : settingsRows.getChildren()) {
            if (!row.isManaged()) {
                continue;
            }
            double rowHeight = row instanceof Region region
                    ? Math.max(region.minHeight(rowWidth), region.prefHeight(rowWidth))
                    : row.getLayoutBounds().getHeight();
            if (Double.isFinite(rowHeight) && rowHeight > 0.0) {
                contentHeight += rowHeight;
            }
            visibleRows++;
        }
        if (visibleRows > 1) {
            contentHeight += (visibleRows - 1) * settingsRows.getSpacing();
        }
        contentHeight = Math.max(contentHeight, settingsRows.prefHeight(rowWidth));
        settingsScroll.setPrefViewportHeight(Math.min(MAX_VIEWPORT_HEIGHT, contentHeight));
    }

    /** Розміщує картку за природними розмірами з урахуванням меж сцени та мінімальних відступів. */
    void layoutChildren() {
        if (owner.getWidth() <= 0.0 || owner.getHeight() <= 0.0) {
            return;
        }

        double availableWidth = Math.max(0.0, owner.getWidth() - PANEL_EDGE_SPACING);
        double availableHeight = Math.max(0.0, owner.getHeight() - PANEL_EDGE_SPACING);
        double preferredWidth = preferredWidthForLayout();
        double panelWidth = Math.min(availableWidth, Math.min(MAX_PANEL_WIDTH, preferredWidth));
        double preferredHeight = preferredHeightForLayout(panelWidth);
        double panelHeight = Math.min(availableHeight, Math.min(MAX_PANEL_HEIGHT, preferredHeight));
        panel.resizeRelocate(
                Math.max(0.0, (owner.getWidth() - panelWidth) / 2.0),
                Math.max(0.0, (owner.getHeight() - panelHeight) / 2.0),
                panelWidth,
                panelHeight
        );
        panel.layout();
    }

    /**
     * Повертає природну ширину картки, вимірюючи її один раз на показ форми.
     * Відкриття popup не є зміною вмісту форми, тому не має запускати нове природне вимірювання.
     *
     * @return природна ширина картки для поточної локалізації.
     */
    private double preferredWidthForLayout() {
        String currentTextSignature = layoutTextSignature();
        if (!Objects.equals(cachedLayoutTextSignature, currentTextSignature)) {
            invalidatePreferredSize();
            cachedLayoutTextSignature = currentTextSignature;
        }
        if (Double.isFinite(cachedPreferredWidth)) {
            return cachedPreferredWidth;
        }

        double preferredWidth = Math.max(panel.minWidth(-1.0), panel.prefWidth(-1.0));
        Insets panelInsets = panel.getInsets();
        Insets rowInsets = settingsRows.getInsets();
        Insets scrollInsets = settingsScroll.getInsets();
        double rowsWidth = Math.max(settingsRows.minWidth(-1.0), settingsRows.prefWidth(-1.0));
        double requiredRowsWidth = rowsWidth
                + rowInsets.getLeft() + rowInsets.getRight()
                + scrollInsets.getLeft() + scrollInsets.getRight()
                + verticalScrollbarWidth();
        double requiredPanelWidth = requiredRowsWidth + panelInsets.getLeft() + panelInsets.getRight();
        cachedPreferredWidth = Math.max(preferredWidth, requiredPanelWidth);
        return cachedPreferredWidth;
    }

    /**
     * Вимірює природну висоту для ширини картки, повторно використовуючи результат між UI-станами.
     * Перерахунок виконується лише тоді, коли змінюється доступна ширина або локалізація.
     *
     * @param panelWidth фактична ширина картки після обмеження розміром вікна.
     * @return природна висота картки для переданої ширини.
     */
    private double preferredHeightForLayout(double panelWidth) {
        if (Double.isFinite(cachedPreferredHeight)
                && Math.abs(cachedPreferredHeightWidth - panelWidth) < 0.5) {
            return cachedPreferredHeight;
        }
        cachedPreferredHeightWidth = panelWidth;
        cachedPreferredHeight = Math.max(panel.minHeight(panelWidth), panel.prefHeight(panelWidth));
        return cachedPreferredHeight;
    }

    /**
     * Формує сигнатуру локалізованих текстів, які можуть змінити природний розмір картки.
     * Текст вибраного значення ComboBox навмисно не входить до сигнатури: його зміна не повинна
     * змінювати геометрію під час взаємодії зі списком.
     *
     * @return детермінована сигнатура заголовка, підписів і кнопок.
     */
    private String layoutTextSignature() {
        StringBuilder signature = new StringBuilder();
        appendLayoutTexts(panel, signature);
        return signature.toString();
    }

    /** Додає до сигнатури лише тексти вузлів, які формують природну ширину картки. */
    private void appendLayoutTexts(Node node, StringBuilder signature) {
        if (node instanceof Labeled labeled
                && (node.getStyleClass().contains("neuron-settings-title")
                || node.getStyleClass().contains("neuron-settings-label")
                || node.getStyleClass().contains("neuron-settings-button"))) {
            String text = Objects.toString(labeled.getText(), "");
            signature.append(node.getStyleClass()).append(':')
                    .append(text.length()).append(':').append(text).append('|');
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                appendLayoutTexts(child, signature);
            }
        }
    }

    /**
     * Скидає вимірювання після зміни текстів форми; нові розміри буде обчислено під час layout.
     * Метод доступний іншим класам пакета, які змінюють вміст форми.
     */
    void invalidatePreferredSize() {
        cachedPreferredWidth = Double.NaN;
        cachedPreferredHeight = Double.NaN;
        cachedPreferredHeightWidth = Double.NaN;
    }

    /**
     * Читає CSS-обчислену ширину вертикальної смуги. Початкове застосування CSS виконує подання
     * перед вимірюванням; тут не можна викликати applyCss(), оскільки метод також досягається
     * з layout/width listeners.
     *
     * @return ширина смуги після застосування CSS або нуль, якщо вузол ще недоступний.
     */
    private double verticalScrollbarWidth() {
        if (settingsScroll.getScene() == null) {
            return 0.0;
        }
        Node scrollbar = settingsScroll.lookup(".scroll-bar:vertical");
        if (!(scrollbar instanceof Region region)) {
            return 0.0;
        }
        double preferredWidth = region.prefWidth(-1.0);
        if (Double.isFinite(preferredWidth) && preferredWidth > 0.0) {
            return preferredWidth;
        }
        double actualWidth = region.getWidth();
        return Double.isFinite(actualWidth) ? Math.max(0.0, actualWidth) : 0.0;
    }
}
