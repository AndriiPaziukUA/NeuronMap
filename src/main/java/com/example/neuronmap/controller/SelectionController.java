package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.service.GroupService;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.function.Consumer;

/** Owns selection state interactions and group/ungroup commands. */
public final class SelectionController {

    private final GroupService groupService;
    private final EditorState state;
    private final Runnable refreshNeuronViews;
    private final Runnable save;
    private final Consumer<String> status;
    private final LocalizationService localization;

    public SelectionController(
            GroupService groupService,
            EditorState state,
            Runnable refreshNeuronViews,
            Runnable save,
            Consumer<String> status
    ) {
        this(
                groupService,
                state,
                refreshNeuronViews,
                save,
                status,
                new LocalizationService(java.util.Locale.forLanguageTag("uk"))
        );
    }

    public SelectionController(
            GroupService groupService,
            EditorState state,
            Runnable refreshNeuronViews,
            Runnable save,
            Consumer<String> status,
            LocalizationService localization
    ) {
        this.groupService = groupService;
        this.state = state;
        this.refreshNeuronViews = refreshNeuronViews;
        this.save = save;
        this.status = status;
        this.localization = localization;
    }

    public void selectForPrimaryPress(String neuronId, boolean additive) {
        if (additive) {
            toggle(neuronId);
        } else if (!state.selectedNeuronIds().contains(neuronId)) {
            selectOnly(neuronId);
        } else {
            refreshNeuronViews.run();
        }
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
    }

    public void groupSelection() {
        if (state.selectedNeuronIds().size() < 2) {
            status.accept(localization.text("status.group_min"));
            return;
        }

        groupService.create(
                new LinkedHashSet<>(state.selectedNeuronIds())
        );
        save.run();
        status.accept(
                localization.text(
                        "status.group_created",
                        state.selectedNeuronIds().size()
                )
        );
    }

    public void ungroupSelection() {
        groupService.ungroup(
                new HashSet<>(state.selectedNeuronIds())
        );
        save.run();
        status.accept(localization.text("status.ungrouped"));
    }
}
