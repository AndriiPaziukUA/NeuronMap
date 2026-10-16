package com.example.neuronmap.application;

import com.example.neuronmap.model.CameraState;
import com.example.neuronmap.model.NeuronType;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Зберігає стан взаємодії редактора: перевірений стан камери, вибрані нейрони та параметри активного спеціального режиму.
 */
public final class EditorState {

    /**
     * Описує п’ять режимів взаємодії: звичайний стан, додавання нейрона, створення або видалення зв’язку й обертання нейрона.
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
    private String selectedNeuronForMenu;

    private final Set<String> selectedNeuronIds = new LinkedHashSet<>();

    /**
     * Створює стан редактора, перевіряючи значення камери через спільний контракт {@link CameraState}.
     *
     * @param zoom коефіцієнт масштабування в підтримуваному діапазоні.
     * @param panX скінченне горизонтальне зміщення камери.
     * @param panY скінченне вертикальне зміщення камери.
     * @throws IllegalArgumentException якщо значення камери не відповідають контракту.
     */
    public EditorState(double zoom, double panX, double panY) {
        CameraState camera = validatedCameraState(zoom, panX, panY);
        this.zoom = camera.zoom();
        this.panX = camera.panX();
        this.panY = camera.panY();
    }

    /**
     * Повертає поточний коефіцієнт масштабування редактора.
     *
     * @return поточний коефіцієнт масштабування редактора.
     */
    public double zoom() {
        return zoom;
    }

    /**
     * Установлює коефіцієнт масштабування камери, не змінюючи стан у разі некоректного значення.
     *
     * @param zoom коефіцієнт масштабування в підтримуваному діапазоні.
     * @throws IllegalArgumentException якщо новий стан камери не відповідає спільному контракту.
     */
    public void setZoom(double zoom) {
        CameraState camera = validatedCameraState(zoom, panX, panY);
        this.zoom = camera.zoom();
    }

    /**
     * Повертає горизонтальне зміщення камери.
     *
     * @return горизонтальне зміщення камери.
     */
    public double panX() {
        return panX;
    }

    /**
     * Установлює горизонтальне зміщення камери, перевіряючи весь новий стан до зміни поля.
     *
     * @param panX скінченне горизонтальне зміщення камери.
     * @throws IllegalArgumentException якщо новий стан камери не відповідає спільному контракту.
     */
    public void setPanX(double panX) {
        CameraState camera = validatedCameraState(zoom, panX, panY);
        this.panX = camera.panX();
    }

    /**
     * Повертає вертикальне зміщення камери.
     *
     * @return вертикальне зміщення камери.
     */
    public double panY() {
        return panY;
    }

    /**
     * Установлює вертикальне зміщення камери, перевіряючи весь новий стан до зміни поля.
     *
     * @param panY скінченне вертикальне зміщення камери.
     * @throws IllegalArgumentException якщо новий стан камери не відповідає спільному контракту.
     */
    public void setPanY(double panY) {
        CameraState camera = validatedCameraState(zoom, panX, panY);
        this.panY = camera.panY();
    }

    /**
     * Повертає поточний режим взаємодії.
     *
     * @return поточний режим взаємодії.
     */
    public Mode mode() {
        return mode;
    }

    /**
     * Перевіряє, чи немає активного спеціального режиму.
     *
     * @return {@code true}, якщо режим взаємодії звичайний; інакше {@code false}.
     */
    public boolean isIdle() {
        return mode == Mode.IDLE;
    }

    /**
     * Перевіряє, чи активний спеціальний режим взаємодії.
     *
     * @return {@code true}, якщо активний неосновний режим; інакше {@code false}.
     */
    public boolean isSpecialModeActive() {
        return mode != Mode.IDLE;
    }

    /**
     * Вмикає режим додавання нейрона та запам’ятовує тип майбутнього нейрона.
     *
     * @param type тип нейрона або елемента.
     */
    public void enterAddNeuronMode(NeuronType type) {
        clearTransientModes();
        mode = Mode.ADD_NEURON;
        pendingNeuronType = type;
    }

