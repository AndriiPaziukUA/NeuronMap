package com.example.neuronmap.coordinator;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.config.AppConfig;
import com.example.neuronmap.controller.CameraController;
import com.example.neuronmap.controller.ConnectionController;
import com.example.neuronmap.controller.EditorKeyboardController;
import com.example.neuronmap.controller.NeuronInteractionController;
import com.example.neuronmap.controller.PulseAnimationController;
import com.example.neuronmap.controller.SelectionController;
import com.example.neuronmap.controller.SimulationController;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.service.ConnectionService;
import com.example.neuronmap.service.GroupService;
import com.example.neuronmap.service.HistoryService;
import com.example.neuronmap.service.MapService;
import com.example.neuronmap.service.NeuronClipboardService;
import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.simulation.SimulationService;
import com.example.neuronmap.view.connection.ConnectionView;
import com.example.neuronmap.view.editor.MainView;
import com.example.neuronmap.view.editor.NeuronLayerOrderManager;
import com.example.neuronmap.view.neuron.NeuronMenuView;
import com.example.neuronmap.view.neuron.NeuronView;
import com.example.neuronmap.view.neuron.RotationHandleView;
import com.example.neuronmap.view.simulation.SimulationStepPresenter;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/**
 * Складає граф залежних компонентів карти, не перебираючи на себе сценарії роботи редактора.
 *
 * <p>Фабрика обмежена створенням контролерів і координаторів, які утворюють циклічно пов'язаний
 * граф взаємодії. Стан проєкту, перемикання сховищ і життєвий цикл сцени залишаються
 * відповідальністю {@link MapEditorCoordinator}.</p>
 */
final class MapEditorComponentFactory {

    /** Забороняє створення екземплярів без стану складання залежностей. */
    private MapEditorComponentFactory() {
    }

    /**
     * Створює компоненти редактора у порядку, необхідному для їхніх callback-залежностей.
     *
     * @param inputs залежності, спільний стан і дії зовнішнього координатора.
     * @return узгоджений набір створених компонентів редактора.
     */
    static Components create(Inputs inputs) {
        Objects.requireNonNull(inputs, "inputs");
        BaseComponents base = createBaseComponents(inputs);
        InteractionComponents interaction = createInteractionComponents(inputs, base);
        return createRemainingComponents(inputs, base, interaction);
    }

    /**
     * Створює контролери, які не потребують координатора презентації та взаємодії.
     *
     * @param inputs залежності складання редактора.
     * @return початкові компоненти для подальшого зв'язування графа.
     */
    private static BaseComponents createBaseComponents(Inputs inputs) {
        NeuronLayerOrderManager layerOrderController = new NeuronLayerOrderManager(
                inputs.neuronViews(),
                inputs.connectionViews(),
                () -> inputs.neuronService().model().connections()
        );
        NeuronMenuView neuronMenuView = new NeuronMenuView(
                inputs.state()::selectedNeuronForMenu,
                inputs.actions().toggleNeuronDirection(),
                inputs.localization()
        );
        NeuronSettingsOverlayCoordinator settingsOverlayCoordinator = new NeuronSettingsOverlayCoordinator(
                inputs.view().node(),
                inputs.view().mainMenu(),
                inputs.localization()
        );

        SimulationStepPresenter stepPresenter = new SimulationStepPresenter(
                inputs.view().workspace(),
                inputs.neuronViews(),
                inputs.actions().refreshNeuronVisuals(),
                new PulseAnimationController(inputs.view().workspace())
        );
        SimulationController simulationController = new SimulationController(
                inputs.neuronService(),
                new SimulationService(),
                stepPresenter,
                inputs.actions().save(),
                inputs.actions().updateStatus(),
                inputs.view().toolbar()::setSimulationPaused,
                inputs.view().toolbar()::setSimulationControlsVisible,
                inputs.simulationTickMillis(),
                inputs.config().simulation().minTickMillis(),
                inputs.config().simulation().maxTickMillis(),
                inputs.localization()
        );
        SimulationSpeedCoordinator simulationSpeedCoordinator = new SimulationSpeedCoordinator(
                inputs.mapService(),
                inputs.actions().applySimulationSpeed(),
                inputs.actions().refreshCurrentProjectAfterSpeedChange(),
                inputs.actions().updateStatus(),
                inputs.localization(),
                inputs.config().simulation().minTickMillis(),
                inputs.config().simulation().maxTickMillis()
        );

        ConnectionController connectionController = new ConnectionController(
                inputs.connectionService(),
                inputs.state(),
                inputs.view().workspace(),
                inputs.neuronViews(),
                inputs.actions().refreshMapPresentation(),
                inputs.actions().save(),
                inputs.actions().updateStatus(),
                inputs.localization()
        );
        SelectionController selectionController = new SelectionController(
                inputs.groupService(),
                inputs.state(),
                inputs.actions().refreshNeuronVisuals(),
                inputs.actions().save(),
                inputs.actions().updateStatus(),
                inputs.localization()
        );

        NeuronClipboardService clipboardService = new NeuronClipboardService(
                inputs.neuronService(),
                inputs.groupService()
        );
        NeuronInteractionController neuronController = new NeuronInteractionController(
                inputs.neuronService(),
                inputs.groupService(),
                selectionController,
                clipboardService,
                inputs.state(),
                inputs.view().workspace(),
                inputs.neuronViews(),
                inputs.rotationHandles(),
                connectionController,
                simulationController,
                inputs.actions().save(),
                inputs.actions().refreshMapPresentation(),
                inputs.actions().updateStatus(),
                neuronMenuView,
                inputs.localization(),
                settingsOverlayCoordinator
        );

        return new BaseComponents(
                layerOrderController,
                neuronMenuView,
                simulationController,
                simulationSpeedCoordinator,
                connectionController,
                selectionController,
                neuronController
        );
    }

