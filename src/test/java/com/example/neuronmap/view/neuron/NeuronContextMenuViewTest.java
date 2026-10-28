package com.example.neuronmap.view.neuron;

import com.example.neuronmap.i18n.LocalizationService;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє структуру контекстного меню нейрона, векторні значки, локалізацію підказок і дії кнопок.
 */
final class NeuronContextMenuViewTest {

    /** Запускає JavaFX toolkit перед виконанням тестів подання.
     *
     * @throws Exception якщо JavaFX toolkit не запускається вчасно.
     */
    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    /** Перевіряє порядок векторних значків і відсутність дії видалення зв’язку, коли зв’язків немає.
     *
     * @throws Exception якщо JavaFX-потік не виконав перевірку вчасно.
     */
    @Test
    void createsMenuWithVectorIconsWhenNoConnectionsExist() throws Exception {
        runOnFxThread(() -> {
            AtomicInteger customizedButtons = new AtomicInteger();
            NeuronContextMenuView view = new NeuronContextMenuView(
                    new LocalizationService(Locale.forLanguageTag("uk")),
                    _ -> customizedButtons.incrementAndGet(),
                    () -> null
            );

            VBox menu = view.createMenu(false, actions());
            assertTrue(menu.getStyleClass().contains("connection-menu"));
            assertEquals(1, menu.getChildren().size());
            HBox buttons = (HBox) menu.getChildren().getFirst();
            List<Button> menuButtons = buttons.getChildren().stream().map(Button.class::cast).toList();
            assertEquals(5, menuButtons.size());
            assertEquals(
                    List.of(
                            "neuron-menu-icon-fire",
                            "neuron-menu-icon-add-connection",
                            "neuron-menu-icon-toggle-type",
                            "neuron-menu-icon-settings",
                            "neuron-menu-icon-delete"
                    ),
                    iconNames(buttons)
            );
            assertTrue(menuButtons.getFirst().getStyleClass().contains("neuron-menu-activate"));
            assertTrue(menuButtons.getLast().getStyleClass().contains("neuron-menu-delete"));
            assertEquals("Імпульс", menuButtons.getFirst().getAccessibleText());
            assertEquals(5, customizedButtons.get());
        });
    }

    /** Перевіряє додавання графічних значків видалення зв’язку та зміни напрямку за відповідних умов.
     *
     * @throws Exception якщо JavaFX-потік не виконав перевірку вчасно.
     */
    @Test
    void includesOptionalVectorIconsWhenAvailable() throws Exception {
        runOnFxThread(() -> {
            LocalizationService localization = new LocalizationService(Locale.ENGLISH);
            NeuronMenuView neuronMenuView = new NeuronMenuView(() -> null, ignored -> { }, localization);
            NeuronContextMenuView view = new NeuronContextMenuView(
                    localization,
                    _ -> { },
                    neuronMenuView::createDirectionButton
            );

            HBox buttons = (HBox) view.createMenu(true, actions()).getChildren().getFirst();

            assertEquals(7, buttons.getChildren().size());
            assertEquals(
                    List.of(
                            "neuron-menu-icon-fire",
                            "neuron-menu-icon-add-connection",
                            "neuron-menu-icon-delete-connection",
                            "neuron-menu-icon-direction",
                            "neuron-menu-icon-toggle-type",
                            "neuron-menu-icon-settings",
                            "neuron-menu-icon-delete"
                    ),
                    iconNames(buttons)
            );
        });
    }

    /** Перевіряє, що внутрішній нейрон зберігає кнопки в поданні, але не показує їх користувачу.
     *
     * @throws Exception якщо JavaFX-потік не виконав перевірку вчасно.
     */
    @Test
    void hidesDirectionTypeAndSettingsForStructureMembers() throws Exception {
        runOnFxThread(() -> {
            LocalizationService localization = new LocalizationService(Locale.ENGLISH);
            NeuronMenuView neuronMenuView = new NeuronMenuView(() -> null, ignored -> { }, localization);
            NeuronContextMenuView view = new NeuronContextMenuView(
                    localization,
                    _ -> { },
                    neuronMenuView::createDirectionButton
            );

            HBox buttons = (HBox) view.createMenu(true, true, actions()).getChildren().getFirst();
            List<Button> restrictedButtons = buttons.getChildren().stream()
                    .map(Button.class::cast)
                    .filter(button -> button.getGraphic().getStyleClass().stream().anyMatch(styleClass ->
                            styleClass.equals("neuron-menu-icon-direction")
                                    || styleClass.equals("neuron-menu-icon-toggle-type")
                                    || styleClass.equals("neuron-menu-icon-settings")))
                    .toList();

            assertEquals(3, restrictedButtons.size(), "Усі три обмежені кнопки мають залишатися в поданні");
            assertTrue(restrictedButtons.stream().noneMatch(Button::isVisible));
            assertTrue(restrictedButtons.stream().noneMatch(Button::isManaged));
            assertEquals(4, buttons.getChildren().stream().filter(Node::isVisible).count(),
                    "Імпульс, додавання/видалення зв’язку та видалення нейрона мають залишитися доступними");
            assertTrue(buttons.getChildren().stream()
                    .filter(Node::isVisible)
                    .allMatch(Node::isManaged));
        });
    }

