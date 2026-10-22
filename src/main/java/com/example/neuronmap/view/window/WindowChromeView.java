package com.example.neuronmap.view.window;

import com.example.neuronmap.i18n.LocalizationService;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HeaderBar;
import javafx.scene.layout.HeaderButtonType;
import javafx.scene.layout.HeaderDragType;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Відображає локалізований заголовок редактора всередині системної {@link HeaderBar} JavaFX.
 * Платформний стиль визначає компонування та CSS; переміщення, зміну розміру й семантику кнопок забезпечує JavaFX.
 * У повноекранному режимі заголовок повністю приховується та перестає займати місце в компонуванні.
 */
public final class WindowChromeView extends BorderPane {

    /** Висота смуги заголовка в логічних пікселях. */
    public static final double TITLE_BAR_HEIGHT = 36.0;

    private static final String LABELS_BUNDLE = "i18n.windowChrome";
    private static final String BASE_STYLESHEET = "/window-chrome.css";

    private final Stage stage;
    private final LocalizationService localization;
    private final WindowChromePlatformStyle platformStyle;
    private final HBox titleBar = new HBox();
    private final Label titleLabel = new Label();
    private final Region spacer = new Region();
    private final HBox controlGroup = new HBox();
    private final Button minimizeButton = createButton(
            "window-control-minimize",
            WindowChromeIconFactory.createMinimizeIcon()
    );
    private final Button maximizeButton = createButton(
            "window-control-maximize",
            WindowChromeIconFactory.createMaximizeIcon(false)
    );
    private final Button closeButton = createButton(
            "window-control-close",
            WindowChromeIconFactory.createCloseIcon()
    );
    private final Consumer<Locale> localeChangeListener = ignored -> refreshControlLabels();
    private final ChangeListener<Boolean> maximizedListener =
            (observable, wasMaximized, isMaximized) -> refreshWindowState();
    private final ChangeListener<Boolean> fullScreenListener =
            (observable, wasFullScreen, isFullScreen) -> refreshWindowState();
    private final List<String> addedStylesheets = new ArrayList<>();

    private Scene installedScene;
    private Parent originalRoot;
    private boolean maximized;
    private boolean disposed;
    private boolean extendedHeaderBarInstalled;

    /**
     * Створює заголовок із платформним компонуванням, визначеним поточною ОС.
     *
     * @param stage головне вікно, властивості якого відображає подання.
     * @param localization спільна служба локалізації застосунку.
     * @throws NullPointerException якщо будь-який аргумент не задано.
     */
    public WindowChromeView(Stage stage, LocalizationService localization) {
        this(stage, localization, WindowChromePlatformStyle.current());
    }

    /**
     * Створює заголовок із явно заданим платформним компонуванням для перевірок і контрольованої інтеграції.
     *
     * @param stage головне вікно, властивості якого відображає подання.
     * @param localization спільна служба локалізації застосунку.
     * @param platformStyle платформний стиль компонування та CSS.
     * @throws NullPointerException якщо будь-який аргумент не задано.
     */
    WindowChromeView(
            Stage stage,
            LocalizationService localization,
            WindowChromePlatformStyle platformStyle
    ) {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.platformStyle = Objects.requireNonNull(platformStyle, "platformStyle");

        getStyleClass().addAll("window-chrome-root", platformStyle.rootStyleClass());
        titleBar.getStyleClass().add("window-title-bar");
        titleBar.setMinHeight(TITLE_BAR_HEIGHT);
        titleBar.setPrefHeight(TITLE_BAR_HEIGHT);
        titleBar.setMaxHeight(TITLE_BAR_HEIGHT);
        titleBar.setPadding(new Insets(0.0, 6.0, 0.0, 12.0));
        titleBar.setFillHeight(true);

        titleLabel.getStyleClass().add("window-title");
        titleLabel.setMinWidth(0.0);
        titleLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        titleLabel.textProperty().bind(stage.titleProperty());
        spacer.getStyleClass().add("window-title-spacer");
        HBox.setHgrow(spacer, Priority.ALWAYS);

        controlGroup.getStyleClass().add("window-control-group");
        if (platformStyle.controlsOnLeadingEdge()) {
            controlGroup.getChildren().addAll(closeButton, minimizeButton, maximizeButton);
            // Не дозволяємо StackPane розтягувати групу кнопок на всю ширину заголовка.
            controlGroup.setMaxWidth(Region.USE_PREF_SIZE);
            StackPane centeredHeader = new StackPane(titleLabel, controlGroup);
            centeredHeader.getStyleClass().add("window-title-centered-header");
            centeredHeader.setMaxWidth(Double.MAX_VALUE);
            titleLabel.maxWidthProperty().bind(Bindings.createDoubleBinding(
                    () -> Math.max(
                            0.0,
                            centeredHeader.getWidth()
                                    - (2.0 * controlGroup.getBoundsInParent().getMaxX())
                                    - 16.0
                    ),
                    centeredHeader.widthProperty(),
                    controlGroup.boundsInParentProperty()
            ));
            StackPane.setAlignment(titleLabel, Pos.CENTER);
            StackPane.setAlignment(controlGroup, Pos.CENTER_LEFT);
            HBox.setHgrow(centeredHeader, Priority.ALWAYS);
            titleBar.getChildren().add(centeredHeader);
        } else {
            titleBar.getChildren().addAll(titleLabel, spacer, minimizeButton, maximizeButton, closeButton);
        }
        setTop(titleBar);

        stage.maximizedProperty().addListener(maximizedListener);
        stage.fullScreenProperty().addListener(fullScreenListener);
        localization.addListener(localeChangeListener);
        maximized = stage.isMaximized() || stage.isFullScreen();
        refreshControlLabels();
        refreshWindowState();
    }

