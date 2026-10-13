package com.example.neuronmap.controller;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.view.NeuronMenuView;
import javafx.scene.Node;
import javafx.scene.control.Button;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Делегувальний адаптер для сумісності з наявними місцями створення контекстного меню нейрона.
 *
 * @deprecated Створення й оформлення UI-контролів виконує {@link NeuronMenuView} у пакеті {@code view}.
 */
@Deprecated(since = "0.7.1", forRemoval = false)
public final class NeuronMenuCustomizer {

    private final NeuronMenuView menuView;

    /**
     * Створює адаптер із callback-ом зміни напрямку нейрона.
     *
     * @param toolbarRoot кореневий вузол панелі інструментів.
     * @param selectedNeuronIdSupplier функція, що повертає ідентифікатор вибраного нейрона.
     * @param directionAction callback, який передає ідентифікатор нейрона контролеру.
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
     * Створює адаптер подання меню з переданою службою локалізації.
     *
     * @param toolbarRoot кореневий вузол панелі інструментів.
     * @param selectedNeuronIdSupplier функція, що повертає ідентифікатор вибраного нейрона.
     * @param directionAction callback, який передає ідентифікатор нейрона контролеру.
     * @param localization служба локалізації інтерфейсу.
     */
    public NeuronMenuCustomizer(
            Node toolbarRoot,
            Supplier<String> selectedNeuronIdSupplier,
            Consumer<String> directionAction,
            LocalizationService localization
    ) {
        this.menuView = new NeuronMenuView(
                toolbarRoot,
                selectedNeuronIdSupplier,
                directionAction,
                localization
        );
    }

    /**
     * Передає поданню оформлення кнопки контекстного меню.
     *
     * @param button кнопка інтерфейсу, яку потрібно оформити.
     */
    public void customizeMenuButton(Button button) {
        menuView.customizeMenuButton(button);
    }

    /**
     * Передає поданню створення кнопки перемикання напрямку.
     *
     * @return оформлена кнопка або {@code null}, якщо дію напрямку не передано.
     */
    public Button createDirectionButton() {
        return menuView.createDirectionButton();
    }

    /**
     * Передає поданню прибирання кнопки виходу з режиму видалення зв’язку.
     */
    public void removeDeleteConnectionModeExitButton() {
        menuView.removeDeleteConnectionModeExitButton();
    }
}
