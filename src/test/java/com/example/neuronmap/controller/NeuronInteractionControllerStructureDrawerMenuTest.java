package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.coordinator.MapPresentationCoordinator;
import com.example.neuronmap.coordinator.NeuronSettingsOverlayCoordinator;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.service.ConnectionService;
import com.example.neuronmap.service.GroupService;
import com.example.neuronmap.service.NeuronClipboardService;
import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.service.StructureService;
import com.example.neuronmap.simulation.SimulationService;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import com.example.neuronmap.view.connection.ConnectionView;
import com.example.neuronmap.view.editor.NeuronLayerOrderManager;
import com.example.neuronmap.view.editor.WorkspaceView;
import com.example.neuronmap.view.menu.MainMenuView;
import com.example.neuronmap.view.neuron.NeuronMenuView;
import com.example.neuronmap.view.neuron.NeuronView;
import com.example.neuronmap.view.neuron.RotationHandleView;
import com.example.neuronmap.view.simulation.SimulationStepPresenter;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє керування шухлядкою структури через меню й Enter, а також запуск імпульсу з фокусованого нейрона. */
final class NeuronInteractionControllerStructureDrawerMenuTest {

    /** Перевіряє відкриття й закриття шухлядки через меню з одночасним обмеженням обертання. */
    @Test
    void structureMenuTogglesDrawerWithoutPulseOrRotationHandle() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture fixture = createFixture();
            try {
                int initialSimulationActivityEvents = fixture.simulationActivityEvents().get();
                var structure = fixture.neuronService().create(NeuronType.STRUCTURE, 260.0, 180.0);
                fixture.controller().loadViews();
                NeuronView structureView = fixture.neuronViews().get(structure.id());
                RotationHandleView rotationHandle = fixture.rotationHandles().get(structure.id());
                assertNotNull(structureView);
                assertNotNull(rotationHandle);

                fireClick(structureView, MouseButton.SECONDARY);
                Button drawerButton = findDrawerButton(fixture.workspace());
                assertEquals("Open", drawerButton.getTooltip().getText());
                drawerButton.fire();

                assertTrue(fixture.structureService().find(structure.id()).orElseThrow().isExpanded());
                assertTrue(structureView.isStructureDrawerExpanded());
                assertTrue(drawerButton.getGraphic().getStyleClass()
                        .contains("neuron-menu-icon-structure-drawer-expanded"));
                assertEquals("Close", drawerButton.getTooltip().getText());
                assertFalse(rotationHandle.isVisible());
                assertEquals(EditorState.Mode.IDLE, fixture.state().mode(),
                        "Кнопка структури не повинна запускати режим обертання");
                assertEquals(initialSimulationActivityEvents, fixture.simulationActivityEvents().get(),
                        "Кнопка структури не повинна запускати симуляцію");

                fireClick(structureView, MouseButton.PRIMARY);
                assertFalse(rotationHandle.isVisible(),
                        "Клацання по структурі з відкритою шухлядкою не повинно показувати ручку");

                fireClick(structureView, MouseButton.SECONDARY);
                drawerButton = findDrawerButton(fixture.workspace());
                assertEquals("Close", drawerButton.getTooltip().getText());
                drawerButton.fire();

                assertFalse(fixture.structureService().find(structure.id()).orElseThrow().isExpanded());
                assertFalse(structureView.isStructureDrawerExpanded());
                assertTrue(drawerButton.getGraphic().getStyleClass()
                        .contains("neuron-menu-icon-structure-drawer-collapsed"));
            } finally {
                dispose(fixture);
            }
        });
    }

    /** Перевіряє запуск імпульсу для нейрона й обидва напрями перемикання шухлядки клавішею Enter. */
    @Test
    void enterActivatesFocusedNeuronAndTogglesFocusedStructure() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture fixture = createFixture();
            try {
                var neuron = fixture.neuronService().create(NeuronType.EXCITATORY, 220.0, 180.0);
                var structure = fixture.neuronService().create(NeuronType.STRUCTURE, 420.0, 180.0);
                fixture.controller().loadViews();
                NeuronView neuronView = fixture.neuronViews().get(neuron.id());
                NeuronView structureView = fixture.neuronViews().get(structure.id());
                assertNotNull(neuronView);
                assertNotNull(structureView);
                assertTrue(neuronView.isFocusTraversable());
                assertTrue(structureView.isFocusTraversable());

                int initialActivityEvents = fixture.simulationActivityEvents().get();
                fireClick(neuronView, MouseButton.PRIMARY);
                assertTrue(fireEnter(neuronView), "Enter має зупинити поширення після обробки нейрона");
                assertTrue(fixture.simulationActivityEvents().get() > initialActivityEvents,
                        "Enter на звичайному нейроні має запустити симуляційний імпульс");

                int activityEventsAfterPulse = fixture.simulationActivityEvents().get();
                fireClick(structureView, MouseButton.PRIMARY);
                assertTrue(fireEnter(structureView), "Enter має зупинити поширення після перемикання шухлядки");
                assertTrue(fixture.structureService().find(structure.id()).orElseThrow().isExpanded());
                assertTrue(structureView.isStructureDrawerExpanded());

                assertTrue(fireEnter(structureView), "Enter має зупинити поширення після перемикання шухлядки");
                assertFalse(fixture.structureService().find(structure.id()).orElseThrow().isExpanded());
                assertFalse(structureView.isStructureDrawerExpanded());
                assertEquals(activityEventsAfterPulse, fixture.simulationActivityEvents().get(),
                        "Перемикання шухлядки не повинно повторно запускати симуляцію");
            } finally {
                dispose(fixture);
            }
        });
    }

    /** Створює мінімальний граф редактора з реальним координатором подань та контролером взаємодії. */
    private static Fixture createFixture() {
        NeuronMapModel model = new NeuronMapModel();
        model.createNeuron(NeuronType.EXCITATORY, 100.0, 80.0);
        NeuronService neuronService = new NeuronService(model);
        StructureService structureService = new StructureService(model);
        GroupService groupService = new GroupService(model);
        ConnectionService connectionService = new ConnectionService(model);
        EditorState state = new EditorState(1.0, 0.0, 0.0);
        LocalizationService localization = new LocalizationService(Locale.ENGLISH);
        WorkspaceView workspace = new WorkspaceView();
        Map<String, NeuronView> neuronViews = new HashMap<>();
        Map<String, ConnectionView> connectionViews = new HashMap<>();
        Map<String, RotationHandleView> rotationHandles = new HashMap<>();

        SelectionController selectionController = new SelectionController(
                groupService, state, () -> { }, () -> { }, ignored -> { }, localization);
        SimulationStepPresenter stepPresenter = new SimulationStepPresenter(
                workspace, neuronViews, () -> { }, new PulseAnimationController(workspace));
        AtomicInteger simulationActivityEvents = new AtomicInteger();
        SimulationController simulation = new SimulationController(
                neuronService, new SimulationService(), stepPresenter,
                () -> { }, ignored -> { }, ignored -> { }, ignored -> simulationActivityEvents.incrementAndGet(),
                650.0, 50.0, 5000.0, localization);
        ConnectionController connections = ConnectionControllerFactory.create(
                new ConnectionControllerFactory.Dependencies(
                        connectionService, state, workspace, neuronViews,
                        () -> { }, () -> { }, ignored -> { }, localization));

        NeuronMenuView neuronMenuView = new NeuronMenuView(
                state::selectedNeuronForMenu, ignored -> { }, localization);
        MainMenuView mainMenu = new MainMenuView(localization, ignored -> { });
        StackPane overlayHost = new StackPane(workspace.node(), mainMenu);
        Scene scene = new Scene(overlayHost, 1000.0, 700.0);
        NeuronSettingsOverlayCoordinator settingsCoordinator =
                new NeuronSettingsOverlayCoordinator(overlayHost, mainMenu, localization);
        NeuronInteractionController controller = NeuronInteractionControllerFactory.create(
                new NeuronInteractionControllerFactory.Dependencies(
                        neuronService,
                        groupService,
                        selectionController,
                        new NeuronClipboardService(neuronService, groupService),
                        state,
                        workspace,
                        neuronViews,
                        rotationHandles,
                        connections,
                        simulation,
                        () -> { },
                        () -> { },
                        ignored -> { },
                        neuronMenuView,
                        localization,
                        settingsCoordinator));
        new MapPresentationCoordinator(
                neuronService,
                workspace,
                neuronViews,
                connectionViews,
                rotationHandles,
                controller,
                connections,
                new NeuronLayerOrderManager(neuronViews, connectionViews, model::connections));
        scene.getRoot().applyCss();
        scene.getRoot().layout();
        controller.loadViews();

        return new Fixture(controller, simulation, connections, mainMenu, neuronService, structureService,
                state, workspace, neuronViews, rotationHandles, simulationActivityEvents);
    }

    /** Закриває контролери та фонові ресурси тестового редактора. */
    private static void dispose(Fixture fixture) {
        fixture.controller().dispose();
        fixture.simulation().shutdown();
        fixture.connections().dispose();
        fixture.mainMenu().dispose();
    }

    /** Надсилає повний цикл подій миші до вказаного нейронного подання. */
    private static void fireClick(NeuronView neuronView, MouseButton button) {
        fireMouseEvent(neuronView, MouseEvent.MOUSE_PRESSED, button);
        fireMouseEvent(neuronView, MouseEvent.MOUSE_RELEASED, button);
        fireMouseEvent(neuronView, MouseEvent.MOUSE_CLICKED, button);
    }

    /** Надсилає одиночну подію миші без залежності від фізичного пристрою введення. */
    private static void fireMouseEvent(NeuronView neuronView, javafx.event.EventType<MouseEvent> type,
                                       MouseButton button) {
        MouseEvent event = new MouseEvent(
                type, 10.0, 10.0, 10.0, 10.0, button, 1,
                false, false, false, false,
                button == MouseButton.PRIMARY, false,
                button == MouseButton.SECONDARY, false, false, false, null);
        Event.fireEvent(neuronView, event);
    }

    /** Надсилає Enter до нейрона й перевіряє, що спожита подія не доходить до батьківського вузла. */
    private static boolean fireEnter(NeuronView neuronView) {
        Parent parent = neuronView.getParent();
        assertNotNull(parent, "Нейронне подання має бути приєднане до дерева JavaFX");
        boolean[] reachedParent = {false};
        EventHandler<KeyEvent> parentObserver = ignored -> reachedParent[0] = true;
        parent.addEventHandler(KeyEvent.KEY_PRESSED, parentObserver);
        try {
            KeyEvent event = new KeyEvent(
                    neuronView, neuronView, KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER,
                    false, false, false, false);
            Event.fireEvent(neuronView, event);
            return !reachedParent[0];
        } finally {
            parent.removeEventHandler(KeyEvent.KEY_PRESSED, parentObserver);
        }
    }

    /** Знаходить первинну кнопку керування шухлядкою в меню структури. */
    private static Button findDrawerButton(WorkspaceView workspace) {
        VBox menu = workspace.overlayLayer().getChildren().stream()
                .filter(VBox.class::isInstance)
                .map(VBox.class::cast)
                .filter(VBox::isVisible)
                .findFirst()
                .orElseThrow();
        HBox buttons = (HBox) menu.getChildren().getFirst();
        return buttons.getChildren().stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> button.getStyleClass().contains("neuron-menu-structure-drawer"))
                .findFirst()
                .orElseThrow();
    }

    /**
     * Зберігає залежності й подання, потрібні тестовим сценаріям.
     *
     * @param controller контролер взаємодії з нейронами.
     * @param simulation контролер симуляції для завершення його життєвого циклу.
     * @param connections контролер зв'язків для завершення його життєвого циклу.
     * @param mainMenu подання головного меню для звільнення слухачів.
     * @param neuronService служба створення й читання нейронів.
     * @param structureService служба читання стану структур.
     * @param state спільний стан взаємодії редактора.
     * @param workspace полотно з графічними шарами.
     * @param neuronViews подання нейронів, створені координатором.
     * @param rotationHandles ручки обертання відповідних нейронів.
     * @param simulationActivityEvents кількість повідомлень про зміну активності симуляції.
     */
    private record Fixture(
            NeuronInteractionController controller,
            SimulationController simulation,
            ConnectionController connections,
            MainMenuView mainMenu,
            NeuronService neuronService,
            StructureService structureService,
            EditorState state,
            WorkspaceView workspace,
            Map<String, NeuronView> neuronViews,
            Map<String, RotationHandleView> rotationHandles,
            AtomicInteger simulationActivityEvents
    ) { }
}
