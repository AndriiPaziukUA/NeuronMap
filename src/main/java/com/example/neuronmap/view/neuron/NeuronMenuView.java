package com.example.neuronmap.view.neuron;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.view.support.TooltipFactory;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Створює та оформлює кнопки контекстного меню нейрона.
 */
public final class NeuronMenuView {

    private final Supplier<String> selectedNeuronIdSupplier;
    private final Consumer<String> directionAction;
    private final LocalizationService localization;

    /**
     * Створює подання меню з переданими залежностями та спільною службою локалізації.
     *
     * @param selectedNeuronIdSupplier функція, що повертає ідентифікатор нейрона для контекстного меню.
     * @param directionAction callback, який передає ідентифікатор нейрона контролеру для зміни напрямку; {@code null} приховує цю дію.
     * @param localization обов'язкова спільна служба локалізації інтерфейсу.
     * @throws NullPointerException якщо локалізацію не передано.
     */
    public NeuronMenuView(
            Supplier<String> selectedNeuronIdSupplier,
            Consumer<String> directionAction,
            LocalizationService localization
    ) {
        this.localization = Objects.requireNonNull(localization, "localization");
        this.selectedNeuronIdSupplier = selectedNeuronIdSupplier == null
                ? () -> null
                : selectedNeuronIdSupplier;
        this.directionAction = directionAction;
    }

    /**
     * Зберігає сумісність із наявними викликами, які раніше передавали корінь панелі інструментів.
     * Після вилучення застарілої кнопки поданню меню більше не потрібно знати про цей вузол.
     *
     * @param ignoredToolbarRoot кореневий вузол панелі зі старого контракту; більше не використовується.
     * @param selectedNeuronIdSupplier функція, що повертає ідентифікатор нейрона для контекстного меню.
     * @param directionAction callback, який передає ідентифікатор нейрона контролеру для зміни напрямку.
     * @param localization обов'язкова спільна служба локалізації інтерфейсу.
     * @throws NullPointerException якщо локалізацію не передано.
     */
    public NeuronMenuView(
            Node ignoredToolbarRoot,
            Supplier<String> selectedNeuronIdSupplier,
            Consumer<String> directionAction,
            LocalizationService localization
    ) {
        this(selectedNeuronIdSupplier, directionAction, localization);
    }

    /**
     * Застосовує локальні класи стилю до спеціальних кнопок і встановлює локалізовані підказки.
     * Візуальні стани та їхні кольори визначає stylesheet нейрона через псевдоклас :hover.
     *
     * @param button кнопка інтерфейсу, яку потрібно оформити.
     */
    public void customizeMenuButton(Button button) {
        if (button == null) {
            return;
        }

        if (button.getStyleClass().contains("neuron-menu-activate")) {
            TooltipFactory.install(button, localization.text("neuron.menu.fire"));
        }
    }

    /**
     * Створює кнопку перемикання напрямку з векторним значком і передає дію контролеру через callback.
     * Розмір кнопки й значка визначається їхніми окремими CSS-обмеженнями.
     *
     * @return оформлена кнопка або {@code null}, якщо дію напрямку не передано.
     */
    public Button createDirectionButton() {
        if (directionAction == null) {
            return null;
        }

        String tooltipText = localization.text("neuron.menu.direction");
        Button directionButton = new Button();
        directionButton.setGraphic(NeuronMenuIconFactory.createDirectionIcon());
        directionButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        directionButton.setAccessibleText(tooltipText);
        directionButton.getStyleClass().add("menu-button");
        directionButton.getStyleClass().add("neuron-menu-direction");
        directionButton.setOnAction(event -> {
            String neuronId = selectedNeuronIdSupplier.get();
            if (neuronId != null) {
                directionAction.accept(neuronId);
            }
            event.consume();
        });
        TooltipFactory.install(directionButton, tooltipText);
        return directionButton;
    }
}
