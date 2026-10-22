package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.coordinator.NeuronSettingsOverlayCoordinator;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.service.GroupService;
import com.example.neuronmap.service.NeuronClipboardService;
import com.example.neuronmap.service.NeuronService;
import com.example.neuronmap.view.editor.WorkspaceView;
import com.example.neuronmap.view.neuron.NeuronMenuView;
import com.example.neuronmap.view.neuron.NeuronView;
import com.example.neuronmap.view.neuron.RotationHandleView;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Складає спеціалізовані контролери взаємодії з нейронами навколо їхнього спільного callback-власника. */
public final class NeuronInteractionControllerFactory {

    /** Забороняє створення фабрики без залежностей для складання графа взаємодії. */
    private NeuronInteractionControllerFactory() {
    }

    /**
     * Створює контролер взаємодії, а потім ін'єктує його підконтролери з callback-ами до власника.
     * Такий порядок прибирає створення concrete-залежностей із самого контролера й не вимагає сервіс-локатора.
     *
     * @param dependencies перевірений набір залежностей взаємодії з нейронами.
     * @return повністю ініціалізований контролер взаємодії.
     * @throws NullPointerException якщо набір залежностей не передано.
     */
    public static NeuronInteractionController create(Dependencies dependencies) {
        Objects.requireNonNull(dependencies, "dependencies");
        NeuronInteractionController interactionController = new NeuronInteractionController(
                dependencies.state(),
                dependencies.selectionController(),
                dependencies.simulation(),
                dependencies.localization()
        );

        NeuronClipboardController clipboardController = createClipboardController(
                dependencies, interactionController);
        NeuronDeletionController deletionController = createDeletionController(
                dependencies, interactionController);
        NeuronMenuController menuController = createMenuController(
                dependencies, interactionController, deletionController);
        NeuronRotationController rotationController = createRotationController(
                dependencies, interactionController);
        NeuronDragController dragController = createDragController(
                dependencies, interactionController, rotationController);

        interactionController.initializeControllers(
                clipboardController,
                deletionController,
                menuController,
                rotationController,
                dragController
        );
        return interactionController;
    }

    /** Створює підконтролер буфера обміну, передаючи йому дії до власника взаємодії. */
    private static NeuronClipboardController createClipboardController(
            Dependencies dependencies,
            NeuronInteractionController owner
    ) {
        return new NeuronClipboardController(
                dependencies.clipboardService(),
                dependencies.state(),
                dependencies.workspace(),
                owner::hideMenu,
                dependencies.refreshPresentation(),
                dependencies.save(),
                dependencies.status(),
                dependencies.localization()
        );
    }

    /** Створює підконтролер видалення нейронів зі спільними callback-ами оновлення та локалізації. */
    private static NeuronDeletionController createDeletionController(
            Dependencies dependencies,
            NeuronInteractionController owner
    ) {
        return new NeuronDeletionController(
                dependencies.neuronService(),
                dependencies.state(),
                dependencies.workspace(),
                owner::hideMenu,
                dependencies.refreshPresentation(),
                dependencies.save(),
                dependencies.status(),
                dependencies.localization()
        );
    }

    /** Створює контекстне меню після створення контролера видалення, від якого меню залежить явно. */
    private static NeuronMenuController createMenuController(
            Dependencies dependencies,
            NeuronInteractionController owner,
            NeuronDeletionController deletionController
    ) {
        return new NeuronMenuController(
                dependencies.neuronService(),
                dependencies.state(),
                dependencies.workspace(),
                dependencies.connections(),
                dependencies.simulation(),
                deletionController,
                dependencies.neuronMenuView(),
                owner::refreshVisuals,
                owner::refreshRelatedViews,
                owner::refreshOverlayPositions,
                dependencies.save(),
                dependencies.status(),
                dependencies.localization(),
                dependencies.settingsOverlayCoordinator()
        );
    }

    /** Створює контролер обертання, використовуючи локалізацію й callback-и власника презентації. */
    private static NeuronRotationController createRotationController(
            Dependencies dependencies,
            NeuronInteractionController owner
    ) {
        return new NeuronRotationController(
                dependencies.neuronService(),
                dependencies.state(),
                dependencies.workspace(),
                dependencies.neuronViews(),
                dependencies.rotationHandles(),
                dependencies.localization(),
                owner::hideMenu,
                owner::refreshVisuals,
                owner::refreshRelatedViews,
                owner::refreshOverlayPositions,
                dependencies.save()
        );
    }

    /** Створює контролер перетягування після контролера обертання, щоб передати його дію показу ручки. */
    private static NeuronDragController createDragController(
            Dependencies dependencies,
            NeuronInteractionController owner,
            NeuronRotationController rotationController
    ) {
        return new NeuronDragController(
                dependencies.neuronService(),
                dependencies.groupService(),
                dependencies.selectionController(),
                dependencies.state(),
                dependencies.workspace(),
                owner::hideMenu,
                rotationController::show,
                owner::refreshVisuals,
                owner::refreshRelatedViews,
                owner::refreshOverlayPositions,
                dependencies.save()
        );
    }

    /**
     * Повний набір залежностей для створення графа взаємодії з нейронами.
     *
     * @param neuronService служба операцій над нейронами.
     * @param groupService служба операцій над групами нейронів.
     * @param selectionController контролер вибору нейронів.
     * @param clipboardService служба копіювання та вставлення нейронів і груп.
     * @param state спільний стан редактора.
     * @param workspace полотно редактора.
     * @param neuronViews спільні подання нейронів за ідентифікаторами.
     * @param rotationHandles ручки обертання, що відповідають поданням нейронів.
     * @param connections фасад керування зв’язками.
     * @param simulation контролер симуляції.
     * @param save дія збереження змін карти.
     * @param refreshPresentation дія синхронізації подань карти.
     * @param status дія відображення повідомлення користувачу.
     * @param neuronMenuView подання кнопок контекстного меню нейрона.
     * @param localization спільна служба локалізації інтерфейсу.
     * @param settingsOverlayCoordinator координатор накладки налаштувань нейрона.
     */
    public record Dependencies(
            NeuronService neuronService,
            GroupService groupService,
            SelectionController selectionController,
            NeuronClipboardService clipboardService,
            EditorState state,
            WorkspaceView workspace,
            Map<String, NeuronView> neuronViews,
            Map<String, RotationHandleView> rotationHandles,
            ConnectionController connections,
            SimulationController simulation,
            Runnable save,
            Runnable refreshPresentation,
            Consumer<String> status,
            NeuronMenuView neuronMenuView,
            LocalizationService localization,
            NeuronSettingsOverlayCoordinator settingsOverlayCoordinator
    ) {
        /** Перевіряє повноту графа до створення дочірніх контролерів та їхніх подієвих реєстрацій. */
        public Dependencies {
            Objects.requireNonNull(neuronService, "neuronService");
            Objects.requireNonNull(groupService, "groupService");
            Objects.requireNonNull(selectionController, "selectionController");
            Objects.requireNonNull(clipboardService, "clipboardService");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(workspace, "workspace");
            Objects.requireNonNull(neuronViews, "neuronViews");
            Objects.requireNonNull(rotationHandles, "rotationHandles");
            Objects.requireNonNull(connections, "connections");
            Objects.requireNonNull(simulation, "simulation");
            Objects.requireNonNull(save, "save");
            Objects.requireNonNull(refreshPresentation, "refreshPresentation");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(neuronMenuView, "neuronMenuView");
            Objects.requireNonNull(localization, "localization");
            Objects.requireNonNull(settingsOverlayCoordinator, "settingsOverlayCoordinator");
        }
    }
}
