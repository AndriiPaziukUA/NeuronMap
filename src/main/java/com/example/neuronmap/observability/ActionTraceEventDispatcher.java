package com.example.neuronmap.observability;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventDispatchChain;
import javafx.event.EventDispatcher;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Labeled;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.util.Duration;

import java.util.Locale;
import java.util.Objects;

/**
 * Додає до сцени журналювання зовнішніх UI-подій і об'єднує серії рухів вказівника, клавіш та прокручування.
 * Сам обробник події залишається власником поведінки; диспетчер лише відкриває контекст для аспекту.
 */
public final class ActionTraceEventDispatcher implements AutoCloseable {

    private static final long DEFAULT_IDLE_INTERVAL_MILLIS = 300L;
    private static final long IDLE_INTERVAL_MILLIS = readIdleInterval();

    private final Scene scene;
    private final EventDispatcher originalDispatcher;
    private final EventDispatcher tracingDispatcher = this::dispatch;
    private final Runnable preLayoutPulseListener = this::beginPulseTrace;
    private final Runnable postLayoutPulseListener = this::finishPulseTrace;
    private final PauseTransition mouseMoveIdleTimer;
    private final PauseTransition scrollIdleTimer;
    private final PauseTransition mouseGestureIdleTimer;
    private final PauseTransition keyboardIdleTimer;

    private ActionTraceContext.ActionSession mouseMoveSession;
    private ActionTraceContext.ActionSession scrollSession;
    private ActionTraceContext.ActionSession mouseGestureSession;
    private ActionTraceContext.ActionSession keyboardSession;
    private ActionTraceContext.SessionBinding pulseBinding;
    private boolean installed;
    private boolean closed;

    /**
     * Встановлює адаптер подій на сцену, зберігаючи попередній диспетчер для подальшого відновлення.
     *
     * @param scene сцена, зовнішні події якої потрібно трасувати.
     * @return власник реєстрації, який потрібно закрити під час завершення редактора.
     * @throws NullPointerException якщо сцену не задано.
     * @throws IllegalStateException якщо встановлення виконується поза JavaFX Application Thread.
     */
    public static ActionTraceEventDispatcher install(Scene scene) {
        return new ActionTraceEventDispatcher(Objects.requireNonNull(scene, "scene"));
    }

    /**
     * Зберігає початковий диспетчер сцени, створює таймери та підключає маршрутизатор UI-подій.
     *
     * @param scene сцена, для якої встановлюється трасування.
     */
    private ActionTraceEventDispatcher(Scene scene) {
        requireJavaFxThread();
        this.scene = scene;
        originalDispatcher = scene.getEventDispatcher();
        mouseMoveIdleTimer = createTimer(this::finishMouseMove);
        scrollIdleTimer = createTimer(this::finishScroll);
        mouseGestureIdleTimer = createTimer(this::finishMouseGesture);
        keyboardIdleTimer = createTimer(this::finishKeyboardAction);
        if (ActionTraceContext.isEnabled()) {
            scene.addPreLayoutPulseListener(preLayoutPulseListener);
            scene.addPostLayoutPulseListener(postLayoutPulseListener);
            scene.setEventDispatcher(tracingDispatcher);
            installed = true;
        }
    }

    /**
     * Відкриває контекст одного кадру перед CSS та layout, якщо кадр не належить уже активній дії.
     * Якщо попередній callback завершення кадру не відбувся, закриває його контекст перед наступним кадром.
     *
     * <p>JavaFX викликає цей слухач у JavaFX Application Thread перед обчисленням CSS і компонування.</p>
     */
    void beginPulseTrace() {
        if (closed || !installed) {
            return;
        }
        if (pulseBinding != null) {
            finishPulseTrace();
        }
        if (ActionTraceContext.currentSession() != null) {
            return;
        }
        String dimensions = String.format(Locale.ROOT, "розмір сцени=%.0fx%.0f", scene.getWidth(), scene.getHeight());
        pulseBinding = ActionTraceContext.beginMeasuredAction("Кадр сцени JavaFX", dimensions);
    }

