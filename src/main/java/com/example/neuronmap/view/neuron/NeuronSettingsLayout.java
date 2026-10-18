package com.example.neuronmap.view.neuron;

import javafx.geometry.Insets;
import javafx.scene.Node;
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
        double preferredHeight = Math.max(panel.minHeight(panelWidth), panel.prefHeight(panelWidth));
        double panelHeight = Math.min(availableHeight, Math.min(MAX_PANEL_HEIGHT, preferredHeight));
        panel.resizeRelocate(
                Math.max(0.0, (owner.getWidth() - panelWidth) / 2.0),
                Math.max(0.0, (owner.getHeight() - panelHeight) / 2.0),
                panelWidth,
                panelHeight
        );
        panel.layout();
    }

    /** Обчислює мінімальну потрібну ширину за вмістом і фактичною шириною scrollbar. */
    private double preferredWidthForLayout() {
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
        return Math.max(preferredWidth, requiredPanelWidth);
    }

    /** Повертає фактичну або CSS-обчислену ширину вертикальної смуги прокручування. */
    private double verticalScrollbarWidth() {
        Node scrollbar = settingsScroll.lookup(".scroll-bar:vertical");
        if (!(scrollbar instanceof Region region)) {
            return 0.0;
        }
        double width = region.getWidth();
        if (width <= 0.0) {
            width = region.prefWidth(-1.0);
        }
        return Double.isFinite(width) ? Math.max(0.0, width) : 0.0;
    }
}