    /**
     * Встановлює вміст редактора під заголовком і додає спільні та платформні таблиці стилів.
     * Усі обов'язкові CSS-ресурси перевіряються до зміни кореня сцени.
     *
     * @param scene сцена з уже створеним редактором.
     * @throws NullPointerException якщо сцену не задано.
     * @throws IllegalStateException якщо ресурс стилів відсутній, подання вже встановлено або його очищено.
     */
    public void installInto(Scene scene) {
        ensureNotDisposed("install into a Scene");
        Objects.requireNonNull(scene, "scene");
        Parent editorContent = scene.getRoot();
        if (installedScene != null) {
            if (installedScene == scene && editorContent == this) {
                return;
            }
            throw new IllegalStateException("Window chrome view is already installed");
        }
        if (editorContent == this) {
            throw new IllegalStateException("Scene already uses this view without an installation record");
        }

        URL baseStylesheet = requireStylesheet(BASE_STYLESHEET);
        URL platformStylesheet = platformStyle.stylesheetResource()
                .map(this::requireStylesheet)
                .orElse(null);

        installedScene = scene;
        originalRoot = editorContent;
        try {
            addStylesheetIfMissing(scene, baseStylesheet);
            if (platformStylesheet != null) {
                addStylesheetIfMissing(scene, platformStylesheet);
            }
            setCenter(editorContent);
            scene.setRoot(this);
        } catch (RuntimeException | Error installationFailure) {
            Throwable cleanupFailure = restoreInstalledScene(null);
            if (cleanupFailure != null && cleanupFailure != installationFailure) {
                installationFailure.addSuppressed(cleanupFailure);
            }
            throw installationFailure;
        }
    }

    /**
     * Передає системну взаємодію заголовка API JavaFX та задає семантичні ролі трьом кнопкам.
     * У Windows і Linux кнопки займають усю висоту заголовка; macOS зберігає компактні круглі індикатори.
     * Інтерактивні кнопки явно виключено з успадкованої перетягуваної області.
     *
     * @throws IllegalStateException якщо подання очищено, Stage не використовує {@link StageStyle#EXTENDED}
     *                               або операцію вже виконано.
     */
    public void installExtendedHeaderBar() {
        ensureNotDisposed("install an extended header bar");
        if (stage.getStyle() != StageStyle.EXTENDED) {
            throw new IllegalStateException("Extended header bar requires StageStyle.EXTENDED");
        }
        if (extendedHeaderBarInstalled) {
            throw new IllegalStateException("Extended header bar is already installed");
        }

        HeaderBar headerBar = new HeaderBar();
        headerBar.getStyleClass().add("window-title-bar-host");
        headerBar.setMinHeight(TITLE_BAR_HEIGHT);
        headerBar.setPrefHeight(TITLE_BAR_HEIGHT);
        headerBar.setMaxHeight(TITLE_BAR_HEIGHT);
        HeaderBar.setSystemButtonHeight(stage, 0.0);
        HeaderBar.setButtonType(minimizeButton, HeaderButtonType.ICONIFY);
        HeaderBar.setButtonType(maximizeButton, HeaderButtonType.MAXIMIZE);
        HeaderBar.setButtonType(closeButton, HeaderButtonType.CLOSE);
        HeaderBar.setDragType(titleBar, HeaderDragType.DRAGGABLE_SUBTREE);
        HeaderBar.setDragType(minimizeButton, HeaderDragType.NONE);
        HeaderBar.setDragType(maximizeButton, HeaderDragType.NONE);
        HeaderBar.setDragType(closeButton, HeaderDragType.NONE);

        titleBar.setPadding(platformStyle.controlsOnLeadingEdge()
                ? new Insets(0.0, 6.0, 0.0, 6.0)
                : new Insets(0.0, 0.0, 0.0, 12.0));
        titleBar.setMaxWidth(Double.MAX_VALUE);
        setTop(null);
        headerBar.setCenter(titleBar);
        setTop(headerBar);

        // Стандартні дії кнопок визначаються їхніми системними ролями HeaderButtonType.
        extendedHeaderBarInstalled = true;
        refreshWindowState();
        requestLayout();
    }

