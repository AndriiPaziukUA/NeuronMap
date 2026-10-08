package com.example.neuronmap.application;

import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.persistence.MapRepository;
import com.example.neuronmap.service.ConnectionService;
import com.example.neuronmap.service.GroupService;
import com.example.neuronmap.service.MapService;
import com.example.neuronmap.service.NeuronService;

/** Application-level service graph and entry point for the specialized services. */
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
}
