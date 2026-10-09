package com.example.neuronmap.persistence;

/**
 * Повідомляє про помилку під час читання або запису збережених даних.
 */
public final class PersistenceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Повертає результат операції «виняток».
     *
     * @param message повідомлення для показу чи журналювання.
     *
     * @param cause значення, що визначає відповідну операцію для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public PersistenceException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
