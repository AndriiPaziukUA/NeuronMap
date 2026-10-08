package com.example.neuronmap.view;

import com.example.neuronmap.i18n.LocalizationService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.Objects;

/** In-window confirmation overlay for pending menu edits. */
public final class UnsavedChangesView extends StackPane {

    private final LocalizationService localization;
    private final Label title = new Label();
    private final Label message = new Label();
    private final Button yesButton = new Button();
    private final Button noButton = new Button();

    public UnsavedChangesView(LocalizationService localization) {
        this.localization = Objects.requireNonNull(localization, "localization");

        setManaged(false);
        setVisible(false);
        setPickOnBounds(true);
        getStyleClass().add("unsaved-overlay");

        VBox card = new VBox(12.0);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(22.0));
        card.setMaxWidth(420.0);
        card.getStyleClass().add("unsaved-card");

        title.getStyleClass().add("unsaved-title");
        message.getStyleClass().add("unsaved-message");
        message.setWrapText(true);
        message.setMaxWidth(360.0);

        HBox buttons = new HBox(10.0, yesButton, noButton);
        buttons.setAlignment(Pos.CENTER);
        yesButton.getStyleClass().add("main-menu-button");
        noButton.getStyleClass().add("main-menu-back-button");

        card.getChildren().addAll(title, message, buttons);
        getChildren().add(card);
        StackPane.setAlignment(card, Pos.CENTER);
        refreshTexts();
    }

    public boolean isShowing() {
        return isVisible();
    }

    public void show(
            Runnable saveAction,
            Runnable discardAction
    ) {
        Objects.requireNonNull(saveAction, "saveAction");
        Objects.requireNonNull(discardAction, "discardAction");

        yesButton.setOnAction(event -> {
            hide();
            saveAction.run();
            event.consume();
        });
        noButton.setOnAction(event -> {
            hide();
            discardAction.run();
            event.consume();
        });

        refreshTexts();
        setManaged(true);
        setVisible(true);
        requestFocus();
    }

    public void hide() {
        setVisible(false);
        setManaged(false);
        yesButton.setOnAction(null);
        noButton.setOnAction(null);
    }

    public void refreshTexts() {
        title.setText(localization.text("menu.unsaved.title"));
        message.setText(localization.text("menu.unsaved.message"));
        yesButton.setText(localization.text("common.yes"));
        noButton.setText(localization.text("common.no"));
    }
}
