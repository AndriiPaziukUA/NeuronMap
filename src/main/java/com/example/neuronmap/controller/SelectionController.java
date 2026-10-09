package com.example.neuronmap.controller;

import com.example.neuronmap.application.EditorState;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.service.GroupService;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.function.Consumer;

/**
 * Керує вибором нейронів, групуванням і розгрупуванням вибраних елементів.
 */
public final class SelectionController {

    private final GroupService groupService;
    private final EditorState state;
    private final Runnable refreshNeuronViews;
    private final Runnable save;
    private final Consumer<String> status;
    private final LocalizationService localization;

    /**
     * Створює екземпляр SelectionController та зберігає передані залежності, потрібні для його роботи.
     *
     * @param groupService служба операцій над групами нейронів.
     * @param state стан об’єкта, який потрібно зберегти або відновити.
     * @param refreshNeuronViews callback, який оновлює візуальні подання нейронів після зміни вибору.
     * @param save функція зворотного виклику для відповідної дії.
     * @param status callback для показу повідомлення в рядку стану.
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
     * Створює екземпляр SelectionController та зберігає передані залежності, потрібні для його роботи.
     *
     * @param groupService служба операцій над групами нейронів.
     * @param state стан об’єкта, який потрібно зберегти або відновити.
     * @param refreshNeuronViews callback, який оновлює візуальні подання нейронів після зміни вибору.
     * @param save функція зворотного виклику для відповідної дії.
     * @param status callback для показу повідомлення в рядку стану.
     * @param localization служба локалізації інтерфейсу.
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
     * Обробляє первинне натискання по нейрону: замінює вибір або додає нейрон до нього, якщо натиснуто клавішу модифікатора.
     *
     * @param neuronId ідентифікатор нейрона.
     * @param additive значення «additive», яке використовується в цьому методі.
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
     * Залишає у виборі лише вказаний нейрон та оновлює його відображення.
     *
     * @param neuronId ідентифікатор нейрона.
     */
    public void selectOnly(String neuronId) {
        state.selectOnly(neuronId);
        refreshNeuronViews.run();
    }

    /**
     * Перемикає включення нейрона до поточного вибору.
     *
     * @param neuronId ідентифікатор нейрона.
     */
    public void toggle(String neuronId) {
        state.toggleSelection(neuronId);
        refreshNeuronViews.run();
    }

    /**
     * Знімає виділення з усіх нейронів і оновлює візуальний стан.
     */
    public void clear() {
        state.clearSelection();
        refreshNeuronViews.run();
    }

    /**
     * Створює групу з вибраних нейронів.
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
     * Прибирає групування для вибраних нейронів.
     */
    public void ungroupSelection() {
        groupService.ungroup(
                new HashSet<>(state.selectedNeuronIds())
        );
        save.run();
        status.accept(localization.text("status.ungrouped"));
    }
}