    /** Завершує контекст кадру після CSS та layout, до передавання сцени на рендеринг. */
    void finishPulseTrace() {
        ActionTraceContext.SessionBinding binding = pulseBinding;
        if (binding == null) {
            return;
        }
        pulseBinding = null;
        binding.close();
    }

    /**
     * Розпізнає тип зовнішньої події та делегує її спеціалізованому агрегатору або початковому диспетчеру.
     *
     * @param event подія, що надійшла зі сцени.
     * @param tail решта ланцюга диспетчеризації.
     * @return результат стандартної маршрутизації JavaFX.
     */
    private Event dispatch(Event event, EventDispatchChain tail) {
        if (closed || !installed) {
            return originalDispatcher.dispatchEvent(event, tail);
        }
        ActionTraceContext.ActionSession activeSession = ActionTraceContext.currentSession();
        if (activeSession != null) {
            if (event instanceof ActionEvent) {
                activeSession.addEvent(describeActionEvent(event));
            }
            return originalDispatcher.dispatchEvent(event, tail);
        }
        if (event instanceof MouseEvent mouseEvent) {
            return dispatchMouseEvent(mouseEvent, tail);
        }
        if (event instanceof ScrollEvent scrollEvent) {
            return dispatchScrollEvent(scrollEvent, tail);
        }
        if (event instanceof KeyEvent keyEvent) {
            return dispatchKeyEvent(keyEvent, tail);
        }
        if (event instanceof ActionEvent) {
            return dispatchStandaloneAction(describeActionEvent(event), event, tail);
        }
        return originalDispatcher.dispatchEvent(event, tail);
    }

    /**
     * Групує натискання, перетягування, відпускання та клацання в один сеанс взаємодії мишею.
     *
     * @param event подія миші.
     * @param tail решта ланцюга диспетчеризації.
     * @return результат початкової диспетчеризації.
     */
    private Event dispatchMouseEvent(MouseEvent event, EventDispatchChain tail) {
        if (event.getEventType() == MouseEvent.MOUSE_MOVED) {
            return dispatchTimedMouseMove(event, tail);
        }
        if (event.getEventType() == MouseEvent.MOUSE_PRESSED) {
            finishMouseMove();
            finishMouseGesture();
            mouseGestureSession = ActionTraceContext.startEvent("Взаємодія мишею", describeMouseEvent(event));
            try {
                return dispatchInSession(mouseGestureSession, event, tail);
            } finally {
                mouseGestureIdleTimer.playFromStart();
            }
        }
        if (event.getEventType() == MouseEvent.MOUSE_DRAGGED) {
            if (mouseGestureSession == null) {
                mouseGestureSession = ActionTraceContext.startEvent("Перетягування мишею", describeMouseEvent(event));
            }
            try {
                return dispatchInSession(mouseGestureSession, event, tail);
            } finally {
                mouseGestureIdleTimer.playFromStart();
            }
        }
        if (event.getEventType() == MouseEvent.MOUSE_RELEASED) {
            if (mouseGestureSession == null) {
                mouseGestureSession = ActionTraceContext.startEvent(
                        "Відпускання кнопки миші", describeMouseEvent(event));
            }
            try {
                return dispatchInSession(mouseGestureSession, event, tail);
            } finally {
                mouseGestureIdleTimer.playFromStart();
            }
        }
        if (event.getEventType() == MouseEvent.MOUSE_CLICKED) {
            if (mouseGestureSession == null) {
                mouseGestureSession = ActionTraceContext.startEvent("Клацання мишею", describeMouseEvent(event));
            }
            try {
                return dispatchInSession(mouseGestureSession, event, tail);
            } finally {
                mouseGestureIdleTimer.playFromStart();
            }
        }
        return originalDispatcher.dispatchEvent(event, tail);
    }