    /**
     * Повертає тип нейрона, вибраний для наступного додавання, або {@code null}.
     *
     * @return тип нейрона для наступного додавання або {@code null}.
     */
    public NeuronType pendingNeuronType() {
        return pendingNeuronType;
    }

    /**
     * Вмикає створення зв’язку та зберігає ідентифікатор його початкового нейрона.
     *
     * @param sourceId ідентифікатор початкового нейрона зв’язку.
     */
    public void enterCreateConnectionMode(String sourceId) {
        clearTransientModes();
        mode = Mode.CREATE_CONNECTION;
        connectionSourceId = sourceId;
    }

    /**
     * Повертає ідентифікатор початкового нейрона нового зв’язку.
     *
     * @return ідентифікатор початкового нейрона нового зв’язку.
     */
    public String connectionSourceId() {
        return connectionSourceId;
    }

    /**
     * Вмикає видалення зв’язку, вибраного від указаного нейрона.
     *
     * @param neuronId ідентифікатор нейрона.
     */
    public void enterDeleteConnectionMode(String neuronId) {
        clearTransientModes();
        mode = Mode.DELETE_CONNECTION;
        deleteConnectionNeuronId = neuronId;
    }

    /**
     * Повертає ідентифікатор нейрона, від якого вибирають зв’язок для видалення.
     *
     * @return ідентифікатор нейрона для видалення зв’язку.
     */
    public String deleteConnectionNeuronId() {
        return deleteConnectionNeuronId;
    }

    /**
     * Вмикає режим обертання нейрона.
     */
    public void enterRotateMode() {
        clearTransientModes();
        mode = Mode.ROTATE_NEURON;
    }

    /**
     * Зберігає ідентифікатор нейрона, для якого потрібно показати контекстне меню.
     *
     * @param neuronId ідентифікатор нейрона.
     */
    public void setSelectedNeuronForMenu(String neuronId) {
        selectedNeuronForMenu = neuronId;
    }

    /**
     * Повертає ідентифікатор нейрона для контекстного меню.
     *
     * @return ідентифікатор нейрона для контекстного меню.
     */
    public String selectedNeuronForMenu() {
        return selectedNeuronForMenu;
    }

    /**
     * Повертає незмінне подання ідентифікаторів вибраних нейронів.
     *
     * @return незмінне подання ідентифікаторів вибраних нейронів.
     */
    public Set<String> selectedNeuronIds() {
        return Collections.unmodifiableSet(selectedNeuronIds);
    }

    /**
     * Замінює поточний вибір одним указаним нейроном.
     *
     * @param neuronId ідентифікатор нейрона.
     */
    public void selectOnly(String neuronId) {
        selectedNeuronIds.clear();
        selectedNeuronIds.add(neuronId);
    }

    /**
     * Додає нейрон до вибору або прибирає його, якщо він уже вибраний.
     *
     * @param neuronId ідентифікатор нейрона.
     */
    public void toggleSelection(String neuronId) {
        if (!selectedNeuronIds.add(neuronId)) {
            selectedNeuronIds.remove(neuronId);
        }
    }

    /**
     * Очищає вибір нейронів.
     */
    public void clearSelection() {
        selectedNeuronIds.clear();
    }

    /**
     * Скидає активний спеціальний режим і очищає його тимчасові параметри.
     */
    public void clearTransientModes() {
        pendingNeuronType = null;
        connectionSourceId = null;
        deleteConnectionNeuronId = null;
        mode = Mode.IDLE;
    }

    /**
     * Скидає спеціальний режим та вибір нейрона для контекстного меню.
     */
    public void resetToIdle() {
        clearTransientModes();
        selectedNeuronForMenu = null;
    }

    /**
     * Перевіряє кандидатний стан камери за спільним контрактом моделі.
     *
     * @param zoom масштаб камери.
     * @param panX горизонтальне зміщення.
     * @param panY вертикальне зміщення.
     * @return перевірений стан камери.
     * @throws IllegalArgumentException якщо будь-яке значення порушує контракт камери.
     */
    private static CameraState validatedCameraState(double zoom, double panX, double panY) {
        return new CameraState(zoom, panX, panY);
    }
}
