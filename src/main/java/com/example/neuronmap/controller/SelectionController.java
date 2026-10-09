package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.service.GroupService;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.function.Consumer;

/**
 * Керує вибором нейронів та інших елементів карти.
 */
public final class SelectionController {

    private final GroupService groupService;
    private final EditorState state;
    private final Runnable refreshNeuronViews;
    private final Runnable save;
    private final Consumer<String> status;
    private final LocalizationService localization;

    /**
     * Повертає результат операції «вибір».
     *
     * @param groupService значення, що визначає група служба для цієї операції.
     *
     * @param state стан обʼєкта або редактора.
     *
     * @param refreshNeuronViews значення, що визначає нейрон для цієї операції.
     *
     * @param save значення, що визначає відповідну операцію для цієї операції.
     *
     * @param status стан операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
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

    /**
     * Повертає результат операції «вибір».
     *
     * @param groupService значення, що визначає група служба для цієї операції.
     *
     * @param state стан обʼєкта або редактора.
     *
     * @param refreshNeuronViews значення, що визначає нейрон для цієї операції.
     *
     * @param save значення, що визначає відповідну операцію для цієї операції.
     *
     * @param status стан операції.
     *
     * @param localization значення, що визначає локалізація для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
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

    /**
     * Виконує операцію «для».
     *
     * @param neuronId ідентифікатор нейрона.
     *
     * @param additive значення, що визначає відповідну операцію для цієї операції.
     */
    public void selectForPrimaryPress(String neuronId, boolean additive) {
        if (additive) {
            toggle(neuronId);
        } else if (!state.selectedNeuronIds().contains(neuronId)) {
            selectOnly(neuronId);
        } else {
            refreshNeuronViews.run();
        }
    }

    /**
     * Виконує операцію «лише».
     *
     * @param neuronId ідентифікатор нейрона.
     */
    public void selectOnly(String neuronId) {
        state.selectOnly(neuronId);
        refreshNeuronViews.run();
    }

    /**
     * Перемикає стан «потрібні дані».
     *
     * @param neuronId ідентифікатор нейрона.
     */
    public void toggle(String neuronId) {
        state.toggleSelection(neuronId);
        refreshNeuronViews.run();
    }

    /**
     * Видаляє або скидає дані, повʼязані з «потрібні дані».
     */
    public void clear() {
        state.clearSelection();
        refreshNeuronViews.run();
    }

    /**
     * Виконує операцію «група вибір».
     */
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

    /**
     * Виконує операцію «вибір».
     */
    public void ungroupSelection() {
        groupService.ungroup(
                new HashSet<>(state.selectedNeuronIds())
        );
        save.run();
        status.accept(localization.text("status.ungrouped"));
    }
}
