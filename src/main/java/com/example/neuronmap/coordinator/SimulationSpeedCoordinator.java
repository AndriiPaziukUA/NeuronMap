package com.example.neuronmap.coordinator;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.service.MapService;
import com.example.neuronmap.simulation.SimulationSpeed;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/**
 * Координує зміну швидкості симуляції: перевіряє введення, зберігає значення в активному проєкті
 * та передає його компонентам, які відповідають за виконання й відображення симуляції.
 *
 * <p>Збереження виконується через {@link MapService}, який делегує операцію активному репозиторію.
 * Тому параметр швидкості залишається окремим для кожного проєкту та не потрапляє до глобальних налаштувань.</p>
 */
public final class SimulationSpeedCoordinator {

    private final MapService mapService;
    private final DoubleConsumer applySpeedAction;
    private final Runnable persistentProjectChangedAction;
    private final Consumer<String> statusAction;
    private final LocalizationService localization;
    private final double minTickMillis;
    private final double maxTickMillis;

    /**
     * Створює координатор швидкості з доступом до активного сховища та діями застосування результату.
     *
     * @param mapService служба, яка зберігає параметр у поточному репозиторії проєкту.
     * @param applySpeedAction дія застосування швидкості до симуляції та інтерфейсу.
     * @param persistentProjectChangedAction дія оновлення метаданих активного збереженого проєкту.
     * @param statusAction дія показу повідомлення стану користувачу.
     * @param localization служба локалізації повідомлень.
     * @param minTickMillis мінімальна допустима тривалість такту.
     * @param maxTickMillis максимальна допустима тривалість такту.
     * @throws NullPointerException якщо обов'язкову залежність не передано.
     * @throws IllegalArgumentException якщо діапазон швидкості некоректний.
     */
    public SimulationSpeedCoordinator(
            MapService mapService,
            DoubleConsumer applySpeedAction,
            Runnable persistentProjectChangedAction,
            Consumer<String> statusAction,
            LocalizationService localization,
            double minTickMillis,
            double maxTickMillis
    ) {
        this.mapService = Objects.requireNonNull(mapService, "mapService");
        this.applySpeedAction = Objects.requireNonNull(applySpeedAction, "applySpeedAction");
        this.persistentProjectChangedAction = Objects.requireNonNull(
                persistentProjectChangedAction,
                "persistentProjectChangedAction"
        );
        this.statusAction = Objects.requireNonNull(statusAction, "statusAction");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.minTickMillis = minTickMillis;
        this.maxTickMillis = maxTickMillis;
        SimulationSpeed.requireMillis(minTickMillis, minTickMillis, maxTickMillis);
    }

    /**
     * Перевіряє введену тривалість такту й зберігає її в активному репозиторії перед застосуванням.
     * Некоректне введення показує локалізоване повідомлення, а помилки сховища не маскуються під помилки введення.
     *
     * @param text текстове значення тривалості такту в мілісекундах.
     */
    public void changeSpeed(String text) {
        double millis;
        try {
            millis = SimulationSpeed.parseMillis(text, minTickMillis, maxTickMillis);
        } catch (IllegalArgumentException invalidInput) {
            statusAction.accept(localization.text("status.speed_invalid"));
            return;
        }

        mapService.saveSimulationTickMillis(millis);
        applySpeedAction.accept(millis);
        if (mapService.isPersistent()) {
            persistentProjectChangedAction.run();
        }
    }
}
