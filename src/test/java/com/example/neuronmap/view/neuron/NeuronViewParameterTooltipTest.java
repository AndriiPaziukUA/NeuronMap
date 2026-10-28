package com.example.neuronmap.view.neuron;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronParameters;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє вміст, локалізацію та ізоляцію підказок параметрів нейрона. */
final class NeuronViewParameterTooltipTest {

    /** Перевіряє, що підказка показує всі чотири параметри з локалізованими назвами. */
    @Test
    void showsOnlyLocalizedNeuronSettings() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            NeuronView view = createView();
            view.configureParameterTooltipLocalization(new LocalizationService(Locale.forLanguageTag("uk")));

            assertEquals(
                    "Сила сигналу: 7\nПоріг активації: 3\nАктивні такти: 5\nЗатримка: 1",
                    view.parameterTooltipTextForTest()
            );
            assertTrue(view.isParameterTooltipInstalledForTest());
            view.disposeParameterTooltip();
        });
    }

    /** Перевіряє, що підказка прибирається під час вибору нейрона й повертається після зняття вибору. */
    @Test
    void hidesTooltipForSelectedNeuronAndRestoresItAfterDeselection() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            NeuronView view = createView();
            LocalizationService localization = new LocalizationService(Locale.ENGLISH);
            view.configureParameterTooltipLocalization(localization);

            view.refreshVisuals(true);
            assertFalse(view.isParameterTooltipInstalledForTest());

            view.refreshVisuals(false);
            assertTrue(view.isParameterTooltipInstalledForTest());
            view.disposeParameterTooltip();
        });
    }

    /** Перевіряє актуальність усіх параметрів у підказці після їх зміни в моделі нейрона. */
    @Test
    void refreshesTooltipWhenNeuronParametersChange() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            NeuronView view = createView();
            view.configureParameterTooltipLocalization(new LocalizationService(Locale.ENGLISH));
            view.model().setParameters(new NeuronParameters(9, 4, 2, 3));
            view.refreshVisuals(false);

            assertEquals(
                    "Signal strength: 9\nActivation threshold: 4\nActive ticks: 2\nDelay: 3",
                    view.parameterTooltipTextForTest()
            );
            view.disposeParameterTooltip();
        });
    }

    /** Перевіряє, що підказка прив'язана до видимих частин нейрона, а не до контейнера вкладеної шухлядки. */
    @Test
    void installsParameterTooltipOnVisibleNeuronPartsOnly() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            NeuronView view = createView();
            view.configureParameterTooltipLocalization(new LocalizationService(Locale.ENGLISH));

            List<javafx.scene.Node> targets = view.parameterTooltipTargetsForTest();
            assertEquals(3, targets.size(), "Підказка має належати тілу та двом портам нейрона");
            assertTrue(targets.contains(view.getChildren().getFirst()),
                    "Тіло нейрона повинно показувати його параметри");
            assertTrue(view.getChildren().containsAll(targets),
                    "Цілі підказки мають бути видимими дочірніми вузлами картки");
            assertFalse(targets.contains(view),
                    "Контейнер не повинен утримувати підказку, поки курсор перебуває на шухлядці структури");

            view.disposeParameterTooltip();
        });
    }

    /**
     * Перевіряє, що учасник реагує на наведення, але його події не активують зовнішню структуру.
     * Це також запобігає повторному запуску підказки параметрів контейнера поверх підказки учасника.
     */
    @Test
    void drawerMouseEventsDoNotTriggerOuterStructureTooltipOrClickHandlers() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            NeuronView structure = createNamedView("tooltip-structure", NeuronType.STRUCTURE, "Структура");
            NeuronView member = createNamedView("tooltip-member", NeuronType.EXCITATORY, "Учасник");
            structure.setStructureMembers(List.of(member));
            structure.toggleStructureDrawer();

            StackPane root = new StackPane(structure);
            new Scene(root, 400.0, 300.0);
            root.applyCss();
            root.layout();

            NeuronStructureDrawerView drawer = structure.getChildren().stream()
                    .filter(NeuronStructureDrawerView.class::isInstance)
                    .map(NeuronStructureDrawerView.class::cast)
                    .findFirst()
                    .orElseThrow();
            Rectangle memberBody = (Rectangle) member.getChildren().getFirst();
            AtomicInteger outerMouseMoves = new AtomicInteger();
            AtomicInteger outerMouseClicks = new AtomicInteger();
            structure.addEventHandler(MouseEvent.MOUSE_MOVED, _ -> outerMouseMoves.incrementAndGet());
            structure.addEventHandler(MouseEvent.MOUSE_CLICKED, _ -> outerMouseClicks.incrementAndGet());

            try {
                Event.fireEvent(memberBody, mouseEvent(MouseEvent.MOUSE_MOVED, MouseButton.NONE, 0));

                assertTrue(member.isNameLabelVisibleForTest(),
                        "Вкладений нейрон має одразу реагувати на наведення миші");
                assertFalse(structure.isNameLabelVisibleForTest(),
                        "Наведення на учасника не повинно показувати назву зовнішньої структури");
                assertEquals(0, outerMouseMoves.get(),
                        "Подія переміщення миші з учасника не повинна спливати до структури-контейнера");

                Event.fireEvent(drawer, mouseEvent(MouseEvent.MOUSE_CLICKED, MouseButton.PRIMARY, 1));
                Event.fireEvent(memberBody, mouseEvent(MouseEvent.MOUSE_CLICKED, MouseButton.PRIMARY, 1));
                assertEquals(0, outerMouseClicks.get(),
                        "Клацання по шухлядці або її учаснику не повинно активувати контейнер");
            } finally {
                structure.disposeParameterTooltip();
                member.disposeParameterTooltip();
            }
        });
    }

    /** Створює подання з контрольованими параметрами для ізольованої перевірки тексту підказки. */
    private static NeuronView createView() {
        Neuron neuron = new Neuron(
                "tooltip-neuron",
                NeuronType.EXCITATORY,
                0,
                new NeuronParameters(7, 3, 5)
        );
        return new NeuronView(new NeuronPresentation(neuron, 10.0, 20.0, 0.0));
    }

    /** Створює назване подання заданого типу для регресійної перевірки подій шухлядки. */
    private static NeuronView createNamedView(String id, NeuronType type, String name) {
        Neuron neuron = new Neuron(id, type, 0, new NeuronParameters(7, 3, 5));
        neuron.setName(name);
        return new NeuronView(new NeuronPresentation(neuron, 10.0, 20.0, 0.0));
    }

    /** Створює синтетичну подію миші для тестування спливання без залежності від фізичного курсора. */
    private static MouseEvent mouseEvent(EventType<MouseEvent> type, MouseButton button, int clickCount) {
        boolean primaryButtonDown = button == MouseButton.PRIMARY && type == MouseEvent.MOUSE_PRESSED;
        return new MouseEvent(
                type,
                10.0, 10.0, 10.0, 10.0,
                button, clickCount,
                false, false, false, false,
                primaryButtonDown, false, false,
                false, false, true, null
        );
    }
}
