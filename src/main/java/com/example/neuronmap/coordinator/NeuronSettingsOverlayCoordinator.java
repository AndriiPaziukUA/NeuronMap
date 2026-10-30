package com.example.neuronmap.coordinator;

import com.example.neuronmap.i18n.LocalizationService;
import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronParameters;
import com.example.neuronmap.model.NeuronType;
import com.example.neuronmap.view.menu.MainMenuView;
import com.example.neuronmap.view.neuron.NeuronSettingsView;
import javafx.beans.value.ChangeListener;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Координує життєвий цикл модальної накладки налаштувань та її взаємодію з головним меню. */
public final class NeuronSettingsOverlayCoordinator {

    private static final Runnable NO_CLOSE_ACTION = () -> { };

    private final StackPane overlayHost;
    private final MainMenuView mainMenu;
    private final LocalizationService localization;
    private Runnable settingsClosedAction = NO_CLOSE_ACTION;
    private boolean suppressCloseAction;
    private boolean disposed;
    private NeuronSettingsView settingsView;
    private ChangeListener<Boolean> mainMenuVisibilityListener;

    private final ChangeListener<Boolean> settingsVisibilityListener =
            (_, _, isVisible) -> {
                if (Boolean.FALSE.equals(isVisible)) {
                    stopWatchingMainMenu();
                    if (!suppressCloseAction) {
                        Runnable action = settingsClosedAction;
                        settingsClosedAction = NO_CLOSE_ACTION;
                        if (!settingsView.isSaving()) {
                            action.run();
                        }
                    }
                }
            };

    /**
     * Створює координатор з явними залежностями контейнера накладок і головного меню.
     * Координатор не шукає елементи в дереві JavaFX та не залежить від структури предків.
     *
     * @param overlayHost контейнер, до якого додаються модальні накладки редактора.
     * @param mainMenu подання головного меню, видимість якого визначає пріоритет накладки.
     * @param localization спільна служба локалізації застосунку.
     * @throws NullPointerException якщо обов'язкову залежність не задано.
     */
    public NeuronSettingsOverlayCoordinator(
            StackPane overlayHost,
            MainMenuView mainMenu,
            LocalizationService localization
    ) {
        this.overlayHost = Objects.requireNonNull(overlayHost, "overlayHost");
        this.mainMenu = Objects.requireNonNull(mainMenu, "mainMenu");
        this.localization = Objects.requireNonNull(localization, "localization");
    }

    /**
     * Показує накладку налаштувань нейрона без додаткової дії після її закриття.
     *
     * @param neuron нейрон, параметри якого редагують.
     * @param onSave дія застосування підтверджених параметрів.
     * @throws NullPointerException якщо нейрон або callback збереження не задано.
     * @throws IllegalStateException якщо координатор уже звільнено.
     */
    public void show(Neuron neuron, Consumer<NeuronParameters> onSave) {
        Objects.requireNonNull(onSave, "onSave");
        showWithName(neuron, (parameters, _) -> onSave.accept(parameters));
    }

    /**
     * Показує накладку налаштувань нейрона з дією після звичайного закриття користувачем.
     *
     * @param neuron нейрон, параметри якого редагують.
     * @param onSave дія застосування підтверджених параметрів.
     * @param onClose дія, яку виконують після звичайного закриття накладки користувачем.
     * @throws NullPointerException якщо обов'язковий аргумент не задано.
     * @throws IllegalStateException якщо координатор уже звільнено.
     */
    public void show(Neuron neuron, Consumer<NeuronParameters> onSave, Runnable onClose) {
        Objects.requireNonNull(onSave, "onSave");
        showWithName(neuron, (parameters, _) -> onSave.accept(parameters), onClose);
    }

    /**
     * Показує накладку для редагування параметрів нейрона та його назви.
     * Для сумісності кількість структури за замовчуванням не задається; повний поточний лічильник
     * передає спеціалізований метод {@link #showWithStructureSettings(Neuron, int, NeuronSettingsView.StructureSettingsSaveAction, Runnable)}.
     *
     * @param neuron нейрон, значення якого редагують.
     * @param onSave дія застосування параметрів і назви.
     * @throws NullPointerException якщо нейрон або callback збереження не задано.
     * @throws IllegalStateException якщо координатор уже звільнено.
     */
    public void showWithName(Neuron neuron, BiConsumer<NeuronParameters, String> onSave) {
        showWithName(neuron, onSave, NO_CLOSE_ACTION);
    }

    /**
     * Показує накладку редагування та виконує дію після її звичайного закриття користувачем.
     * Програмне приховування накладки та відкриття головного меню не викликають цю дію.
     *
     * @param neuron нейрон, значення якого редагують.
     * @param onSave дія застосування параметрів і назви.
     * @param onClose дія, яку виконують після звичайного закриття накладки користувачем.
     * @throws NullPointerException якщо обов'язковий аргумент не задано.
     * @throws IllegalStateException якщо координатор уже звільнено.
     */
    public void showWithName(Neuron neuron, BiConsumer<NeuronParameters, String> onSave, Runnable onClose) {
        Objects.requireNonNull(onSave, "onSave");
        showInternal(neuron, neuron != null && neuron.type() == NeuronType.STRUCTURE ? 0 : -1,
                (parameters, name, ignoredCount, ignoredStrongest) -> onSave.accept(parameters, name), onClose);
    }

