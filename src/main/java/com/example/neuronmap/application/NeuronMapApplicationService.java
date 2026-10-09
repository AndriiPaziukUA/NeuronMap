package com.example.neuronmap.application;

import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.persistence.MapRepository;
import com.example.neuronmap.service.ConnectionService;
import com.example.neuronmap.service.GroupService;
import com.example.neuronmap.service.MapService;
import com.example.neuronmap.service.NeuronService;

/**
 * Створює та надає служби, через які інші компоненти виконують операції над картою.
 */
public final class NeuronMapApplicationService {

    private final MapService mapService;
    private final NeuronService neuronService;
    private final ConnectionService connectionService;
    private final GroupService groupService;

    /**
     * Повертає результат операції «нейрон карта служба».
     *
     * @param model модель карти нейронів.
     *
     * @param repository значення, що визначає відповідну операцію для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public NeuronMapApplicationService(
            NeuronMapModel model,
            MapRepository repository
    ) {
        if (model == null) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalArgumentException("model must not be null");
        }
        if (repository == null) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalArgumentException("repository must not be null");
        }

        mapService = new MapService(model, repository);
        neuronService = new NeuronService(model);
        connectionService = new ConnectionService(model);
        groupService = new GroupService(model);
    }

    /**
     * Повертає результат операції «карта».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public MapService map() {
        return mapService;
    }

    /**
     * Повертає результат операції «нейрони».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public NeuronService neurons() {
        return neuronService;
    }

    /**
     * Повертає результат операції «звʼязки».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public ConnectionService connections() {
        return connectionService;
    }

    /**
     * Повертає результат операції «групи».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public GroupService groups() {
        return groupService;
    }
}
