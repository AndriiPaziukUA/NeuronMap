package com.example.neuronmap.application;

import com.example.neuronmap.model.NeuronMapModel;
import com.example.neuronmap.persistence.MapRepository;
import com.example.neuronmap.service.ConnectionService;
import com.example.neuronmap.service.GroupService;
import com.example.neuronmap.service.MapService;
import com.example.neuronmap.service.NeuronService;

/**
 * Об’єднує прикладні служби карти, нейронів, зв’язків і груп та надає контролерам єдину точку доступу до них.
 */
public final class NeuronMapApplicationService {

    private final MapService mapService;
    private final NeuronService neuronService;
    private final ConnectionService connectionService;
    private final GroupService groupService;

    /**
     * Створює екземпляр NeuronMapApplicationService та зберігає передані залежності, потрібні для його роботи.
     *
     * @param model модель карти нейронів.
     * @param repository сховище, через яке читають і зберігають карту.
     */
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

    /**
     * Повертає службу, яка завантажує та зберігає карту й налаштування її камери.
     *
     * @return службу, яка завантажує та зберігає карту й налаштування її камери.
     */
    public MapService map() {
        return mapService;
    }

    /**
     * Повертає службу операцій над нейронами.
     *
     * @return службу операцій над нейронами.
     */
    public NeuronService neurons() {
        return neuronService;
    }

    /**
     * Повертає службу створення, пошуку й видалення зв’язків.
     *
     * @return службу створення, пошуку й видалення зв’язків.
     */
    public ConnectionService connections() {
        return connectionService;
    }

    /**
     * Повертає службу групування та переміщення груп нейронів.
     *
     * @return службу групування та переміщення груп нейронів.
     */
    public GroupService groups() {
        return groupService;
    }
}
