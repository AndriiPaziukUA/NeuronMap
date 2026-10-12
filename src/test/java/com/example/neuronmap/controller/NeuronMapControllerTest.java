package com.example.neuronmap.controller;

import com.example.neuronmap.application.NeuronMapApplicationService;
import com.example.neuronmap.config.AppConfig;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.persistence.GlobalSettingsStore;
import com.example.neuronmap.persistence.ProjectStorageDirectoryResolver;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє, що контролер отримує службу локалізації ззовні та не приймає файлову інфраструктуру.
 */
final class NeuronMapControllerTest {

    /**
     * Перевіряє, що публічні конструктори вимагають передати готову службу локалізації.
     */
    @Test
    void requiresInjectedLocalizationService() {
        List<Class<?>[]> signatures = Arrays.stream(NeuronMapController.class.getConstructors())
                .map(Constructor::getParameterTypes)
                .toList();

        assertTrue(signatures.stream().anyMatch(signature -> Arrays.equals(signature, new Class<?>[]{
                NeuronMapApplicationService.class,
                AppConfig.class,
                LocalizationService.class
        })));
        assertTrue(signatures.stream().anyMatch(signature -> Arrays.equals(signature, new Class<?>[]{
                NeuronMapApplicationService.class,
                AppConfig.class,
                LocalizationService.class,
                Runnable.class
        })));
        assertFalse(signatures.stream().anyMatch(signature -> Arrays.equals(signature, new Class<?>[]{
                NeuronMapApplicationService.class,
                AppConfig.class
        })));
        assertFalse(signatures.stream().anyMatch(signature -> Arrays.equals(signature, new Class<?>[]{
                NeuronMapApplicationService.class,
                AppConfig.class,
                Runnable.class
        })));
    }

    /**
     * Перевіряє, що поля контролера не залежать від сховища налаштувань або визначника каталогів.
     */
    @Test
    void doesNotHoldPersistenceInfrastructure() {
        assertFalse(Arrays.stream(NeuronMapController.class.getDeclaredFields())
                .map(Field::getType)
                .anyMatch(type -> type == GlobalSettingsStore.class
                        || type == ProjectStorageDirectoryResolver.class));
    }
}
