package com.example.neuronmap.coordinator;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.controller.ConnectionController;
import com.example.neuronmap.controller.ConnectionControllerFactory;
import com.example.neuronmap.controller.NeuronInteractionController;
import com.example.neuronmap.controller.NeuronInteractionControllerFactory;
import com.example.neuronmap.controller.PulseAnimationController;
import com.example.neuronmap.controller.SelectionController;
import com.example.neuronmap.controller.SimulationController;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.Neuron;
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
import com.example.neuronmap.view.neuron.NeuronStructureDrawerView;
import com.example.neuronmap.view.neuron.NeuronView;
import com.example.neuronmap.view.neuron.RotationHandleView;
import com.example.neuronmap.view.simulation.SimulationStepPresenter;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє, що додавання учасників через налаштування одразу оновлює вже відкриту шухлядку. */
final class StructureMemberCountPresentationTest {

    /** Перевіряє створення подань одразу після збереження нової кількості відкритої структури. */
    @Test
    void newlyAddedMembersAppearImmediatelyInOpenDrawer() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture fixture = new Fixture();
            try {
                Neuron structure = fixture.neuronService.create(NeuronType.STRUCTURE, 80.0, 60.0);
                fixture.presentation.refreshAll();
                NeuronView structureView = fixture.neuronViews.get(structure.id());
                assertNotNull(structureView);

                fixture.model.structure(structure.id()).setExpanded(true);
                fixture.presentation.refreshAll();
                NeuronStructureDrawerView drawer = drawerOf(structureView);
                assertTrue(drawer.isVisible());
                assertEquals(1, drawer.memberViews().size());

                fixture.neuronController.showMenu(structureView);
                VBox menu = fixture.workspace.overlayLayer().getChildren().stream()
                        .filter(VBox.class::isInstance)
                        .map(VBox.class::cast)
                        .findFirst()
                        .orElseThrow();
                HBox menuButtons = (HBox) menu.getChildren().getFirst();
                Button settingsButton = menuButtons.getChildren().stream()
                        .filter(Button.class::isInstance)
                        .map(Button.class::cast)
                        .filter(button -> button.getGraphic() != null
                                && button.getGraphic().getStyleClass().contains("neuron-menu-icon-settings"))
                        .findFirst()
                        .orElseThrow();
                settingsButton.fire();

                NeuronSettingsView settingsView = fixture.overlayHost.getChildren().stream()
                        .filter(NeuronSettingsView.class::isInstance)
                        .map(NeuronSettingsView.class::cast)
                        .findFirst()
                        .orElseThrow();
                TextField countField = settingsView.lookupAll(".neuron-settings-row").stream()
                        .filter(HBox.class::isInstance)
                        .map(HBox.class::cast)
                        .filter(row -> row.getChildren().getFirst() instanceof Label label
                                && label.getText().equals(fixture.localization.textFrom(
                                        "i18n.neuronSettings", "structure_neuron_count")))
                        .map(row -> (TextField) row.getChildren().get(1))
                        .findFirst()
                        .orElseThrow();
                countField.setText("3");
                Button saveButton = settingsView.lookupAll(".neuron-settings-button").stream()
                        .filter(Button.class::isInstance)
                        .map(Button.class::cast)
                        .filter(button -> button.getText().equals(fixture.localization.text("neuron.settings.save")))
                        .findFirst()
                        .orElseThrow();
                fixture.workspace.node().layout();
                assertFalse(fixture.workspace.node().isNeedsLayout(),
                        "Перед збереженням перевірка має починатися з уже компонованого полотна");
                saveButton.fire();

                assertTrue(fixture.workspace.node().isNeedsLayout(),
                        "Після додавання учасників корінь полотна має отримати запит на негайне оновлення кадру");
                assertEquals(3, fixture.neuronService.structureMemberCount(structure.id()));
                assertTrue(drawer.isVisible(), "Відкрита шухлядка не повинна закриватися після збереження");
                assertEquals(3, drawer.memberViews().size(),
                        "Повна синхронізація має одразу додати створені подання до відкритої шухлядки");
                assertTrue(drawer.memberViews().stream().allMatch(memberView -> memberView.getParent() == drawer));
                assertFalse(fixture.workspace.overlayLayer().getChildren().contains(menu),
                        "Після збереження форма не повинна відновлювати контекстне меню поверх нових учасників");
                assertTrue(fixture.workspace.overlayLayer().getChildren().stream()
                                .noneMatch(node -> node instanceof VBox),
                        "Шар накладок не повинен перекривати шухлядку меню, яке щойно було збережене");
            } finally {
                fixture.dispose();
            }
        });
    }

    /** Повертає шухлядку структурного подання для перевірки її вмісту й видимості. */
    private static NeuronStructureDrawerView drawerOf(NeuronView structureView) {
        return structureView.getChildrenUnmodifiable().stream()
                .filter(NeuronStructureDrawerView.class::isInstance)
                .map(NeuronStructureDrawerView.class::cast)
                .findFirst()
                .orElseThrow();
    }

    /** Збирає компоненти редактора з реальною синхронізацією подань через координатор карти. */
    private static final class Fixture {
        private final NeuronMapModel model = new NeuronMapModel();
        private final NeuronService neuronService = new NeuronService(model);
        private final GroupService groupService = new GroupService(model);
        private final ConnectionService connectionService = new ConnectionService(model);
        private final EditorState state = new EditorState(1.0, 0.0, 0.0);
        private final WorkspaceView workspace = new WorkspaceView();
        private final Map<String, NeuronView> neuronViews = new HashMap<>();
        private final Map<String, ConnectionView> connectionViews = new HashMap<>();
        private final Map<String, RotationHandleView> rotationHandles = new HashMap<>();
        private final LocalizationService localization = new LocalizationService(Locale.forLanguageTag("uk"));
        private final MainMenuView mainMenu = new MainMenuView(localization, ignored -> { });
        private final StackPane overlayHost = new StackPane(workspace.node(), mainMenu);
        private final NeuronSettingsOverlayCoordinator settingsOverlayCoordinator =
                new NeuronSettingsOverlayCoordinator(overlayHost, mainMenu, localization);
        private final SelectionController selectionController = new SelectionController(
                groupService, state, () -> { }, () -> { }, ignored -> { }, localization);
        private final ConnectionController connectionController = ConnectionControllerFactory.create(
                new ConnectionControllerFactory.Dependencies(
                        connectionService, state, workspace, neuronViews, this::refreshPresentation,
                        () -> { }, ignored -> { }, localization));
        private final SimulationController simulationController = new SimulationController(
                neuronService, new SimulationService(),
                new SimulationStepPresenter(
                        workspace, neuronViews, () -> { }, new PulseAnimationController(workspace)),
                () -> { }, ignored -> { }, ignored -> { }, ignored -> { },
                100.0, 10.0, 1_000.0, localization);
        private final NeuronInteractionController neuronController = NeuronInteractionControllerFactory.create(
                new NeuronInteractionControllerFactory.Dependencies(
                        neuronService, groupService, selectionController,
                        new NeuronClipboardService(neuronService, groupService), state, workspace,
                        neuronViews, rotationHandles, connectionController, simulationController,
                        () -> { }, this::refreshPresentation, ignored -> { },
                        new NeuronMenuView(new Pane(), state::selectedNeuronForMenu, ignored -> { }, localization),
                        localization, settingsOverlayCoordinator));
        private final AtomicReference<MapPresentationCoordinator> presentationReference = new AtomicReference<>();
        private final MapPresentationCoordinator presentation = new MapPresentationCoordinator(
                neuronService, workspace, neuronViews, connectionViews, rotationHandles,
                neuronController, connectionController,
                new NeuronLayerOrderManager(neuronViews, connectionViews, model::connections));

        /** Створює JavaFX-сцену, перш ніж тест почне відкривати модальні накладки. */
        private Fixture() {
            presentationReference.set(presentation);
            Scene scene = new Scene(overlayHost, 1_000.0, 700.0);
            scene.getRoot().applyCss();
            scene.getRoot().layout();
        }

        /** Повністю синхронізує графічні подання через координатор — єдиного власника їхнього життєвого циклу. */
        private void refreshPresentation() {
            MapPresentationCoordinator coordinator = presentationReference.get();
            if (coordinator != null) {
                coordinator.refreshAll();
            }
        }

        /** Звільняє контролери та графічні ресурси після перевірки. */
        private void dispose() {
            neuronController.dispose();
            simulationController.shutdown();
            connectionController.dispose();
            settingsOverlayCoordinator.dispose();
            mainMenu.dispose();
            workspace.dispose();
        }
    }
}