    /**
     * Об'єднує часті переміщення вказівника до моменту короткої паузи між подіями.
     *
     * @param event подія переміщення вказівника.
     * @param tail решта ланцюга диспетчеризації.
     * @return результат початкової диспетчеризації.
     */
    private Event dispatchTimedMouseMove(MouseEvent event, EventDispatchChain tail) {
        if (mouseMoveSession == null) {
            mouseMoveSession = ActionTraceContext.startEvent("Рух вказівника", describeMouseEvent(event));
        }
        try {
            return dispatchInSession(mouseMoveSession, event, tail);
        } finally {
            mouseMoveIdleTimer.playFromStart();
        }
    }

    /**
     * Об'єднує послідовні події коліщатка в один сеанс прокручування.
     *
     * @param event подія прокручування.
     * @param tail решта ланцюга диспетчеризації.
     * @return результат початкової диспетчеризації.
     */
    private Event dispatchScrollEvent(ScrollEvent event, EventDispatchChain tail) {
        if (scrollSession == null) {
            scrollSession = ActionTraceContext.startEvent("Прокручування", describeScrollEvent(event));
        }
        try {
            return dispatchInSession(scrollSession, event, tail);
        } finally {
            scrollIdleTimer.playFromStart();
        }
    }

    /**
     * Групує натискання, автоповтор, текстове введення та відпускання клавіші.
     * Символи тексту навмисно не записуються, щоб не зберігати введені користувачем дані.
     *
     * @param event клавіатурна подія.
     * @param tail решта ланцюга диспетчеризації.
     * @return результат початкової диспетчеризації.
     */
    private Event dispatchKeyEvent(KeyEvent event, EventDispatchChain tail) {
        if (event.getEventType() == KeyEvent.KEY_TYPED) {
            if (keyboardSession == null) {
                keyboardSession = ActionTraceContext.startEvent("Введення тексту", "символ приховано");
            }
            try {
                return dispatchInSession(
                        keyboardSession, event, tail, "текстове введення (значення приховано)");
            } finally {
                keyboardIdleTimer.playFromStart();
            }
        }
        if (event.getEventType() == KeyEvent.KEY_PRESSED) {
            if (keyboardSession == null) {
                keyboardSession = ActionTraceContext.startEvent("Натискання клавіші", describeKeyEvent(event));
            }
            try {
                return dispatchInSession(keyboardSession, event, tail);
            } finally {
                keyboardIdleTimer.playFromStart();
            }
        }
        if (event.getEventType() == KeyEvent.KEY_RELEASED) {
            if (keyboardSession == null) {
                keyboardSession = ActionTraceContext.startEvent("Відпускання клавіші", describeKeyEvent(event));
            }
            try {
                return dispatchInSession(keyboardSession, event, tail);
            } finally {
                finishKeyboardAction();
            }
        }
        return originalDispatcher.dispatchEvent(event, tail);
    }

    /**
     * Виконує одиничну подію елемента інтерфейсу в окремому контексті.
     *
     * @param details стислий опис джерела події.
     * @param event подія, яку потрібно передати обробникам.
     * @param tail решта ланцюга диспетчеризації.
     * @return результат початкової диспетчеризації.
     */
    private Event dispatchStandaloneAction(
            String details,
            Event event,
            EventDispatchChain tail
    ) {
        ActionTraceContext.ActionSession session = ActionTraceContext.startEvent(
                "Дія елемента інтерфейсу", details);
        try {
            return dispatchInSession(session, event, tail);
        } finally {
            session.finish();
        }
    }

    /**
     * Виконує подію в контексті дії та обліковує її навіть тоді, коли обробник завершується помилкою.
     *
     * @param session активний контекст дії.
     * @param event подія JavaFX.
     * @param tail решта ланцюга диспетчеризації.
     * @return результат початкової диспетчеризації.
     */
    private Event dispatchInSession(
            ActionTraceContext.ActionSession session,
            Event event,
            EventDispatchChain tail
    ) {
        return dispatchInSession(session, event, tail, describeEventDetails(event));
    }

