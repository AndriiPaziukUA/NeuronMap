package com.example.neuronmap.application;

import com.example.neuronmap.model.NeuronType;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Mutable UI interaction state.
 * Domain objects do not contain camera, selection or active-tool state.
 */
public final class EditorState {

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

    public EditorState(
            double zoom,
            double panX,
            double panY
    ) {
        this.zoom = zoom;
        this.panX = panX;
        this.panY = panY;
    }

    public double zoom() {
        return zoom;
    }

    public void setZoom(double zoom) {
        this.zoom = zoom;
    }

    public double panX() {
        return panX;
    }

    public void setPanX(double panX) {
        this.panX = panX;
    }

    public double panY() {
        return panY;
    }

    public void setPanY(double panY) {
        this.panY = panY;
    }

    public Mode mode() {
        return mode;
    }

    public boolean isIdle() {
        return mode == Mode.IDLE;
    }

    public boolean isSpecialModeActive() {
        return mode != Mode.IDLE;
    }

    public void enterAddNeuronMode(NeuronType type) {
        clearTransientModes();
        mode = Mode.ADD_NEURON;
        pendingNeuronType = type;
    }

    public NeuronType pendingNeuronType() {
        return pendingNeuronType;
    }

    public void enterCreateConnectionMode(String sourceId) {
        clearTransientModes();
        mode = Mode.CREATE_CONNECTION;
        connectionSourceId = sourceId;
    }

    public String connectionSourceId() {
        return connectionSourceId;
    }

    public void enterDeleteConnectionMode(String neuronId) {
        clearTransientModes();
        mode = Mode.DELETE_CONNECTION;
        deleteConnectionNeuronId = neuronId;
    }

    public String deleteConnectionNeuronId() {
        return deleteConnectionNeuronId;
    }

    public void enterRotateMode(String neuronId) {
        clearTransientModes();
        mode = Mode.ROTATE_NEURON;
        rotatingNeuronId = neuronId;
    }

    public String rotatingNeuronId() {
        return rotatingNeuronId;
    }

    public void setSelectedNeuronForMenu(String neuronId) {
        selectedNeuronForMenu = neuronId;
    }

    public String selectedNeuronForMenu() {
        return selectedNeuronForMenu;
    }

    public Set<String> selectedNeuronIds() {
        return Collections.unmodifiableSet(
                selectedNeuronIds
        );
    }

    public void selectOnly(String neuronId) {
        selectedNeuronIds.clear();
        selectedNeuronIds.add(neuronId);
    }

    public void toggleSelection(String neuronId) {
        if (!selectedNeuronIds.add(neuronId)) {
            selectedNeuronIds.remove(neuronId);
        }
    }

    public void clearSelection() {
        selectedNeuronIds.clear();
    }

    public void clearTransientModes() {
        pendingNeuronType = null;
        connectionSourceId = null;
        deleteConnectionNeuronId = null;
        rotatingNeuronId = null;
        mode = Mode.IDLE;
    }

    public void resetToIdle() {
        clearTransientModes();
        selectedNeuronForMenu = null;
    }
}
