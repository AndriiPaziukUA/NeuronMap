package com.example.neuronmap.view;

import com.example.neuronmap.i18n.LocalizationService;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Створює структуру та налаштовує елементи контекстного меню нейрона.
 */
public final class NeuronContextMenuView {

    private final LocalizationService localization;
    private final Consumer<Button> buttonCustomizer;
    private final Supplier<Button> directionButtonSupplier;

    /**
     * Створює подання контекстного меню з локалізацією та правилами оформлення кнопок.
     *
     * @param localization служба локалізації підписів і підказок меню.
     * @param buttonCustomizer дія оформлення кнопок меню.
     * @param directionButtonSupplier постачальник кнопки зміни напрямку нейрона.
     */
    public NeuronContextMenuView(
            LocalizationService localization,
            Consumer<Button> buttonCustomizer,
            Supplier<Button> directionButtonSupplier
    ) {
        this.localization = Objects.requireNonNull(localization, "localization");
        this.buttonCustomizer = Objects.requireNonNull(buttonCustomizer, "buttonCustomizer");
        this.directionButtonSupplier = Objects.requireNonNull(
                directionButtonSupplier,
                "directionButtonSupplier"
        );
    }

    /**
     * Створює меню нейрона з потрібним набором дій та під’єднаними обробниками.
     *
     * @param hasConnections ознака наявності зв’язків у нейрона.
     * @param actions зворотні виклики дій контекстного меню.
     * @return кореневий контейнер контекстного меню.
     */
    public VBox createMenu(boolean hasConnections, Actions actions) {
        Objects.requireNonNull(actions, "actions");

        VBox menu = new VBox(5.0);
        menu.getStyleClass().add("connection-menu");

        HBox buttons = new HBox(4.0);
        buttons.setAlignment(Pos.CENTER);

        Button fire = createButton(
                "⚡",
                localization.text("neuron.menu.fire"),
                "neuron-menu-activate"
        );
        Button addConnection = createButton(
                "↗",
                localization.text("neuron.menu.add_connection"),
                ""
        );
        Button settings = createButton(
                "⚙",
                localization.text("neuron.menu.settings"),
                ""
        );
        Button deleteConnection = hasConnections
                ? createButton(
                        "⛓",
                        localization.text("neuron.menu.delete_connection"),
                        ""
                )
                : null;
        Button direction = directionButtonSupplier.get();
        Button toggleType = createButton(
                "⇄",
                localization.text("neuron.menu.toggle_type"),
                ""
        );
        Button deleteNeuron = createButton(
                "✕",
                localization.text("neuron.menu.delete"),
                "neuron-menu-delete"
        );

        fire.setOnAction(event -> {
            actions.fire().run();
            event.consume();
        });
        addConnection.setOnAction(event -> {
            actions.addConnection().run();
            event.consume();
        });
        settings.setOnAction(event -> {
            actions.settings().run();
            event.consume();
        });
        if (deleteConnection != null) {
            deleteConnection.setOnAction(event -> {
                actions.deleteConnection().run();
                event.consume();
            });
        }
        toggleType.setOnAction(event -> {
            actions.toggleType().run();
            event.consume();
        });
        deleteNeuron.setOnAction(event -> {
            actions.deleteNeuron().run();
            event.consume();
        });

        buttons.getChildren().addAll(fire, addConnection, settings);
        if (deleteConnection != null) {
            buttons.getChildren().add(deleteConnection);
        }
        if (direction != null) {
            buttons.getChildren().add(direction);
        }
        buttons.getChildren().addAll(toggleType, deleteNeuron);
        menu.getChildren().add(buttons);
        return menu;
    }

    /**
     * Створює кнопку меню, встановлює підказку, CSS-класи та передає кнопку на додаткове оформлення.
     *
     * @param text символ або текст кнопки.
     * @param tooltipText текст підказки.
     * @param styleClass додатковий CSS-клас або порожній рядок.
     * @return готова до показу кнопка меню.
     */
    private Button createButton(String text, String tooltipText, String styleClass) {
        Button button = new Button(text);
        button.getStyleClass().add("menu-button");
        if (styleClass != null && !styleClass.isBlank()) {
            button.getStyleClass().add(styleClass);
        }
        button.setTooltip(new javafx.scene.control.Tooltip(tooltipText));
        buttonCustomizer.accept(button);
        return button;
    }

    /**
     * Містить дії, які контролер виконує у відповідь на вибір користувача в контекстному меню.
     *
     * @param fire дія активації імпульсу нейрона.
     * @param addConnection дія переходу до створення зв’язку.
     * @param settings дія відкриття налаштувань нейрона.
     * @param deleteConnection дія переходу до видалення зв’язку.
     * @param toggleType дія зміни типу нейрона.
     * @param deleteNeuron дія видалення нейрона.
     */
    public record Actions(
            Runnable fire,
            Runnable addConnection,
            Runnable settings,
            Runnable deleteConnection,
            Runnable toggleType,
            Runnable deleteNeuron
    ) {
        /**
         * Перевіряє, що для кожної дії меню передано зворотний виклик.
         *
         * @param fire дія активації імпульсу нейрона.
         * @param addConnection дія переходу до створення зв’язку.
         * @param settings дія відкриття налаштувань нейрона.
         * @param deleteConnection дія переходу до видалення зв’язку.
         * @param toggleType дія зміни типу нейрона.
         * @param deleteNeuron дія видалення нейрона.
         */
        public Actions {
            Objects.requireNonNull(fire, "fire");
            Objects.requireNonNull(addConnection, "addConnection");
            Objects.requireNonNull(settings, "settings");
            Objects.requireNonNull(deleteConnection, "deleteConnection");
            Objects.requireNonNull(toggleType, "toggleType");
            Objects.requireNonNull(deleteNeuron, "deleteNeuron");
        }
    }
}