    /**
     * Виконує подію з переданим описом у тимчасово активованому контексті дії.
     *
     * @param session активний контекст дії.
     * @param event подія JavaFX.
     * @param tail решта ланцюга диспетчеризації.
     * @param details безпечний опис події без її повної серіалізації.
     * @return результат початкової диспетчеризації.
     */
    private Event dispatchInSession(
            ActionTraceContext.ActionSession session,
            Event event,
            EventDispatchChain tail,
            String details
    ) {
        session.addEvent(details);
        try (ActionTraceContext.SessionBinding ignored = ActionTraceContext.bind(session)) {
            return originalDispatcher.dispatchEvent(event, tail);
        } catch (RuntimeException | Error failure) {
            session.recordFailure(failure);
            throw failure;
        }
    }

    /**
     * Обирає спеціалізований стислий опис події, не розгортаючи внутрішній стан JavaFX-об'єктів.
     *
     * @param event подія, яку потрібно відобразити у трасі.
     * @return безпечні деталі миші, клавіатури, прокручування або загальний опис події.
     */
    private String describeEventDetails(Event event) {
        if (event instanceof MouseEvent mouseEvent) {
            return describeMouseEvent(mouseEvent);
        }
        if (event instanceof ScrollEvent scrollEvent) {
            return describeScrollEvent(scrollEvent);
        }
        if (event instanceof KeyEvent keyEvent) {
            return describeKeyEvent(keyEvent);
        }
        if (event instanceof ActionEvent) {
            return describeActionEvent(event);
        }
        return describeEvent(event);
    }

    /**
     * Формує опис руху миші без виклику {@code toString()} для вузлів JavaFX.
     *
     * @param event подія миші.
     * @return назва кнопки, координати, модифікатори й тип цільового вузла.
     */
    private String describeMouseEvent(MouseEvent event) {
        return "тип=" + event.getEventType().getName()
                + ", кнопка=" + event.getButton()
                + ", кліків=" + event.getClickCount()
                + ", позиція=" + formatCoordinates(event.getX(), event.getY())
                + ", модифікатори=" + describeModifiers(
                        event.isShiftDown(), event.isControlDown(), event.isAltDown(), event.isMetaDown())
                + ", ціль=" + describeTarget(event.getTarget());
    }

    /**
     * Формує опис прокручування без збереження повного об'єкта події.
     *
     * @param event подія прокручування.
     * @return напрямок і величина прокручування та цільовий вузол.
     */
    private String describeScrollEvent(ScrollEvent event) {
        return "delta=" + formatCoordinates(event.getDeltaX(), event.getDeltaY())
                + ", інерція=" + event.isInertia()
                + ", позиція=" + formatCoordinates(event.getX(), event.getY())
                + ", ціль=" + describeTarget(event.getTarget());
    }

    /**
     * Формує опис клавіатурної команди без запису символу текстового введення.
     *
     * @param event клавіатурна подія.
     * @return код клавіші та активні модифікатори.
     */
    private String describeKeyEvent(KeyEvent event) {
        boolean shortcutModifier = event.isControlDown() || event.isAltDown() || event.isMetaDown();
        boolean textKey = event.getCode().isLetterKey() || event.getCode().isDigitKey()
                || event.getCode() == KeyCode.SPACE;
        String keyName = textKey && !shortcutModifier ? "символ приховано" : event.getCode().getName();
        return "клавіша=" + keyName
                + ", тип=" + event.getEventType().getName()
                + ", модифікатори=" + describeModifiers(
                        event.isShiftDown(), event.isControlDown(), event.isAltDown(), event.isMetaDown())
                + ", ціль=" + describeTarget(event.getTarget());
    }

