package com.example.neuronmap.view;

import com.example.neuronmap.i18n.LocalizationService;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.Pane;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Створює та оформлює кнопки контекстного меню нейрона й керує презентаційними елементами toolbar.
 */
public final class NeuronMenuView {

    private final Node toolbarRoot;
    private final Supplier<String> selectedNeuronIdSupplier;
    private final Consumer<String> directionAction;
    private final LocalizationService localization;

    /**
     * Створює подання меню з українською локалізацією та без дії зміни напрямку.
     *
     * @param toolbarRoot кореневий вузол панелі інструментів.
     */
    public NeuronMenuView(Node toolbarRoot) {
        this(
                toolbarRoot,
                () -> null,
                null,
                new LocalizationService(java.util.Locale.forLanguageTag("uk"))
        );
    }

    /**
     * Створює подання меню з callback-ом зміни напрямку та постачальником вибраного нейрона.
     *
     * @param toolbarRoot кореневий вузол панелі інструментів.
     * @param selectedNeuronIdSupplier функція, що повертає ідентифікатор нейрона для контекстного меню.
     * @param directionAction callback, який передає ідентифікатор нейрона контролеру для зміни напрямку.
     */
    public NeuronMenuView(
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
     * Створює подання меню з переданими залежностями та службою локалізації.
     *
     * @param toolbarRoot кореневий вузол панелі інструментів.
     * @param selectedNeuronIdSupplier функція, що повертає ідентифікатор нейрона для контекстного меню.
     * @param directionAction callback, який передає ідентифікатор нейрона контролеру для зміни напрямку.
     * @param localization служба локалізації інтерфейсу.
     */
    public NeuronMenuView(
            Node toolbarRoot,
            Supplier<String> selectedNeuronIdSupplier,
            Consumer<String> directionAction,
            LocalizationService localization
    ) {
        if (toolbarRoot == null) {
            throw new IllegalArgumentException("toolbarRoot must not be null");
        }
        this.toolbarRoot = toolbarRoot;
        this.selectedNeuronIdSupplier = selectedNeuronIdSupplier == null
                ? () -> null
                : selectedNeuronIdSupplier;
        this.directionAction = directionAction;
        this.localization = localization == null
                ? new LocalizationService(java.util.Locale.forLanguageTag("uk"))
                : localization;
    }

    /**
     * Застосовує оформлення та підказки до кнопки контекстного меню відповідно до її CSS-класів.
     *
     * @param button кнопка інтерфейсу, яку потрібно оформити.
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
     * Створює кнопку перемикання напрямку й передає дію контролеру через callback.
     *
     * @return оформлена кнопка або {@code null}, якщо дію напрямку не передано.
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
     * Прибирає з toolbar кнопку виходу з режиму видалення зв’язку, якщо вона присутня.
     */
    public void removeDeleteConnectionModeExitButton() {
        toolbarRoot.lookupAll(".delete-connection-exit-button").forEach(node -> {
            if (node.getParent() instanceof Pane pane) {
                pane.getChildren().remove(node);
            }
        });
    }
}
