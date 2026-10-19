package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.service.ConnectionService;
import com.example.neuronmap.view.editor.WorkspaceView;
import com.example.neuronmap.view.neuron.NeuronView;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Складає ізольований граф контролера зв’язків, зберігаючи його спеціалізовані реалізації внутрішніми. */
public final class ConnectionControllerFactory {

    /** Забороняє створювати фабрику без параметрів складання графа. */
    private ConnectionControllerFactory() {
    }

    /**
     * Створює контролери створення та видалення зв’язків і передає їх фасаду взаємодії з полотном.
     *
     * @param dependencies перевірений набір залежностей підсистеми зв’язків.
     * @return готовий контролер зв’язків.
     * @throws NullPointerException якщо набір залежностей не передано.
     */
    public static ConnectionController create(Dependencies dependencies) {
        Objects.requireNonNull(dependencies, "dependencies");
        ConnectionDeletionController deletionController = new ConnectionDeletionController(
                dependencies.connectionService(),
                dependencies.state(),
                dependencies.workspace(),
                dependencies.refresh(),
                dependencies.save(),
                dependencies.status(),
                dependencies.localization()
        );
        ConnectionCreationController creationController = new ConnectionCreationController(
                dependencies.connectionService(),
                dependencies.state(),
                dependencies.workspace(),
                dependencies.neuronViews(),
                dependencies.refresh(),
                dependencies.save(),
                dependencies.status(),
                deletionController::exitDelete,
                dependencies.localization()
        );
        return new ConnectionController(
                dependencies.state(),
                dependencies.workspace(),
                creationController,
                deletionController
        );
    }

    /**
     * Залежності, спільні для контролерів створення та видалення зв’язків.
     *
     * @param connectionService служба операцій над зв’язками.
     * @param state спільний стан редактора.
     * @param workspace полотно редактора.
     * @param neuronViews подання нейронів за ідентифікаторами.
     * @param refresh дія синхронізації подань після зміни моделі.
     * @param save дія збереження змін карти.
     * @param status дія показу повідомлення.
     * @param localization спільна служба локалізації.
     */
    public record Dependencies(
            ConnectionService connectionService,
            EditorState state,
            WorkspaceView workspace,
            Map<String, NeuronView> neuronViews,
            Runnable refresh,
            Runnable save,
            Consumer<String> status,
            LocalizationService localization
    ) {
        /** Перевіряє залежності до створення підконтролерів і їхніх UI-ресурсів. */
        public Dependencies {
            Objects.requireNonNull(connectionService, "connectionService");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(workspace, "workspace");
            Objects.requireNonNull(neuronViews, "neuronViews");
            Objects.requireNonNull(refresh, "refresh");
            Objects.requireNonNull(save, "save");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(localization, "localization");
        }
    }
}
