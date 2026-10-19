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
import com.example.neuronmap.simulation.SimulationService;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import com.example.neuronmap.view.connection.ConnectionView;
import com.example.neuronmap.view.editor.NeuronLayerOrderManager;
import com.example.neuronmap.view.editor.WorkspaceView;
import com.example.neuronmap.view.menu.MainMenuView;
import com.example.neuronmap.view.neuron.NeuronMenuView;
import com.example.neuronmap.view.neuron.NeuronSettingsView;
import com.example.neuronmap.view.neuron.NeuronView;
import com.example.neuronmap.view.neuron.RotationHandleView;
import com.example.neuronmap.view.simulation.SimulationStepPresenter;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє реакцію взаємодії з нейроном на різні кнопки миші та видимість контекстного меню. */
final class NeuronInteractionControllerMouseButtonTest {

    /** Перевіряє, що ліве клацання показує ручку обертання без контекстного меню. */
    @Test
    void leftClickShowsRotationHandleWithoutMenu() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture fixture = fixture();
            click(fixture.neuronView(), MouseButton.PRIMARY);
            assertTrue(fixture.handle().isVisible());
            assertFalse(hasVisibleMenu(fixture.workspace()));
            dispose(fixture);
        });
    }

    /** Перевіряє, що праве клацання показує контекстне меню й приховує ручку обертання. */
    @Test
    void rightClickShowsMenuAndHidesRotationHandle() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture fixture = fixture();
            click(fixture.neuronView(), MouseButton.PRIMARY);
            assertTrue(fixture.handle().isVisible());
            click(fixture.neuronView(), MouseButton.SECONDARY);
            assertTrue(hasVisibleMenu(fixture.workspace()));
            assertFalse(fixture.handle().isVisible());
            dispose(fixture);
        });
    }

    /** Перевіряє, що відкриття налаштувань прибирає контекстне меню та приховує його після закриття накладки. */
    @Test
    void openingSettingsClosesContextMenuAndDoesNotRestoreItAfterCancel() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture fixture = fixture();
            click(fixture.neuronView(), MouseButton.SECONDARY);
            assertTrue(hasVisibleMenu(fixture.workspace()));

            VBox contextMenu = fixture.workspace().overlayLayer().getChildren().stream()
                    .filter(VBox.class::isInstance)
                    .map(VBox.class::cast)
                    .filter(VBox::isVisible)
                    .findFirst()
                    .orElseThrow();
            HBox buttons = (HBox) contextMenu.getChildren().getFirst();
            Button settingsButton = buttons.getChildren().stream()
                    .filter(Button.class::isInstance)
                    .map(Button.class::cast)
                    .filter(button -> button.getTooltip() != null
                            && fixture.localization().text("neuron.menu.settings")
                            .equals(button.getTooltip().getText()))
                    .findFirst()
                    .orElseThrow();

            settingsButton.fire();

            assertFalse(hasVisibleMenu(fixture.workspace()),
                    "Контекстне меню потрібно прибрати перед показом модальної накладки");
            NeuronSettingsView settingsView = fixture.overlayHost().getChildren().stream()
                    .filter(NeuronSettingsView.class::isInstance)
                    .map(NeuronSettingsView.class::cast)
                    .findFirst()
                    .orElseThrow();
            settingsView.hide();
            assertFalse(hasVisibleMenu(fixture.workspace()),
                    "Закриття налаштувань не повинно відновлювати старе контекстне меню");
            dispose(fixture);
        });
    }

    /** Створює контролери редактора з явним контейнером накладок і власником синхронізації подань. */
    private static Fixture fixture() {
        NeuronMapModel model = new NeuronMapModel();
        var neuron = model.createNeuron(NeuronType.EXCITATORY, 100.0, 80.0);
        NeuronService neuronService = new NeuronService(model);
        GroupService groupService = new GroupService(model);
        ConnectionService connectionService = new ConnectionService(model);
        EditorState state = new EditorState(1.0, 0.0, 0.0);
        LocalizationService localization = new LocalizationService(Locale.ENGLISH);
        WorkspaceView workspace = new WorkspaceView();
        Map<String, NeuronView> neuronViews = new HashMap<>();
        Map<String, ConnectionView> connectionViews = new HashMap<>();
        Map<String, RotationHandleView> rotationHandles = new HashMap<>();

        SelectionController selectionController = new SelectionController(
                groupService,
                state,
                () -> { },
                () -> { },
                ignored -> { },
                localization
        );
        SimulationStepPresenter stepPresenter = new SimulationStepPresenter(
                workspace,
                neuronViews,
                () -> { },
                new PulseAnimationController(workspace)
        );
        SimulationController simulation = new SimulationController(
                neuronService,
                new SimulationService(),
                stepPresenter,
                () -> { },
                ignored -> { },
                ignored -> { },
                ignored -> { },
                650.0,
                50.0,
                5000.0,
                localization
        );
        ConnectionController connections = ConnectionControllerFactory.create(
                new ConnectionControllerFactory.Dependencies(
                        connectionService,
                        state,
                        workspace,
                        neuronViews,
                        () -> { },
                        () -> { },
                        ignored -> { },
                        localization
                )
        );

        HBox toolbar = new HBox();
        NeuronMenuView neuronMenuView = new NeuronMenuView(
                toolbar,
                state::selectedNeuronForMenu,
                ignored -> { },
                localization
        );
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
                        settingsCoordinator
                )
        );
        new MapPresentationCoordinator(
                neuronService,
                workspace,
                neuronViews,
                connectionViews,
                rotationHandles,
                controller,
                connections,
                new NeuronLayerOrderManager(neuronViews, connectionViews, model::connections)
        );

        scene.getRoot().applyCss();
        scene.getRoot().layout();
        controller.loadViews();
        NeuronView neuronView = neuronViews.get(neuron.id());
        assertNotNull(neuronView);
        return new Fixture(controller, workspace, neuronView, rotationHandles.get(neuron.id()),
                overlayHost, mainMenu, localization);
    }

    /** Звільняє контролер і ресурси головного меню після тестового сценарію. */
    private static void dispose(Fixture fixture) {
        fixture.controller().dispose();
        fixture.mainMenu().dispose();
    }

    /** Викликає події натискання, відпускання та клацання миші для зазначеного подання нейрона. */
    private static void click(NeuronView neuronView, MouseButton button) {
        fire(neuronView, MouseEvent.MOUSE_PRESSED, button);
        fire(neuronView, MouseEvent.MOUSE_RELEASED, button);
        fire(neuronView, MouseEvent.MOUSE_CLICKED, button);
    }

    /** Надсилає подію миші до подання нейрона. */
    private static void fire(NeuronView neuronView, javafx.event.EventType<MouseEvent> type, MouseButton button) {
        MouseEvent event = new MouseEvent(
                type,
                10.0, 10.0, 10.0, 10.0,
                button, 1,
                false, false, false, false,
                button == MouseButton.PRIMARY,
                false,
                button == MouseButton.SECONDARY,
                false, false, false, null
        );
        javafx.event.Event.fireEvent(neuronView, event);
    }

    /** Перевіряє, чи містить полотно видиме контекстне меню. */
    private static boolean hasVisibleMenu(WorkspaceView workspace) {
        return workspace.overlayLayer().getChildren().stream()
                .anyMatch(node -> node instanceof VBox && node.isVisible());
    }

    /** Зберігає залежності тестового редактора та вузли для перевірок взаємодії. */
    private record Fixture(
            NeuronInteractionController controller,
            WorkspaceView workspace,
            NeuronView neuronView,
            RotationHandleView handle,
            StackPane overlayHost,
            MainMenuView mainMenu,
            LocalizationService localization
    ) { }
}
