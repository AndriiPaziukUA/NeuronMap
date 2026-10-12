package com.example.neuronmap.i18n;

/**
 * Визначає контракт читання та збереження вибраної мови інтерфейсу.
 */
public interface LanguagePreferenceStore {

    /**
     * Повертає збережений мовний тег.
     *
     * @return мовний тег або {@code null}, якщо вибір мови ще не збережено.
     */
    String loadLanguageTag();

    /**
     * Зберігає вибраний мовний тег.
     *
     * @param languageTag мовний тег або {@code null}, щоб очистити збережене значення.
     */
    void saveLanguageTag(String languageTag);
}
