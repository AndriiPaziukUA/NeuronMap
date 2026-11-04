package com.example.neuronmap.app;

import com.example.neuronmap.application.NeuronMapApplicationService;
import com.example.neuronmap.config.AppConfig;
import com.example.neuronmap.coordinator.MapEditorCoordinator;
import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.persistence.MapRepositoryFactory;
import com.example.neuronmap.persistence.project.ProjectStorageDirectoryResolver;
import com.example.neuronmap.persistence.settings.GlobalSettingsStore;
import com.example.neuronmap.service.ProjectCatalogService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Перевіряє внутрішній контракт фасаду редактора та межі модульної інкапсуляції. */
final class NeuronMapEditorTest {

    /** Перевіряє ін'єкцію служб без експонування внутрішніх типів через API експортованого пакета. */
    @Test
    void requiresInjectedLocalizationCatalogAndRepositoryFactory() {
        List<Constructor<?>> constructors = Arrays.stream(NeuronMapEditor.class.getDeclaredConstructors()).toList();
        Class<?>[] expectedSignature = {
                NeuronMapApplicationService.class,
                AppConfig.class,
                LocalizationService.class,
                ProjectCatalogService.class,
                MapRepositoryFactory.class,
                Runnable.class
        };

        Constructor<?> injectedConstructor = constructors.stream()
                .filter(constructor -> Arrays.equals(constructor.getParameterTypes(), expectedSignature))
                .findFirst()
                .orElseThrow();

        assertFalse(Modifier.isPublic(NeuronMapEditor.class.getModifiers()));
        assertFalse(Modifier.isPublic(injectedConstructor.getModifiers()));
        assertFalse(constructors.stream().anyMatch(constructor -> Arrays.equals(
                constructor.getParameterTypes(), new Class<?>[]{
                        NeuronMapApplicationService.class,
                        AppConfig.class,
                        LocalizationService.class,
                        ProjectCatalogService.class
                })));
        assertFalse(constructors.stream().anyMatch(constructor -> Arrays.equals(
                constructor.getParameterTypes(), new Class<?>[]{
                        NeuronMapApplicationService.class,
                        AppConfig.class,
                        LocalizationService.class,
                        ProjectCatalogService.class,
                        Runnable.class
                })));
    }

    /** Переконується, що фасад не утримує залежностей від файлового сховища глобальних налаштувань. */
    @Test
    void doesNotHoldPersistenceInfrastructure() {
        assertFalse(Arrays.stream(NeuronMapEditor.class.getDeclaredFields())
                .map(Field::getType)
                .anyMatch(type -> type == GlobalSettingsStore.class
                        || type == ProjectStorageDirectoryResolver.class));
    }

    /** Перевіряє, що фасад залишається внутрішньою частиною прикладного пакета. */
    @Test
    void belongsToApplicationPackage() {
        assertEquals("com.example.neuronmap.app", NeuronMapEditor.class.getPackageName());
    }

    /** Перевіряє, що складання графа редактора залишається внутрішнім механізмом координаторів. */
    @Test
    void keepsComponentFactoryInternalAndLifecycleApiStable() throws ClassNotFoundException {
        Class<?> factory = Class.forName("com.example.neuronmap.coordinator.MapEditorComponentFactory");
        assertFalse(Modifier.isPublic(factory.getModifiers()));

        List<String> publicCoordinatorMethods = Arrays.stream(MapEditorCoordinator.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(Method::getName)
                .sorted()
                .toList();

        assertEquals(List.of("centerInitialView", "createScene", "shutdown"), publicCoordinatorMethods);
    }
}
