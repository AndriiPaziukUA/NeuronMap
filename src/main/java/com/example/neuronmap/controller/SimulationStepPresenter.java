package com.example.neuronmap.controller;

import com.example.neuronmap.view.NeuronView;
import com.example.neuronmap.view.WorkspaceView;

import java.util.Map;

/**
 * Зберігає сумісний тип контролерного пакета та передає відображення компоненту пакета view.
 */
public final class SimulationStepPresenter extends com.example.neuronmap.view.SimulationStepPresenter {

    /**
     * Створює адаптер для подання результатів симуляції.
     *
     * @param workspace полотно редактора.
     * @param neuronViews візуальні подання нейронів за їхніми ідентифікаторами.
     * @param refreshNeuronViews дія оновлення візуальних подань нейронів.
     */
    public SimulationStepPresenter(
            WorkspaceView workspace,
            Map<String, NeuronView> neuronViews,
            Runnable refreshNeuronViews
    ) {
        super(
                workspace,
                neuronViews,
                refreshNeuronViews,
                new PulseAnimationController(workspace)
        );
    }
}
