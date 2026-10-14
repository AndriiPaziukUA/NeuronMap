package com.example.neuronmap.application;

import com.example.neuronmap.project.ProjectDescriptor;

import java.util.Objects;

/**
 * Визначає та виконує початкове наповнення нового тимчасового проєкту демонстраційною картою.
 * Правило застосовується після завантаження проєкту й не змінює збережені або вже заповнені карти.
 */
public final class InitialProjectInitializer {

    private final NeuronMapApplicationService application;

    /**
     * Створює сценарій початкової ініціалізації з доступом до служб активної карти.
     *
     * @param application прикладна служба, що надає модель та служби роботи з картою.
     * @throws NullPointerException якщо прикладну службу не задано.
     */
    public InitialProjectInitializer(NeuronMapApplicationService application) {
        this.application = Objects.requireNonNull(application, "application");
    }

    /**
     * Створює демонстраційну карту лише для порожнього проєкту, який ще не було збережено.
     *
     * @param project опис поточного проєкту.
     * @return {@code true}, якщо демонстраційні дані було створено; інакше {@code false}.
     * @throws NullPointerException якщо опис проєкту не задано.
     * @throws IllegalArgumentException якщо служби нейронів і зв’язків працюють із різними моделями.
     */
    public boolean initializeIfRequired(ProjectDescriptor project) {
        Objects.requireNonNull(project, "project");
        if (project.isPersisted() || !application.map().isEmpty()) {
            return false;
        }

        DemoMapFactory.populate(application.neurons(), application.connections());
        return true;
    }
}