    /**
     * Формує короткий опис програмної дії елемента інтерфейсу.
     *
     * @param event подія дії.
     * @return тип і підпис джерела або цільового елемента.
     */
    private String describeActionEvent(Event event) {
        Object source = event.getSource();
        if (source instanceof MenuItem menuItem) {
            return "елемент меню=" + describeMenuItem(menuItem);
        }
        if (source instanceof Labeled labeled) {
            return "елемент=" + describeLabeledControl(labeled)
                    + ", ціль=" + describeTarget(event.getTarget());
        }
        return "джерело=" + describeTarget(source) + ", ціль=" + describeTarget(event.getTarget());
    }

    /**
     * Формує коротку ідентичність пункту меню з його видимим підписом та необов'язковим ID.
     *
     * @param menuItem пункт меню, який ініціював дію.
     * @return клас пункту меню, його підпис і стабільний UI-ідентифікатор, якщо він заданий.
     */
    private String describeMenuItem(MenuItem menuItem) {
        String identifier = menuItem.getId();
        return menuItem.getClass().getSimpleName()
                + "[" + sanitizeLabel(menuItem.getText()) + "]"
                + (identifier == null || identifier.isBlank() ? "" : "#" + sanitizeLabel(identifier));
    }

    /**
     * Формує коротку ідентичність кнопки чи іншого підписаного вузла з текстом і необов'язковим ID.
     *
     * @param labeled вузол, який ініціював програмну дію.
     * @return клас вузла, його підпис і стабільний UI-ідентифікатор, якщо він заданий.
     */
    private String describeLabeledControl(Labeled labeled) {
        String identifier = labeled.getId();
        return labeled.getClass().getSimpleName()
                + "[" + sanitizeLabel(labeled.getText()) + "]"
                + (identifier == null || identifier.isBlank() ? "" : "#" + sanitizeLabel(identifier));
    }

    /**
     * Формує назву цільового вузла, не розгортаючи його властивості.
     *
     * @param target джерело або ціль події.
     * @return назва типу та необов'язковий ідентифікатор вузла.
     */
    private String describeTarget(Object target) {
        if (target instanceof Node node) {
            String identifier = node.getId();
            return node.getClass().getSimpleName()
                    + (identifier == null || identifier.isBlank() ? "" : "#" + sanitizeLabel(identifier));
        }
        if (target == null) {
            return "немає";
        }
        return target.getClass().getSimpleName();
    }

    /**
     * Формує безпечний опис для події, що не належить до окремого типізованого сценарію.
     *
     * @param event подія JavaFX.
     * @return назва класу події та її ціль.
     */
    private String describeEvent(Event event) {
        return "подія=" + event.getClass().getSimpleName() + ", ціль=" + describeTarget(event.getTarget());
    }

    /**
     * Перетворює набір модифікаторів на компактний перелік активних клавіш.
     *
     * @param shift ознака Shift.
     * @param control ознака Ctrl.
     * @param alt ознака Alt.
     * @param meta ознака Meta.
     * @return перелік натиснутих модифікаторів або {@code none}.
     */
    private String describeModifiers(boolean shift, boolean control, boolean alt, boolean meta) {
        StringBuilder result = new StringBuilder();
        appendModifier(result, "Shift", shift);
        appendModifier(result, "Ctrl", control);
        appendModifier(result, "Alt", alt);
        appendModifier(result, "Meta", meta);
        return result.isEmpty() ? "немає" : result.toString();
    }

    /**
     * Додає назву активного модифікатора до переліку без зайвих роздільників.
     *
     * @param destination рядок, до якого додається назва.
     * @param name коротка назва клавіші-модифікатора.
     * @param active чи натиснутий відповідний модифікатор.
     */
    private void appendModifier(StringBuilder destination, String name, boolean active) {
        if (!active) {
            return;
        }
        if (!destination.isEmpty()) {
            destination.append('+');
        }
        destination.append(name);
    }

    /**
     * Форматує пару координат, не залежачи від системної локалі.
     *
     * @param x горизонтальна координата.
     * @param y вертикальна координата.
     * @return компактне представлення пари координат.
     */
    private String formatCoordinates(double x, double y) {
        return String.format(Locale.ROOT, "(%.1f; %.1f)", x, y);
    }