    /**
     * Звільняє слухачі локалі та стану вікна, відновлює початковий корінь сцени й видаляє додані цим поданням стилі.
     * Повторний виклик безпечний; у разі часткової помилки незалежні кроки очищення все одно виконуються.
     *
     * @throws RuntimeException якщо один із кроків очищення завершується помилкою виконання.
     * @throws Error якщо один із кроків очищення завершується помилкою віртуальної машини або іншою помилкою.
     */
    public void dispose() {
        if (disposed) {
            return;
        }

        Throwable cleanupFailure = null;
        cleanupFailure = attemptCleanup(cleanupFailure, () -> localization.removeListener(localeChangeListener));
        cleanupFailure = attemptCleanup(cleanupFailure, () -> stage.maximizedProperty().removeListener(maximizedListener));
        cleanupFailure = attemptCleanup(cleanupFailure, () -> stage.fullScreenProperty().removeListener(fullScreenListener));
        cleanupFailure = attemptCleanup(cleanupFailure, () -> titleLabel.textProperty().unbind());
        cleanupFailure = restoreInstalledScene(cleanupFailure);

        if (cleanupFailure != null) {
            rethrowCleanupFailure(cleanupFailure);
        }
        disposed = true;
    }

    /**
     * Забороняє повторне встановлення подання після успішного звільнення його ресурсів.
     *
     * @param operation опис операції, яку намагаються виконати.
     * @throws IllegalStateException якщо подання вже очищено.
     */
    private void ensureNotDisposed(String operation) {
        if (disposed) {
            throw new IllegalStateException("Cannot " + operation + " on a disposed window chrome view");
        }
    }

    /**
     * Перевіряє наявність CSS-ресурсу до зміни дерева сцени.
     *
     * @param resourcePath абсолютний шлях до ресурсу в classpath.
     * @return знайдений ресурс стилів.
     * @throws IllegalStateException якщо ресурс відсутній.
     */
    private URL requireStylesheet(String resourcePath) {
        URL stylesheet = getClass().getResource(resourcePath);
        if (stylesheet == null) {
            throw new IllegalStateException(resourcePath + " is missing");
        }
        return stylesheet;
    }

    /**
     * Додає таблицю стилів до сцени лише за відсутності такого URI й запам'ятовує власні зміни.
     *
     * @param scene сцена, до якої додається таблиця стилів.
     * @param stylesheet таблиця стилів, яку потрібно підключити.
     */
    private void addStylesheetIfMissing(Scene scene, URL stylesheet) {
        String stylesheetUrl = stylesheet.toExternalForm();
        if (!scene.getStylesheets().contains(stylesheetUrl)) {
            scene.getStylesheets().add(stylesheetUrl);
            addedStylesheets.add(stylesheetUrl);
        }
    }

    /**
     * Відновлює стан сцени, який існував до встановлення заголовка, зберігаючи всі помилки очищення.
     *
     * @param priorFailure попередня помилка або {@code null}, якщо очищення починається без помилки.
     * @return первинна помилка з доданими помилками очищення або {@code null}, якщо все вдалося.
     */
    private Throwable restoreInstalledScene(Throwable priorFailure) {
        Scene scene = installedScene;
        if (scene == null) {
            return priorFailure;
        }

        if (originalRoot != null && getCenter() == originalRoot) {
            priorFailure = attemptCleanup(priorFailure, () -> setCenter(null));
        }
        if (originalRoot != null && scene.getRoot() == this) {
            priorFailure = attemptCleanup(priorFailure, () -> scene.setRoot(originalRoot));
        }
        for (int index = addedStylesheets.size() - 1; index >= 0; index--) {
            String stylesheetUrl = addedStylesheets.get(index);
            priorFailure = attemptCleanup(priorFailure, () -> scene.getStylesheets().remove(stylesheetUrl));
        }
        if (priorFailure == null) {
            addedStylesheets.clear();
            installedScene = null;
            originalRoot = null;
        }
        return priorFailure;
    }

