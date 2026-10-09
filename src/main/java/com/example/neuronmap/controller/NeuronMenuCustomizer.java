package com.example.neuronmap.controller;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.view.TooltipFactory;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.Pane;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Визначає, як налаштовується меню нейрона перед його показом.
 */
public final class NeuronMenuCustomizer {

    private final Node toolbarRoot;
    private final Supplier<String> selectedNeuronIdSupplier;
    private final Consumer<String> directionAction;
    private final LocalizationService localization;

    /**
     * Повертає результат операції «нейрон меню».
     *
     * @param toolbarRoot значення, що визначає панель інструментів для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public NeuronMenuCustomizer(Node toolbarRoot) {
        this(
                toolbarRoot,
                () -> null,
                null,
                new LocalizationService(java.util.Locale.forLanguageTag("uk"))
        );
    }

    /**
     * Повертає результат операції «нейрон меню».
     *
     * @param toolbarRoot значення, що визначає панель інструментів для цієї операції.
     *
     * @param selectedNeuronIdSupplier значення, що визначає вибраний нейрон ідентифікатор для цієї операції.
     *
     * @param directionAction значення, що визначає напрямок для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public NeuronMenuCustomizer(
            Node toolbarRoot,
            Supplier<String> selectedNeuronIdSupplier,
            Consumer<String> directionAction
    ) {
        this(
                toolbarRoot,
                selectedNeuronIdSupplier,
                directionAction,
                new LocalizationService(java.util.Locale.forLanguageTag("uk"))
        );
    }

    /**
     * Повертає результат операції «нейрон меню».
     *
     * @param toolbarRoot значення, що визначає панель інструментів для цієї операції.
     *
     * @param selectedNeuronIdSupplier значення, що визначає вибраний нейрон ідентифікатор для цієї операції.
     *
     * @param directionAction значення, що визначає напрямок для цієї операції.
     *
     * @param localization значення, що визначає локалізація для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public NeuronMenuCustomizer(
            Node toolbarRoot,
            Supplier<String> selectedNeuronIdSupplier,
            Consumer<String> directionAction,
            LocalizationService localization
    ) {
        if (toolbarRoot == null) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalArgumentException("toolbarRoot must not be null");
        }

        this.toolbarRoot = toolbarRoot;
        this.selectedNeuronIdSupplier =
                selectedNeuronIdSupplier == null
                        ? () -> null
                        : selectedNeuronIdSupplier;
        this.directionAction = directionAction;
        this.localization = localization == null
                ? new LocalizationService(java.util.Locale.forLanguageTag("uk"))
                : localization;
    }

    /**
     * Виконує операцію «меню кнопка».
     *
     * @param button значення, що визначає кнопка для цієї операції.
     */
    public void customizeMenuButton(Button button) {
        if (button == null) {
            return;
        }

        if (button.getStyleClass().contains("neuron-menu-activate")) {
            button.setStyle(
                    "-fx-background-color: #f5c542; "
                            + "-fx-text-fill: black;"
            );
            TooltipFactory.install(
                    button,
                    localization.text("neuron.menu.fire")
            );
            return;
        }

        if (button.getStyleClass().contains("neuron-menu-delete")) {
            button.setStyle(
                    "-fx-background-color: #d64545; "
                            + "-fx-text-fill: white;"
            );
        }
    }

    /**
     * Створює обʼєкт із переданих даних «напрямок кнопка».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public Button createDirectionButton() {
        if (directionAction == null) {
            return null;
        }

        Button directionButton = new Button("↔");
        directionButton.getStyleClass().add("menu-button");
        directionButton.getStyleClass().add("neuron-menu-direction");
        directionButton.setMinSize(32.0, 32.0);
        directionButton.setPrefSize(32.0, 32.0);
        directionButton.setMaxSize(32.0, 32.0);
        directionButton.setStyle(
                "-fx-background-color: #4c5360; "
                        + "-fx-text-fill: white;"
        );
        directionButton.setOnAction(event -> {
            String neuronId = selectedNeuronIdSupplier.get();
            if (neuronId != null) {
                directionAction.accept(neuronId);
            }
            event.consume();
        });
        TooltipFactory.install(
                directionButton,
                localization.text("neuron.menu.direction")
        );
        return directionButton;
    }

    /**
     * Видаляє або скидає дані, повʼязані з «видалити звʼязок кнопка».
     */
    public void removeDeleteConnectionModeExitButton() {
        toolbarRoot.lookupAll(
                ".delete-connection-exit-button"
        ).forEach(node -> {
            if (node.getParent() instanceof Pane pane) {
                pane.getChildren().remove(node);
            }
        });
    }
}