    /** Перевіряє, що підказки дій контекстного меню мають очікувані тексти в усіх підтримуваних локалях.
     *
     * @throws Exception якщо JavaFX-потік не виконав перевірку вчасно.
     */
    @Test
    void localizesMenuTooltipsInEverySupportedLocale() throws Exception {
        runOnFxThread(() -> {
            assertMenuTooltips(
                    Locale.ENGLISH,
                    List.of("Pulse", "Add connection", "Delete connection", "Change type", "Settings", "Delete")
            );
            assertMenuTooltips(
                    Locale.forLanguageTag("uk"),
                    List.of(
                            "Імпульс", "Додати зв'язок", "Видалити зв'язок",
                            "Змінити тип", "Налаштування", "Видалити"
                    )
            );
            assertMenuTooltips(
                    Locale.forLanguageTag("ru"),
                    List.of(
                            "Импульс", "Добавить связь", "Удалить связь",
                            "Изменить тип", "Настройки", "Удалить"
                    )
            );
        });
    }

    /** Перевіряє підказки меню, створеного для конкретної мови, у фактичному порядку кнопок.
     *
     * @param locale локаль, для якої перевіряються підказки.
     * @param expectedTooltips очікувані тексти підказок у порядку кнопок.
     */
    private static void assertMenuTooltips(Locale locale, List<String> expectedTooltips) {
        NeuronContextMenuView view = new NeuronContextMenuView(
                new LocalizationService(locale),
                _ -> { },
                () -> null
        );
        HBox buttons = (HBox) view.createMenu(true, actions()).getChildren().getFirst();
        List<Button> menuButtons = buttons.getChildren().stream().map(Button.class::cast).toList();
        assertEquals(expectedTooltips, menuButtons.stream()
                .map(button -> button.getTooltip().getText())
                .toList());
    }

    /** Перевіряє, що кожна кнопка має графічний значок замість текстового Unicode-символу.
     *
     * @param buttons ряд кнопок контекстного меню.
     * @return імена класів значків у тому самому порядку, що й кнопки.
     */
    private static List<String> iconNames(HBox buttons) {
        List<String> names = new ArrayList<>();
        for (Node node : buttons.getChildren()) {
            Button button = (Button) node;
            assertTrue(button.getText().isBlank(), "Графічний значок не повинен дублюватися текстом кнопки");
            assertNotNull(button.getGraphic(), "Кнопка меню повинна містити окремий графічний значок");
            names.add(button.getGraphic().getStyleClass().stream()
                    .filter(styleClass -> styleClass.startsWith("neuron-menu-icon-"))
                    .findFirst()
                    .orElseThrow());
        }
        return names;
    }

    /** Перевіряє, що всі кнопки передають керування відповідним діям контролера.
     *
     * @throws Exception якщо JavaFX-потік не виконав перевірку вчасно.
     */
    @Test
    void delegatesButtonActionsToCallbacks() throws Exception {
        runOnFxThread(() -> {
            List<String> actionsCalled = new ArrayList<>();
            NeuronContextMenuView view = new NeuronContextMenuView(
                    new LocalizationService(Locale.forLanguageTag("uk")),
                    _ -> { },
                    () -> null
            );

            NeuronContextMenuView.Actions testActions = new NeuronContextMenuView.Actions(
                    () -> actionsCalled.add("fire"),
                    () -> actionsCalled.add("add"),
                    () -> actionsCalled.add("settings"),
                    () -> actionsCalled.add("delete-connection"),
                    () -> actionsCalled.add("toggle-type"),
                    () -> actionsCalled.add("delete-neuron")
            );
            HBox buttons = (HBox) view.createMenu(true, testActions).getChildren().getFirst();
            buttons.getChildren().forEach(node -> ((Button) node).fire());

            assertEquals(
                    List.of("fire", "add", "delete-connection", "toggle-type", "settings", "delete-neuron"),
                    actionsCalled
            );
        });
    }

    /** Перевіряє, що інтерфейс дій відхиляє відсутні callback-и.
     *
     * @throws Exception якщо JavaFX-потік не виконав перевірку вчасно.
     */
    @Test
    void actionsRequireAllCallbacks() throws Exception {
        runOnFxThread(() -> org.junit.jupiter.api.Assertions.assertThrows(
                NullPointerException.class,
                () -> new NeuronContextMenuView.Actions(
                        () -> { }, () -> { }, () -> { }, null, () -> { }, () -> { }
                )
        ));
    }

    /** Виконує дію в потоці JavaFX і передає помилки до потоку JUnit. */
    private static void runOnFxThread(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Throwable[] failure = new Throwable[1];
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                failure[0] = throwable;
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        if (failure[0] != null) {
            throw new AssertionError("JavaFX test failed", failure[0]);
        }
    }

    /** Створює набір тестових дій, які не змінюють зовнішній стан. */
    private static NeuronContextMenuView.Actions actions() {
        return new NeuronContextMenuView.Actions(
                () -> { },
                () -> { },
                () -> { },
                () -> { },
                () -> { },
                () -> { }
        );
    }
}
