package com.example.neuronmap.view;

import com.example.neuronmap.model.NeuronType;

/**
 * Створює попереднє графічне зображення нейрона для операції перетягування.
 */
public final class NeuronDragPreviewFactory {

    /**
     * Повертає результат операції «нейрон перетягування попередній перегляд».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    private NeuronDragPreviewFactory() {
    }

    /**
     * Створює обʼєкт із переданих даних «потрібні дані».
     *
     * @param type тип обʼєкта.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public static NeuronDragPreviewView create(NeuronType type) {
        /**
         * Повертає результат операції «нейрон перетягування попередній перегляд відображення».
         *
         * @param type тип обʼєкта.
         *
         * @return значення або обʼєкт, визначений описаною операцією.
         */
        return new NeuronDragPreviewView(type);
    }
}
