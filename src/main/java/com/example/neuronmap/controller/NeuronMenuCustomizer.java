package com.example.neuronmap.controller;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.view.TooltipFactory;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.Pane;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Налаштовує кнопки контекстного меню нейрона відповідно до потрібних дій та стану інтерфейсу.
 */
public final class NeuronMenuCustomizer {

    private final Node toolbarRoot;
    private final Supplier<String> selectedNeuronIdSupplier;
    private final Consumer<String> directionAction;
    private final LocalizationService localization;

    /**
     * Створює екземпляр NeuronMenuCustomizer та зберігає передані залежності, потрібні для його роботи.
     *
     * @param toolbarRoot кореневий вузол панелі інструментів, у кнопках якої налаштовують меню.
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
     * Створює екземпляр NeuronMenuCustomizer та зберігає передані залежності, потрібні для його роботи.
     *
     * @param toolbarRoot кореневий вузол панелі інструментів, у кнопках якої налаштовують меню.
     * @param selectedNeuronIdSupplier функція, що повертає ідентифікатор вибраного нейрона.
     * @param directionAction callback, який змінює напрямок портів нейрона.
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
     * Створює екземпляр NeuronMenuCustomizer та зберігає передані залежності, потрібні для його роботи.
     *
     * @param toolbarRoot кореневий вузол панелі інструментів, у кнопках якої налаштовують меню.
     * @param selectedNeuronIdSupplier функція, що повертає ідентифікатор вибраного нейрона.
     * @param directionAction callback, який змінює напрямок портів нейрона.
     * @param localization служба локалізації інтерфейсу.
     */
    public NeuronMenuCustomizer(
            Node toolbarRoot,
            Supplier<String> selectedNeuronIdSupplier,
            Consumer<String> directionAction,
            LocalizationService localization
    ) {
        if (toolbarRoot == null) {

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
     * Налаштовує вигляд і дію кнопки в контекстному меню нейрона.
     *
     * @param button кнопка інтерфейсу, яку потрібно налаштувати.
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
     * Створює кнопку, яка перемикає напрямок портів нейрона.
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
     * Прибирає з меню кнопку виходу з режиму видалення зв’язку, якщо її не потрібно показувати.
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
