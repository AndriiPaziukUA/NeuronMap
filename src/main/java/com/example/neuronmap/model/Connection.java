package com.example.neuronmap.model;

import java.util.Objects;

/**
 * Повертає результат операції «звʼязок».
 *
 * @param id ідентифікатор обʼєкта.
 *
 * @param sourceId значення, що визначає джерело ідентифікатор для цієї операції.
 *
 * @param targetId значення, що визначає кінцевий ідентифікатор для цієї операції.
 *
 * @return значення або обʼєкт, визначений описаною операцією.
 */
/**
 * Описує спрямований звʼязок між двома нейронами карти.
 */
public record Connection(
        String id,
        String sourceId,
        String targetId
) {
    public Connection {
        if (id == null || id.isBlank()) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
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
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalArgumentException(
                    "A neuron cannot connect to itself."
            );
        }
    }
}
