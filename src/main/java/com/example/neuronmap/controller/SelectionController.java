package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.application.NeuronMapApplicationService;
import com.example.neuronmap.view.GroupView;
import com.example.neuronmap.view.NeuronView;
import com.example.neuronmap.view.WorkspaceView;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.function.Consumer;

public final class SelectionController {

    private final NeuronMapApplicationService application;
    private final EditorState state;
    private final WorkspaceView workspace;
    private final Map<String, NeuronView> neuronViews;
    private final Runnable refreshNeuronViews;
    private final Runnable save;
    private final Consumer<String> status;

    public SelectionController(
            NeuronMapApplicationService application,
            EditorState state,
            WorkspaceView workspace,
            Map<String, NeuronView> neuronViews,
            Runnable refreshNeuronViews,
            Runnable save,
            Consumer<String> status
    ) {
        this.application = application;
        this.state = state;
        this.workspace = workspace;
        this.neuronViews = neuronViews;
        this.refreshNeuronViews = refreshNeuronViews;
        this.save = save;
        this.status = status;
    }

    public void selectOnly(String neuronId) {
        state.selectOnly(neuronId);
        refreshNeuronViews.run();
    }

    public void toggle(String neuronId) {
        state.toggleSelection(neuronId);
        refreshNeuronViews.run();
    }

    public void clear() {
        state.clearSelection();
        refreshNeuronViews.run();
        refreshGroups();
    }

    public void groupSelection() {
        if (state.selectedNeuronIds().size() < 2) {
            status.accept(
                    "Для групи вибери щонайменше два нейрони через Ctrl+клік."
            );
            return;
        }

        application.createGroup(
                new LinkedHashSet<>(
                        state.selectedNeuronIds()
                )
        );

        refreshGroups();
        save.run();
        status.accept(
                "Створено групу з "
                        + state.selectedNeuronIds().size()
                        + " нейронів."
        );
    }

    public void ungroupSelection() {
        application.ungroup(
                new HashSet<>(state.selectedNeuronIds())
        );

        refreshGroups();
        save.run();
        status.accept("Вибрані нейрони розгруповано.");
    }

    public void refreshGroups() {
        workspace.groupLayer()
                .getChildren()
                .clear();

        application.model().groups().forEach(group ->
                workspace.groupLayer()
                        .getChildren()
                        .add(
                                new GroupView(
                                        group,
                                        neuronViews
                                )
                        )
        );
    }
}