    /**
     * Зв'язує контролери базового рівня з компонентами синхронізації подань та обробки полотна.
     *
     * @param inputs залежності складання редактора.
     * @param base компоненти контролерів, створені на попередньому кроці.
     * @return координатори подань і взаємодії, пов'язані з контролерами редактора.
     */
    private static InteractionComponents createInteractionComponents(Inputs inputs, BaseComponents base) {
        MapPresentationCoordinator presentation = new MapPresentationCoordinator(
                inputs.neuronService(),
                inputs.view().workspace(),
                inputs.neuronViews(),
                inputs.connectionViews(),
                inputs.rotationHandles(),
                base.neuronController(),
                base.connectionController(),
                base.layerOrderController()
        );

        MapInteractionCoordinator interaction = new MapInteractionCoordinator(
                inputs.neuronService(),
                inputs.state(),
                inputs.view().workspace(),
                base.selectionController(),
                base.connectionController(),
                base.neuronController(),
                base.simulationController(),
                presentation,
                inputs.actions().save(),
                inputs.actions().updateStatus(),
                inputs.localization()
        );
        interaction.configureToolbarButtons(
                inputs.view().toolbar().addExcitatoryButton(),
                inputs.view().toolbar().addInhibitoryButton()
        );
        return new InteractionComponents(presentation, interaction);
    }

    /**
     * Створює компоненти історії, клавіатури й камери після появи координатора взаємодії.
     *
     * @param inputs залежності складання редактора.
     * @param base базові контролери редактора.
     * @param interaction координатори синхронізації та взаємодії.
     * @return повний набір компонентів, потрібний головному координатору редактора.
     */
    private static Components createRemainingComponents(
            Inputs inputs,
            BaseComponents base,
            InteractionComponents interaction
    ) {
        EditorHistoryCoordinator historyCoordinator = new EditorHistoryCoordinator(
                inputs.historyService(),
                base.simulationController(),
                base.connectionController(),
                base.neuronController(),
                inputs.state(),
                inputs.view().workspace(),
                interaction.interaction()::dispose,
                interaction.presentation(),
                inputs.actions().save(),
                inputs.actions().updateStatus(),
                inputs.localization()
        );
        EditorKeyboardController keyboardController = new EditorKeyboardController(
                historyCoordinator::undo,
                historyCoordinator::redo,
                base.neuronController()::deleteSelectedNeurons,
                interaction.interaction()::cancelInteractions
        );
        CameraController cameraController = new CameraController(
                inputs.state(),
                inputs.view().workspace(),
                interaction.presentation()::refreshScreenSpaceOverlays,
                inputs.actions().save(),
                inputs.state()::isSpecialModeActive,
                base.neuronController()::isInteractiveTarget,
                inputs.actions().updateStatus(),
                point -> inputs.view().toolbar().setCameraCoordinates(point.getX(), point.getY()),
                inputs.config().camera().zoomFactor(),
                inputs.localization()
        );

        return new Components(
                base.simulationController(),
                base.simulationSpeedCoordinator(),
                base.connectionController(),
                base.selectionController(),
                base.neuronController(),
                cameraController,
                interaction.presentation(),
                interaction.interaction(),
                keyboardController
        );
    }

