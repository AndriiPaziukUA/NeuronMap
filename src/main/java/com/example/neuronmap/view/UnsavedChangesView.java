package com.example.neuronmap.view;

import com.example.neuronmap.i18n.LocalizationService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.text.TextAlignment;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.Objects;

/**
 * Displays a compact, centered confirmation for actions that could discard unsaved changes.
 * The dimmed overlay fills the menu, while the dialog card sizes itself to its localized content
 * and never expands beyond its preferred width or height.
 */
public final class UnsavedChangesView extends StackPane {

    private final LocalizationService localization;
    private final VBox card = new VBox(12.0);
    private final Label title = new Label();
    private final Label message = new Label();
    private final Button yesButton = new Button();
    private final Button noButton = new Button();
    private Runnable pendingSaveAction;
    private Runnable pendingDiscardAction;

    /**
     * Creates the confirmation overlay and initializes its localized controls.
     *
     * @param localization service providing translated dialog text.
     */
    public UnsavedChangesView(LocalizationService localization) {
        this.localization = Objects.requireNonNull(localization, "localization");

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
        yesButton.getStyleClass().add("main-menu-button");
        noButton.getStyleClass().add("main-menu-back-button");

        card.getChildren().addAll(title, message, buttons);
        getChildren().add(card);
        StackPane.setAlignment(card, Pos.CENTER);
        refreshTexts();
    }

    /**
     * Reports whether the confirmation overlay is visible.
     *
     * @return true when the dialog is showing.
     */
    public boolean isShowing() {
        return isVisible();
    }

    /**
     * Shows the confirmation and stores the actions to run after the user's decision.
     *
     * @param saveAction callback run when the user chooses to save.
     * @param discardAction callback run when the user chooses to discard changes and continue.
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
        requestFocus();
    }

    /**
     * Chooses the discard-and-continue action, for example when Escape is pressed on the dialog.
     * Has no effect when the dialog is already hidden.
     */
    public void discardAndContinue() {
        if (isShowing()) {
            runPendingAction(pendingDiscardAction);
        }
    }

    /** Hides the dialog and clears its pending callbacks without executing either callback. */
    public void hide() {
        setVisible(false);
        setManaged(false);
        yesButton.setOnAction(null);
        noButton.setOnAction(null);
        pendingSaveAction = null;
        pendingDiscardAction = null;
    }

    /** Refreshes all dialog labels using the currently active language. */
    public void refreshTexts() {
        title.setText(localization.text("menu.unsaved.title"));
        message.setText(localization.text("menu.unsaved.message"));
        yesButton.setText(localization.text("common.yes"));
        noButton.setText(localization.text("common.no"));
    }

    /**
     * Exposes the card's maximum height setting for layout regression tests.
     *
     * @return the maximum height, which is tied to the card's preferred height.
     */
    double cardMaxHeightForTest() {
        return card.getMaxHeight();
    }

    /**
     * Exposes the actual card width after layout for adaptive-layout regression tests.
     *
     * @return the rendered width of the confirmation card.
     */
    double cardWidthForTest() {
        return card.getWidth();
    }

    /**
     * Exposes the actual card height after layout for adaptive-layout regression tests.
     *
     * @return the rendered height of the confirmation card.
     */
    double cardHeightForTest() {
        return card.getHeight();
    }

    /**
     * Exposes the message alignment for layout regression tests.
     *
     * @return the message text alignment.
     */
    TextAlignment messageTextAlignmentForTest() {
        return message.getTextAlignment();
    }

    /**
     * Exposes the card maximum-width rule for adaptive-layout regression tests.
     *
     * @return the maximum width rule, tied to the content's preferred width.
     */
    double cardMaxWidthForTest() {
        return card.getMaxWidth();
    }

    /** Hides the overlay before running the selected pending action. */
    private void runPendingAction(Runnable action) {
        Runnable actionToRun = action;
        hide();
        if (actionToRun != null) {
            actionToRun.run();
        }
    }
}
