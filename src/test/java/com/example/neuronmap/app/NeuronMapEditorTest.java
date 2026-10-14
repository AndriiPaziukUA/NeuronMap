package com.example.neuronmap.app;

import com.example.neuronmap.application.NeuronMapApplicationService;
import com.example.neuronmap.config.AppConfig;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.persistence.GlobalSettingsStore;
import com.example.neuronmap.persistence.MapRepositoryFactory;
import com.example.neuronmap.persistence.ProjectStorageDirectoryResolver;
import com.example.neuronmap.service.ProjectCatalogService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Перевіряє контракт фасаду редактора на рівні застосунку та відсутність у ньому відповідальності за файлову інфраструктуру.
 */
final class NeuronMapEditorTest {

    /**
     * Перевіряє, що фасад отримує необхідні служби й фабрику репозиторіїв через єдиний публічний конструктор.
     */
    @Test
    void requiresInjectedLocalizationCatalogAndRepositoryFactory() {
        List<Class<?>[]> signatures = Arrays.stream(NeuronMapEditor.class.getConstructors())
                .map(Constructor::getParameterTypes)
                .toList();

        assertTrue(signatures.stream().anyMatch(signature -> Arrays.equals(signature, new Class<?>[]{
                NeuronMapApplicationService.class,
                AppConfig.class,
                LocalizationService.class,
                ProjectCatalogService.class,
                MapRepositoryFactory.class,
                Runnable.class
        })));
        assertFalse(signatures.stream().anyMatch(signature -> Arrays.equals(signature, new Class<?>[]{
                NeuronMapApplicationService.class,
                AppConfig.class,
                LocalizationService.class,
                ProjectCatalogService.class
        })));
        assertFalse(signatures.stream().anyMatch(signature -> Arrays.equals(signature, new Class<?>[]{
                NeuronMapApplicationService.class,
                AppConfig.class,
                LocalizationService.class,
                ProjectCatalogService.class,
                Runnable.class
        })));
    }

    /**
     * Перевіряє, що фасад не утримує залежностей від сховища глобальних налаштувань або визначника каталогів.
     */
    @Test
    void doesNotHoldPersistenceInfrastructure() {
        assertFalse(Arrays.stream(NeuronMapEditor.class.getDeclaredFields())
                .map(Field::getType)
                .anyMatch(type -> type == GlobalSettingsStore.class
                        || type == ProjectStorageDirectoryResolver.class));
    }

    /**
     * Перевіряє, що клас розташований у прикладному шарі, а не серед контролерів UI-подій.
     */
    @Test
    void belongsToApplicationPackage() {
        assertEquals("com.example.neuronmap.app", NeuronMapEditor.class.getPackageName());
    }
}