    /**
     * Показує налаштування структури зі збереженням сумісності з callback-ом без прапорця конкуренції.
     *
     * @param neuron нейрон структури, параметри якого редагують.
     * @param currentMemberCount поточна кількість безпосередніх учасників структури.
     * @param onSave дія, яка застосовує параметри, назву й бажану кількість.
     * @param onClose дія після звичайного закриття форми користувачем.
     * @throws NullPointerException якщо обов'язковий аргумент не задано.
     * @throws IllegalArgumentException якщо нейрон не є структурою або кількість від'ємна.
     * @throws IllegalStateException якщо координатор уже звільнено.
     */
    @SuppressWarnings("unused") // Публічний адаптер для сумісності зі старим callback без прапорця конкуренції.
    public void showWithStructureCount(
            Neuron neuron,
            int currentMemberCount,
            NeuronSettingsView.SettingsSaveAction onSave,
            Runnable onClose
    ) {
        Objects.requireNonNull(onSave, "onSave");
        showWithStructureSettings(neuron, currentMemberCount,
                (parameters, name, count, ignoredStrongest) -> onSave.accept(parameters, name, count), onClose);
    }

    /**
     * Показує налаштування структури, включно з режимом вибору найсильнішого нейрона.
     *
     * @param neuron нейрон структури, параметри якого редагують.
     * @param currentMemberCount поточна кількість безпосередніх учасників структури.
     * @param onSave дія застосування параметрів, назви, кількості та прапорця конкуренції.
     * @param onClose дія після звичайного закриття форми користувачем.
     * @throws NullPointerException якщо обов'язковий аргумент не задано.
     * @throws IllegalArgumentException якщо нейрон не є структурою або кількість від'ємна.
     * @throws IllegalStateException якщо координатор уже звільнено.
     */
    public void showWithStructureSettings(
            Neuron neuron,
            int currentMemberCount,
            NeuronSettingsView.StructureSettingsSaveAction onSave,
            Runnable onClose
    ) {
        ensureNotDisposed();
        Objects.requireNonNull(neuron, "neuron");
        Objects.requireNonNull(onSave, "onSave");
        Objects.requireNonNull(onClose, "onClose");
        if (neuron.type() != NeuronType.STRUCTURE || currentMemberCount < 0) {
            throw new IllegalArgumentException("A structure and its non-negative member count are required");
        }
        showInternal(neuron, currentMemberCount, onSave, onClose);
    }

    /** Відкриває накладку після перевірки сцени та забезпечує єдиний життєвий цикл слухачів. */
    private void showInternal(
            Neuron neuron,
            int currentMemberCount,
            NeuronSettingsView.StructureSettingsSaveAction onSave,
            Runnable onClose
    ) {
        ensureNotDisposed();
        Objects.requireNonNull(neuron, "neuron");
        Objects.requireNonNull(onSave, "onSave");
        Objects.requireNonNull(onClose, "onClose");
        Scene scene = overlayHost.getScene();
        if (scene == null || mainMenu.isMenuVisible()) {
            hide();
            return;
        }

        hide();
        if (settingsView == null) {
            settingsView = new NeuronSettingsView(localization);
            settingsView.visibleProperty().addListener(settingsVisibilityListener);
        }
        settingsClosedAction = onClose;
        watchMainMenuVisibility();
        try {
            settingsView.showWithStructureSettingsIn(overlayHost, neuron, currentMemberCount, onSave);
        } catch (RuntimeException | Error failure) {
            settingsClosedAction = NO_CLOSE_ACTION;
            try {
                settingsView.hide();
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            stopWatchingMainMenu();
            throw failure;
        }
    }

    /** Приховує накладку без збереження введених параметрів і пригнічує дію звичайного закриття. */
    public void hide() {
        settingsClosedAction = NO_CLOSE_ACTION;
        boolean previousSuppression = suppressCloseAction;
        suppressCloseAction = true;
        try {
            if (settingsView != null) {
                settingsView.hide();
            }
        } finally {
            suppressCloseAction = previousSuppression;
            stopWatchingMainMenu();
        }
    }

    /** Остаточно звільняє подання й слухачі координатора; повторний виклик безпечний. */
    public void dispose() {
        if (disposed) {
            return;
        }
        hide();
        if (settingsView != null) {
            settingsView.visibleProperty().removeListener(settingsVisibilityListener);
            settingsView.dispose();
            settingsView = null;
        }
        disposed = true;
    }

    /** Забороняє використання координатора після звільнення його ресурсів. */
    private void ensureNotDisposed() {
        if (disposed) {
            throw new IllegalStateException("Neuron settings overlay coordinator is disposed");
        }
    }

    /** Підписується на головне меню лише на час показу накладки. */
    private void watchMainMenuVisibility() {
        if (mainMenuVisibilityListener != null) {
            return;
        }
        mainMenuVisibilityListener = (_, _, isVisible) -> {
            if (Boolean.TRUE.equals(isVisible)) {
                hide();
            }
        };
        mainMenu.visibleProperty().addListener(mainMenuVisibilityListener);
    }

    /** Знімає слухач головного меню, завершуючи тимчасову координацію модального стану. */
    private void stopWatchingMainMenu() {
        if (mainMenuVisibilityListener != null) {
            mainMenu.visibleProperty().removeListener(mainMenuVisibilityListener);
        }
        mainMenuVisibilityListener = null;
    }
}
