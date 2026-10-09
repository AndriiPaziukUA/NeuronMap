package com.example.neuronmap.model;

import java.util.Objects;

/**
 * Описує напрямлений зв’язок між початковим і кінцевим нейронами.
 * @param id унікальний ідентифікатор зв’язку.
 * @param sourceId ідентифікатор нейрона, з якого виходить зв’язок.
 * @param targetId ідентифікатор нейрона, до якого веде зв’язок.
 */
public record Connection(
        String id,
        String sourceId,
        String targetId
) {
    /**
     * Створює напрямлений зв’язок між різними нейронами та перевіряє його ідентифікатор і кінцеві вузли.
     *
     * @param id унікальний ідентифікатор елемента.
     * @param sourceId ідентифікатор початкового нейрона зв’язку.
     * @param targetId ідентифікатор кінцевого нейрона зв’язку.
     */
    public Connection {
        if (id == null || id.isBlank()) {

            throw new IllegalArgumentException(
                    "Connection id must not be blank."
            );
        }

        sourceId = Objects.requireNonNull(
                sourceId,
                "sourceId"
        );
        targetId = Objects.requireNonNull(
                targetId,
                "targetId"
        );

        if (sourceId.equals(targetId)) {

            throw new IllegalArgumentException(
                    "A neuron cannot connect to itself."
            );
        }
    }
}
