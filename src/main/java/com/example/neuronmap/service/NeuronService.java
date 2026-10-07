package com.example.neuronmap.service;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronType;

import java.util.Collection;
import java.util.Objects;

/** Business/application operations for individual neurons. */
public final class NeuronService {

    private final NeuronMapModel model;

    public NeuronService(NeuronMapModel model) {
        this.model = Objects.requireNonNull(model, "model");
    }

    public NeuronMapModel model() { return model; }

    public Collection<Neuron> neurons() { return model.neurons(); }

    public Neuron find(String neuronId) {
        if (neuronId == null || neuronId.isBlank()) return null;
        return model.neuron(neuronId);
    }

    public NeuronPresentation presentation(String neuronId) {
        if (neuronId == null || neuronId.isBlank()) return null;
        return model.presentation(neuronId);
    }

    public Neuron create(NeuronType type, double x, double y) {
        return model.createNeuron(type, x, y);
    }

    public Neuron remove(String neuronId) {
        if (neuronId == null || neuronId.isBlank()) return null;
        return model.removeNeuron(neuronId);
    }

    public boolean toggleType(String neuronId) {
        Neuron neuron = find(neuronId);
        if (neuron == null) return false;
        neuron.setType(
                neuron.type() == NeuronType.EXCITATORY
                        ? NeuronType.INHIBITORY
                        : NeuronType.EXCITATORY
        );
        return true;
    }

    public boolean toggleDirection(String neuronId) {
        NeuronPresentation presentation = presentation(neuronId);
        if (presentation == null) return false;
        presentation.toggleDirection();
        return true;
    }

    public boolean updateSettings(String neuronId, int signalStrength, int activationThreshold) {
        Neuron neuron = find(neuronId);
        if (neuron == null) return false;
        neuron.setSignalStrength(signalStrength);
        neuron.setActivationThreshold(activationThreshold);
        return true;
    }

    public boolean move(String neuronId, double dx, double dy) {
        NeuronPresentation presentation = presentation(neuronId);
        if (presentation == null) return false;
        presentation.moveBy(dx, dy);
        return true;
    }

    public boolean setRotation(String neuronId, double degrees) {
        NeuronPresentation presentation = presentation(neuronId);
        if (presentation == null) return false;
        presentation.setRotationDegrees(degrees);
        return true;
    }

    public void clearActivations() { model.clearActivations(); }

    public boolean setActivation(String neuronId, int activation) {
        Neuron neuron = find(neuronId);
        if (neuron == null) return false;
        neuron.setActivation(activation);
        return true;
    }
}