    /**
     * Обмежує довжину написів елементів інтерфейсу та прибирає символи керування рядком.
     *
     * @param value початковий підпис елемента.
     * @return безпечний однорядковий підпис.
     */
    private String sanitizeLabel(String value) {
        if (value == null || value.isBlank()) {
            return "без напису";
        }
        String normalized = value.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
        return normalized.length() > 80 ? normalized.substring(0, 80) + "…" : normalized;
    }

    /**
     * Створює одноразовий таймер паузи між подіями одного типу.
     *
     * @param finishAction дія, яку потрібно завершити після періоду бездіяльності.
     * @return зупинений таймер із зареєстрованим обробником завершення.
     */
    private PauseTransition createTimer(Runnable finishAction) {
        PauseTransition timer = new PauseTransition(Duration.millis(IDLE_INTERVAL_MILLIS));
        timer.setOnFinished(event -> {
            Objects.requireNonNull(event, "event");
            finishAction.run();
        });
        return timer;
    }

    /** Завершує дію руху вказівника після паузи між подіями. */
    private void finishMouseMove() {
        mouseMoveIdleTimer.stop();
        finishSession(mouseMoveSession);
        mouseMoveSession = null;
    }

    /** Завершує дію прокручування після паузи між подіями. */
    private void finishScroll() {
        scrollIdleTimer.stop();
        finishSession(scrollSession);
        scrollSession = null;
    }

    /** Завершує жест миші після відпускання кнопки й завершення супровідних подій. */
    private void finishMouseGesture() {
        mouseGestureIdleTimer.stop();
        finishSession(mouseGestureSession);
        mouseGestureSession = null;
    }

    /** Завершує клавіатурну дію після відпускання клавіші або закінчення періоду автоповтору. */
    private void finishKeyboardAction() {
        keyboardIdleTimer.stop();
        finishSession(keyboardSession);
        keyboardSession = null;
    }

    /**
     * Завершує переданий сеанс, якщо він існує.
     *
     * @param session контекст агрегованої дії або {@code null}, якщо його не було створено.
     */
    private void finishSession(ActionTraceContext.ActionSession session) {
        if (session != null) {
            session.finish();
        }
    }

    /** Перевіряє, що зміна диспетчера та керування таймерами виконується у потоці JavaFX. */
    private void requireJavaFxThread() {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("UI event tracing must be configured on the JavaFX Application Thread");
        }
    }

    /** Читає налаштований інтервал групування подій, відхиляючи нульові та некоректні значення. */
    private static long readIdleInterval() {
        try {
            long configured = Long.parseLong(System.getProperty(
                    "neuronmap.trace.input-idle-ms", Long.toString(DEFAULT_IDLE_INTERVAL_MILLIS)));
            return configured > 0L ? configured : DEFAULT_IDLE_INTERVAL_MILLIS;
        } catch (NumberFormatException exception) {
            return DEFAULT_IDLE_INTERVAL_MILLIS;
        }
    }

    /**
     * Відновлює початковий диспетчер сцени та завершує всі незакриті дії.
     * Повторне закриття безпечне й не зачіпає диспетчер, установлений іншим компонентом пізніше.
     */
    @Override
    public void close() {
        requireJavaFxThread();
        if (closed) {
            return;
        }
        closed = true;
        mouseMoveIdleTimer.stop();
        scrollIdleTimer.stop();
        mouseGestureIdleTimer.stop();
        keyboardIdleTimer.stop();
        if (installed) {
            scene.removePreLayoutPulseListener(preLayoutPulseListener);
            scene.removePostLayoutPulseListener(postLayoutPulseListener);
        }
        finishPulseTrace();
        if (installed && scene.getEventDispatcher() == tracingDispatcher) {
            scene.setEventDispatcher(originalDispatcher);
        }
        finishMouseMove();
        finishScroll();
        finishMouseGesture();
        finishKeyboardAction();
        installed = false;
    }
}
