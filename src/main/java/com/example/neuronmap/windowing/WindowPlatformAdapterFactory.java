package com.example.neuronmap.windowing;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Визначає адаптер керування вікном для поточної операційної системи.
 * Реєстрація платформних реалізацій зосереджена в одному місці, щоб додавання нової ОС не вимагало
 * узгодження окремих перевірок створення адаптера та підтримки власного заголовка.
 */
public final class WindowPlatformAdapterFactory {

    private static final List<PlatformAdapterRegistration> ADAPTER_REGISTRATIONS = List.of(
            new PlatformAdapterRegistration(
                    normalizedOperatingSystem -> normalizedOperatingSystem.startsWith("windows"),
                    WindowsWindowPlatformAdapter::new
            )
    );

    /** Створює фабрику адаптерів без зовнішніх залежностей. */
    private WindowPlatformAdapterFactory() {
    }

    /**
     * Створює адаптер для поточної операційної системи, якщо для неї зареєстровано платформну реалізацію.
     *
     * @return адаптер поточної ОС або порожнє значення, якщо платформа не підтримується.
     */
    public static Optional<WindowPlatformAdapter> createForCurrentOperatingSystem() {
        return createForOperatingSystem(System.getProperty("os.name"));
    }

    /**
     * Створює адаптер за назвою операційної системи; параметр дає змогу перевірити вибір платформи незалежно від ОС тесту.
     *
     * @param operatingSystem назва операційної системи з властивості {@code os.name}.
     * @return адаптер зареєстрованої платформи або порожнє значення для непідтримуваної системи.
     */
    public static Optional<WindowPlatformAdapter> createForOperatingSystem(String operatingSystem) {
        return findRegistration(operatingSystem).map(PlatformAdapterRegistration::createAdapter);
    }

    /**
     * Перевіряє, чи зареєстровано адаптер власного оформлення для заданої ОС, не створюючи його екземпляр.
     *
     * @param operatingSystem назва операційної системи.
     * @return {@code true}, якщо для ОС зареєстровано платформний адаптер.
     */
    public static boolean supportsCustomChrome(String operatingSystem) {
        return findRegistration(operatingSystem).isPresent();
    }

    /**
     * Знаходить реєстрацію, яка відповідає нормалізованій назві операційної системи.
     *
     * @param operatingSystem початкова назва операційної системи; може бути {@code null}.
     * @return перша відповідна реєстрація або порожнє значення, якщо платформа не підтримується.
     */
    private static Optional<PlatformAdapterRegistration> findRegistration(String operatingSystem) {
        if (operatingSystem == null) {
            return Optional.empty();
        }

        String normalizedOperatingSystem = operatingSystem.toLowerCase(Locale.ROOT);
        return ADAPTER_REGISTRATIONS.stream()
                .filter(registration -> registration.supports(normalizedOperatingSystem))
                .findFirst();
    }

    /** Зберігає єдине правило зіставлення операційної системи та створення її адаптера. */
    private record PlatformAdapterRegistration(
            Predicate<String> normalizedOperatingSystemMatcher,
            Supplier<? extends WindowPlatformAdapter> adapterSupplier
    ) {

        /**
         * Перевіряє повноту реєстрації до додавання її до фабрики.
         *
         * @param normalizedOperatingSystemMatcher предикат зіставлення нормалізованої назви ОС.
         * @param adapterSupplier постачальник екземплярів адаптера для зареєстрованої платформи.
         * @throws NullPointerException якщо предикат або постачальник адаптера не задано.
         */
        private PlatformAdapterRegistration {
            Objects.requireNonNull(normalizedOperatingSystemMatcher, "normalizedOperatingSystemMatcher");
            Objects.requireNonNull(adapterSupplier, "adapterSupplier");
        }

        /**
         * Перевіряє, чи підходить реєстрація для вже нормалізованої назви ОС.
         *
         * @param normalizedOperatingSystem назва ОС у нижньому регістрі.
         * @return {@code true}, якщо реєстрація підтримує вказану ОС.
         */
        private boolean supports(String normalizedOperatingSystem) {
            return normalizedOperatingSystemMatcher.test(normalizedOperatingSystem);
        }

        /**
         * Створює новий екземпляр адаптера для зареєстрованої платформи.
         *
         * @return новий платформний адаптер.
         */
        private WindowPlatformAdapter createAdapter() {
            return adapterSupplier.get();
        }
    }
}