    /**
     * Виконує незалежний крок очищення, не зупиняючи наступні кроки після помилки.
     *
     * @param priorFailure накопичена помилка або {@code null}.
     * @param cleanup дія звільнення ресурсу.
     * @return первинна помилка з доданою помилкою очищення або {@code null}.
     */
    private static Throwable attemptCleanup(Throwable priorFailure, Runnable cleanup) {
        try {
            cleanup.run();
        } catch (RuntimeException | Error cleanupFailure) {
            if (priorFailure == null) {
                return cleanupFailure;
            }
            if (priorFailure != cleanupFailure) {
                priorFailure.addSuppressed(cleanupFailure);
            }
        }
        return priorFailure;
    }

    /**
     * Повторно викидає помилку очищення, не приховуючи тип первинної помилки.
     *
     * @param cleanupFailure помилка, що виникла під час звільнення ресурсів.
     */
    private static void rethrowCleanupFailure(Throwable cleanupFailure) {
        if (cleanupFailure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (cleanupFailure instanceof Error error) {
            throw error;
        }
        throw new IllegalStateException("Unexpected checked cleanup failure", cleanupFailure);
    }

    /** Оновлює доступні назви кнопок відповідно до активної локалі. */
    private void refreshControlLabels() {
        setControlLabel(minimizeButton, localization.textFrom(LABELS_BUNDLE, "minimize"));
        setControlLabel(closeButton, localization.textFrom(LABELS_BUNDLE, "close"));
        updateMaximizeIcon();
    }

    /** Узгоджує видимість усієї смуги заголовка й значок кнопки максимізації зі станом Stage. */
    private void refreshWindowState() {
        boolean fullScreen = stage.isFullScreen();
        maximized = stage.isMaximized() || fullScreen;
        updateHeaderVisibility(getTop(), fullScreen);
        updateMaximizeIcon();
        requestLayout();
    }

    /**
     * Застосовує стан видимості заголовка окремо від {@link Stage}, щоб перевіряти компонування без показу вікна.
     * Прихований заголовок також виключається з компонування, щоб він не резервував вертикальний простір.
     *
     * @param header вузол усієї смуги заголовка; {@code null} дозволено до її встановлення.
     * @param fullScreen чи перебуває вікно в повноекранному режимі.
     */
    static void updateHeaderVisibility(Node header, boolean fullScreen) {
        if (header == null) {
            return;
        }
        header.setVisible(!fullScreen);
        header.setManaged(!fullScreen);
    }

    /** Оновлює графічний значок і доступну назву кнопки відповідно до платформної семантики вікна. */
    private void updateMaximizeIcon() {
        Node icon = platformStyle == WindowChromePlatformStyle.MACOS
                ? WindowChromeIconFactory.createMacOsFullScreenIcon(stage.isFullScreen())
                : WindowChromeIconFactory.createMaximizeIcon(maximized);
        maximizeButton.setGraphic(icon);
        String labelKey = platformStyle.maximizeLabelKey(stage.isMaximized(), stage.isFullScreen());
        maximizeButton.setAccessibleText(localization.textFrom(LABELS_BUNDLE, labelKey));
    }

    /** Застосовує локалізовану доступну назву кнопки для допоміжних технологій. */
    private static void setControlLabel(Button button, String label) {
        button.setAccessibleText(label);
    }

    /** Створює кнопку заголовка з векторним значком і класами стилю. */
    private static Button createButton(String styleClass, Node graphic) {
        Button button = new Button();
        button.setGraphic(graphic);
        button.getStyleClass().addAll("window-control-button", styleClass);
        button.setFocusTraversable(false);
        return button;
    }

    /**
     * Зберігає пакетний контракт створення значка macOS для наявних регресійних перевірок.
     *
     * @param fullScreen чи вже перебуває вікно в повноекранному режимі.
     * @return значок входу в повноекранний режим або виходу з нього.
     */
    static Node createMacOsFullScreenIcon(boolean fullScreen) {
        return WindowChromeIconFactory.createMacOsFullScreenIcon(fullScreen);
    }
}