    /**
     * Залежності, що надходять від координатора та використовуються під час складання компонентів.
     *
     * @param config конфігурація симуляції й камери.
     * @param localization служба локалізації інтерфейсу.
     * @param mapService служба доступу до активної карти.
     * @param neuronService служба операцій над нейронами.
     * @param connectionService служба операцій над зв'язками.
     * @param groupService служба операцій над групами.
     * @param historyService служба історії карти.
     * @param state спільний стан редактора.
     * @param view головне подання редактора.
     * @param neuronViews спільні графічні подання нейронів.
     * @param connectionViews спільні графічні подання зв'язків.
     * @param rotationHandles спільні ручки обертання.
     * @param simulationTickMillis поточна тривалість такту симуляції.
     * @param actions callback-и, через які створені компоненти взаємодіють із координатором.
     */
    record Inputs(
            AppConfig config,
            LocalizationService localization,
            MapService mapService,
            NeuronService neuronService,
            ConnectionService connectionService,
            GroupService groupService,
            HistoryService historyService,
            EditorState state,
            MainView view,
            Map<String, NeuronView> neuronViews,
            Map<String, ConnectionView> connectionViews,
            Map<String, RotationHandleView> rotationHandles,
            double simulationTickMillis,
            Actions actions
    ) {
        /** Перевіряє повноту контексту до початку побічних ефектів складання. */
        Inputs {
            Objects.requireNonNull(config, "config");
            Objects.requireNonNull(localization, "localization");
            Objects.requireNonNull(mapService, "mapService");
            Objects.requireNonNull(neuronService, "neuronService");
            Objects.requireNonNull(connectionService, "connectionService");
            Objects.requireNonNull(groupService, "groupService");
            Objects.requireNonNull(historyService, "historyService");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(view, "view");
            Objects.requireNonNull(neuronViews, "neuronViews");
            Objects.requireNonNull(connectionViews, "connectionViews");
            Objects.requireNonNull(rotationHandles, "rotationHandles");
            Objects.requireNonNull(actions, "actions");
        }
    }

    /**
     * Callback-контракт для дій, власником яких залишається головний координатор редактора.
     *
     * @param save збереження активного стану редактора.
     * @param updateStatus показ локалізованого стану.
     * @param refreshMapPresentation синхронізація подань карти.
     * @param refreshNeuronVisuals оновлення вигляду нейронів.
     * @param applySimulationSpeed застосування тривалості такту симуляції.
     * @param refreshCurrentProjectAfterSpeedChange оновлення метаданих активного проєкту.
     * @param toggleNeuronDirection передавання команди зміни напрямку нейрона.
     */
    record Actions(
            Runnable save,
            Consumer<String> updateStatus,
            Runnable refreshMapPresentation,
            Runnable refreshNeuronVisuals,
            DoubleConsumer applySimulationSpeed,
            Runnable refreshCurrentProjectAfterSpeedChange,
            Consumer<String> toggleNeuronDirection
    ) {
        /** Перевіряє callback-и до початку складання графа залежностей. */
        Actions {
            Objects.requireNonNull(save, "save");
            Objects.requireNonNull(updateStatus, "updateStatus");
            Objects.requireNonNull(refreshMapPresentation, "refreshMapPresentation");
            Objects.requireNonNull(refreshNeuronVisuals, "refreshNeuronVisuals");
            Objects.requireNonNull(applySimulationSpeed, "applySimulationSpeed");
            Objects.requireNonNull(refreshCurrentProjectAfterSpeedChange, "refreshCurrentProjectAfterSpeedChange");
            Objects.requireNonNull(toggleNeuronDirection, "toggleNeuronDirection");
        }
    }

    /**
     * Готові компоненти контролерів до побудови координаторів презентації та взаємодії.
     *
     * @param layerOrderController керування порядком графічних шарів.
     * @param neuronMenuView контекстне меню нейрона.
     * @param simulationController контролер виконання симуляції.
     * @param simulationSpeedCoordinator координатор зміни швидкості.
     * @param connectionController контролер зв'язків.
     * @param selectionController контролер вибору.
     * @param neuronController контролер взаємодії з нейронами.
     */
    private record BaseComponents(
            NeuronLayerOrderManager layerOrderController,
            NeuronMenuView neuronMenuView,
            SimulationController simulationController,
            SimulationSpeedCoordinator simulationSpeedCoordinator,
            ConnectionController connectionController,
            SelectionController selectionController,
            NeuronInteractionController neuronController
    ) {
    }

    /**
     * Координатори, які зв'язують графічні подання зі сценаріями взаємодії.
     *
     * @param presentation єдиний власник синхронізації подань карти.
     * @param interaction координатор подій полотна й кнопок інструментів.
     */
    private record InteractionComponents(
            MapPresentationCoordinator presentation,
            MapInteractionCoordinator interaction
    ) {
    }

    /**
     * Повний набір компонентів, створених фабрикою та переданих координатору життєвого циклу редактора.
     *
     * @param simulationController контролер симуляції.
     * @param simulationSpeedCoordinator координатор швидкості симуляції.
     * @param connectionController контролер зв'язків.
     * @param selectionController контролер вибору.
     * @param neuronController контролер взаємодії з нейронами.
     * @param cameraController контролер камери.
     * @param presentation координатор синхронізації подань.
     * @param interaction координатор взаємодії з полотном.
     * @param keyboardController маршрутизатор гарячих клавіш.
     */
    record Components(
            SimulationController simulationController,
            SimulationSpeedCoordinator simulationSpeedCoordinator,
            ConnectionController connectionController,
            SelectionController selectionController,
            NeuronInteractionController neuronController,
            CameraController cameraController,
            MapPresentationCoordinator presentation,
            MapInteractionCoordinator interaction,
            EditorKeyboardController keyboardController
    ) {
    }
}
