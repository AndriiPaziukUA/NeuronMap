package com.example.neuronmap.application;

import com.example.neuronmap.model.NeuronType;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Зберігає поточний стан редактора: вибір обʼєктів, режим роботи та дані взаємодії.
 */
public final class EditorState {

    /**
     * Компонент Mode у складі NeuronMap. Його призначення та параметри операцій описані над відповідними методами. Визначає обмежений набір допустимих варіантів.
     */
    public enum Mode {
        IDLE,
        ADD_NEURON,
        CREATE_CONNECTION,
        DELETE_CONNECTION,
        ROTATE_NEURON
    }

    private double zoom;
    private double panX;
    private double panY;

    private Mode mode = Mode.IDLE;
    private NeuronType pendingNeuronType;
    private String connectionSourceId;
    private String deleteConnectionNeuronId;
    private String rotatingNeuronId;
    private String selectedNeuronForMenu;

    private final Set<String> selectedNeuronIds =
            new LinkedHashSet<>();

    /**
     * Повертає результат операції «стан».
     *
     * @param zoom значення, що визначає масштаб для цієї операції.
     *
     * @param panX значення, що визначає відповідну операцію для цієї операції.
     *
     * @param panY значення, що визначає відповідну операцію для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public EditorState(
            double zoom,
            double panX,
            double panY
    ) {
        this.zoom = zoom;
        this.panX = panX;
        this.panY = panY;
    }

    /**
     * Повертає результат операції «масштаб».
     *
     * @return числове значення, визначене методом.
     */
    public double zoom() {
        return zoom;
    }

    /**
     * Задає або оновлює значення, повʼязані з «масштаб».
     *
     * @param zoom значення, що визначає масштаб для цієї операції.
     */
    public void setZoom(double zoom) {
        this.zoom = zoom;
    }

    /**
     * Повертає результат операції «відповідну операцію».
     *
     * @return числове значення, визначене методом.
     */
    public double panX() {
        return panX;
    }

    /**
     * Задає або оновлює значення, повʼязані з «відповідну операцію».
     *
     * @param panX значення, що визначає відповідну операцію для цієї операції.
     */
    public void setPanX(double panX) {
        this.panX = panX;
    }

    /**
     * Повертає результат операції «відповідну операцію».
     *
     * @return числове значення, визначене методом.
     */
    public double panY() {
        return panY;
    }

    /**
     * Задає або оновлює значення, повʼязані з «відповідну операцію».
     *
     * @param panY значення, що визначає відповідну операцію для цієї операції.
     */
    public void setPanY(double panY) {
        this.panY = panY;
    }

    /**
     * Повертає результат операції «відповідну операцію».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public Mode mode() {
        return mode;
    }

    /**
     * Перевіряє, чи виконується умова «відповідну операцію».
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
    public boolean isIdle() {
        return mode == Mode.IDLE;
    }

    /**
     * Перевіряє, чи виконується умова «відповідну операцію».
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
    public boolean isSpecialModeActive() {
        return mode != Mode.IDLE;
    }

    /**
     * Виконує операцію «додати нейрон».
     *
     * @param type тип обʼєкта.
     */
    public void enterAddNeuronMode(NeuronType type) {
        clearTransientModes();
        mode = Mode.ADD_NEURON;
        pendingNeuronType = type;
    }

    /**
     * Повертає результат операції «очікуваний нейрон тип».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public NeuronType pendingNeuronType() {
        return pendingNeuronType;
    }

    /**
     * Виконує операцію «створити звʼязок».
     *
     * @param sourceId значення, що визначає джерело ідентифікатор для цієї операції.
     */
    public void enterCreateConnectionMode(String sourceId) {
        clearTransientModes();
        mode = Mode.CREATE_CONNECTION;
        connectionSourceId = sourceId;
    }

    /**
     * Повертає результат операції «звʼязок джерело ідентифікатор».
     *
     * @return текстове значення, сформоване або знайдене методом.
     */
    public String connectionSourceId() {
        return connectionSourceId;
    }

    /**
     * Виконує операцію «видалити звʼязок».
     *
     * @param neuronId ідентифікатор нейрона.
     */
    public void enterDeleteConnectionMode(String neuronId) {
        clearTransientModes();
        mode = Mode.DELETE_CONNECTION;
        deleteConnectionNeuronId = neuronId;
    }

    /**
     * Видаляє або скидає дані, повʼязані з «звʼязок нейрон ідентифікатор».
     *
     * @return текстове значення, сформоване або знайдене методом.
     */
    public String deleteConnectionNeuronId() {
        return deleteConnectionNeuronId;
    }

    /**
     * Виконує операцію «повертати».
     *
     * @param neuronId ідентифікатор нейрона.
     */
    public void enterRotateMode(String neuronId) {
        clearTransientModes();
        mode = Mode.ROTATE_NEURON;
        rotatingNeuronId = neuronId;
    }

    /**
     * Повертає результат операції «нейрон ідентифікатор».
     *
     * @return текстове значення, сформоване або знайдене методом.
     */
    public String rotatingNeuronId() {
        return rotatingNeuronId;
    }

    /**
     * Задає або оновлює значення, повʼязані з «вибраний нейрон для меню».
     *
     * @param neuronId ідентифікатор нейрона.
     */
    public void setSelectedNeuronForMenu(String neuronId) {
        selectedNeuronForMenu = neuronId;
    }

    /**
     * Повертає результат операції «вибраний нейрон для меню».
     *
     * @return текстове значення, сформоване або знайдене методом.
     */
    public String selectedNeuronForMenu() {
        return selectedNeuronForMenu;
    }

    /**
     * Повертає результат операції «вибраний нейрон ідентифікатори».
     *
     * @return колекцію результатів; якщо елементів немає, колекція порожня.
     */
    public Set<String> selectedNeuronIds() {
        return Collections.unmodifiableSet(
                selectedNeuronIds
        );
    }

    /**
     * Виконує операцію «лише».
     *
     * @param neuronId ідентифікатор нейрона.
     */
    public void selectOnly(String neuronId) {
        selectedNeuronIds.clear();
        selectedNeuronIds.add(neuronId);
    }

    /**
     * Перемикає стан «вибір».
     *
     * @param neuronId ідентифікатор нейрона.
     */
    public void toggleSelection(String neuronId) {
        if (!selectedNeuronIds.add(neuronId)) {
            selectedNeuronIds.remove(neuronId);
        }
    }

    /**
     * Видаляє або скидає дані, повʼязані з «вибір».
     */
    public void clearSelection() {
        selectedNeuronIds.clear();
    }

    /**
     * Видаляє або скидає дані, повʼязані з «тимчасовий».
     */
    public void clearTransientModes() {
        pendingNeuronType = null;
        connectionSourceId = null;
        deleteConnectionNeuronId = null;
        rotatingNeuronId = null;
        mode = Mode.IDLE;
    }

    /**
     * Видаляє або скидає дані, повʼязані з «до».
     */
    public void resetToIdle() {
        clearTransientModes();
        selectedNeuronForMenu = null;
    }
}
