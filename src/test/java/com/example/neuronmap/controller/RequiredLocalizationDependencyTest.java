package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.service.ConnectionService;
import com.example.neuronmap.service.GroupService;
import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.simulation.SimulationService;
import com.example.neuronmap.testsupport.JavaFxTestSupport;
import com.example.neuronmap.view.editor.ToolbarView;
import com.example.neuronmap.view.editor.WorkspaceView;
import com.example.neuronmap.view.simulation.SimulationStepPresenter;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** Перевіряє, що компоненти редактора не створюють прихований сервіс локалізації за відсутності залежності. */
final class RequiredLocalizationDependencyTest {

    /** Перевіряє, що контролер вибору відхиляє відсутню службу локалізації. */
    @Test
    void selectionControllerRejectsMissingLocalization() {
        NeuronMapModel model = new NeuronMapModel();
        assertThrows(NullPointerException.class, () -> new SelectionController(
                new GroupService(model),
                new EditorState(1.0, 0.0, 0.0),
                () -> { },
                () -> { },
                ignored -> { },
                null
        ));
    }

    /** Перевіряє, що контролер камери не трактує null як запит на створення окремої української локалізації. */
    @Test
    void cameraControllerRejectsMissingLocalization() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            WorkspaceView workspace = new WorkspaceView();
            assertThrows(NullPointerException.class, () -> new CameraController(
                    new EditorState(1.0, 0.0, 0.0),
                    workspace,
                    () -> { },
                    () -> { },
                    () -> false,
                    ignored -> false,
                    ignored -> { },
                    ignored -> { },
                    1.2,
                    null
            ));
        });
    }

    /** Перевіряє, що контролер симуляції вимагає локалізацію до ініціалізації таймерів. */
    @Test
    void simulationControllerRejectsMissingLocalization() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            NeuronMapModel model = new NeuronMapModel();
            WorkspaceView workspace = new WorkspaceView();
            SimulationStepPresenter presenter = new SimulationStepPresenter(
                    workspace,
                    Map.of(),
                    () -> { },
                    new PulseAnimationController(workspace)
            );
            assertThrows(NullPointerException.class, () -> new SimulationController(
                    new NeuronService(model),
                    new SimulationService(),
                    presenter,
                    () -> { },
                    ignored -> { },
                    ignored -> { },
                    ignored -> { },
                    100.0,
                    10.0,
                    500.0,
                    null
            ));
        });
    }

    /** Перевіряє, що панель інструментів не підміняє відсутню локалізацію власним екземпляром. */
    @Test
    void toolbarRejectsMissingLocalization() throws Exception {
        JavaFxTestSupport.runAndWait(() -> assertThrows(NullPointerException.class, () -> new ToolbarView(
                ignored -> { },
                () -> { },
                () -> { },
                () -> { },
                () -> { },
                ignored -> { },
                650.0,
                null
        )));
    }

    /** Перевіряє, що фабрика графа зв'язків відхиляє неповний набір залежностей до створення контролерів. */
    @Test
    void connectionControllerFactoryRejectsMissingLocalization() throws Exception {
        JavaFxTestSupport.runAndWait(() -> {
            NeuronMapModel model = new NeuronMapModel();
            assertThrows(NullPointerException.class, () -> new ConnectionControllerFactory.Dependencies(
                    new ConnectionService(model),
                    new EditorState(1.0, 0.0, 0.0),
                    new WorkspaceView(),
                    Map.of(),
                    () -> { },
                    () -> { },
                    ignored -> { },
                    null
            ));
        });
    }
}
