package com.example.neuronmap.persistence;

/**
 * Представляє помилку збереження або завантаження даних і зберігає першопричину збою.
 */
public final class PersistenceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Створює виняток збереження з повідомленням і першопричиною помилки.
     *
     * @param message значення «message», яке використовується в цьому методі.
     * @param cause першопричина помилки, яку потрібно передати разом із повідомленням.
     */
    public PersistenceException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
