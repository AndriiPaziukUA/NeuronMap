package com.example.neuronmap.application;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.persistence.CameraState;
import com.example.neuronmap.persistence.MapRepository;
import com.example.neuronmap.service.ConnectionService;
import com.example.neuronmap.service.GroupService;
import com.example.neuronmap.service.MapService;
import com.example.neuronmap.service.NeuronService;

import java.nio.file.Path;
import java.util.Set;

/**
 * Backward-compatible application facade and composition root for the
 * service layer. New feature code should depend on the specialized services.
 */
public final class NeuronMapApplicationService {

    private final MapService mapService;
    private final NeuronService neuronService;
    private final ConnectionService connectionService;
    private final GroupService groupService;

    public NeuronMapApplicationService(
            NeuronMapModel model,
            MapRepository repository
    ) {
        if (model == null) {
            throw new IllegalArgumentException("model must not be null");
        }
        if (repository == null) {
            throw new IllegalArgumentException("repository must not be null");
        }

        mapService = new MapService(model, repository);
        neuronService = new NeuronService(model);
        connectionService = new ConnectionService(model);
        groupService = new GroupService(model);
    }

    public MapService map() {
        return mapService;
    }

    public NeuronService neurons() {
        return neuronService;
    }

    public ConnectionService connections() {
        return connectionService;
    }

    public GroupService groups() {
        return groupService;
    }

    public void load() {
        mapService.load();
    }

    public boolean isEmpty() {
        return mapService.isEmpty();
    }

    public NeuronMapModel model() {
        return mapService.model();
    }

    public CameraState loadCameraState() {
        return mapService.loadCameraState();
    }

    public double loadSimulationTickMillis(double fallbackMillis) {
        return mapService.loadSimulationTickMillis(fallbackMillis);
    }

    public Path databasePath() {
        return mapService.databasePath();
    }

    public Neuron createNeuron(
            NeuronType type,
            double x,
            double y
    ) {
        return neuronService.create(type, x, y);
    }

    public boolean createConnection(
            String sourceId,
            String targetId
    ) {
        return connectionService.create(sourceId, targetId);
    }

    public boolean removeConnection(String id) {
        return connectionService.remove(id);
    }

    public int removeConnectionsBetween(
            String firstNeuronId,
            String secondNeuronId
    ) {
        return connectionService.removeBetween(
                firstNeuronId,
                secondNeuronId
        );
    }

    public Neuron removeNeuron(String neuronId) {
        return neuronService.remove(neuronId);
    }

    public boolean toggleNeuronType(String neuronId) {
        return neuronService.toggleType(neuronId);
    }

    public boolean toggleNeuronDirection(String neuronId) {
        return neuronService.toggleDirection(neuronId);
    }

    public void createGroup(Set<String> memberIds) {
        groupService.create(memberIds);
    }

    public void ungroup(Set<String> memberIds) {
        groupService.ungroup(memberIds);
    }

    public void saveSimulationTickMillis(double millis) {
        mapService.saveSimulationTickMillis(millis);
    }

    public void save(EditorState state) {
        mapService.save(state);
    }

    public void close() {
        mapService.close();
    }
}
