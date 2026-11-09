package com.example.neuronmap.observability;

import com.sun.management.ThreadMXBean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Зберігає контекст однієї прикладної дії та збирає дерево викликів методів лише в межах її потоку.
 * Контекст спільно використовують аспект трасування й адаптер подій JavaFX.
 */
public final class ActionTraceContext {

    private static final Logger LOGGER = LogManager.getLogger(ActionTraceContext.class);
    private static final AtomicLong IDENTIFIER_SEQUENCE = new AtomicLong();
    private static final ThreadLocal<ActionSession> CURRENT_SESSION = new ThreadLocal<>();
    /** Трасування вимкнене за замовчуванням і вмикається лише для цільової діагностики. */
    private static final boolean ENABLED = Boolean.parseBoolean(
            System.getProperty("neuronmap.trace.enabled", "false"));
    private static final java.lang.management.ThreadMXBean THREAD_METRICS =
            ManagementFactory.getThreadMXBean();
    /** Важкі лічильники JVM активуються лише тоді, коли користувач явно увімкнув трасування. */
    private static final ThreadMXBean EXTENDED_THREAD_METRICS = ENABLED ? resolveExtendedThreadMetrics() : null;
    private static final MemoryMXBean MEMORY_METRICS = ManagementFactory.getMemoryMXBean();
    private static final double MINIMUM_AUTOMATIC_DURATION_MILLIS = readPositiveDouble();
    private static final int MAXIMUM_STRING_LENGTH = readPositiveInteger();

    /** Забороняє створення екземплярів службового контексту трасування. */
    private ActionTraceContext() {
    }

    /**
     * Повертає ознаку активності трасування з урахуванням параметра запуску JVM.
     *
     * @return {@code true}, якщо збір трас увімкнено.
     */
    public static boolean isEnabled() {
        return ENABLED;
    }

    /**
     * Повертає контекст дії, що наразі виконується в поточному потоці.
     *
     * @return активна дія або {@code null}, якщо виклик не належить до жодної дії.
     */
    public static ActionSession currentSession() {
        return CURRENT_SESSION.get();
    }

    /**
     * Створює контекст дії, отриманої від зовнішньої події користувача.
     * Стартовий рядок записується негайно, а дерево методів — після завершення всієї дії.
     *
     * @param name зрозуміла назва дії.
     * @param details короткий опис типу події та її безпечних параметрів.
     * @return новий контекст із власним коротким ідентифікатором.
     */
    static ActionSession startEvent(String name, String details) {
        return new ActionSession(name, details, true);
    }

    /**
     * Створює іменовану дію, позначену анотацією, без порогу відсіювання коротких викликів.
     *
     * @param name назва логічної дії.
     * @param arguments аргументи методу, що започаткував дію.
     * @return прив'язка, яка завершує дію та відновлює попередній контекст.
     */
    public static SessionBinding beginNamedAction(String name, Object[] arguments) {
        ActionSession session = new ActionSession(name, summarizeArguments(arguments), true);
        return bind(session, true);
    }

    /**
     * Починає автоматичну дію для прикладного методу, який викликано поза активною дією.
     * Короткі виклики відкидаються після вимірювання, а довгі або аварійні залишаються в журналі.
     *
     * @param methodName сигнатура першого методу дії.
     * @param arguments аргументи першого виклику.
     * @return прив'язка, яка завершує дію та відновлює попередній контекст.
     */
    @SuppressWarnings("unused") // Викликається з ActionTraceAspect.aj; Java-інспекція не бачить AspectJ-посилання.
    public static SessionBinding beginAutomatic(String methodName, Object[] arguments) {
        return beginMeasuredAction("Автоматичний виклик: " + methodName, summarizeArguments(arguments));
    }

    /**
     * Починає вимірювану дію без негайного стартового запису, щоб відсіяти короткі та порожні сегменти.
     *
     * @param name назва логічної дії.
     * @param details короткий опис контексту дії.
     * @return прив'язка, яка завершує дію та відновлює попередній контекст.
     */
    static SessionBinding beginMeasuredAction(String name, String details) {
        return bind(new ActionSession(name, details, false), true);
    }

    /**
     * Тимчасово прив'язує наявний контекст до поточного потоку під час синхронної обробки події.
     *
     * @param session контекст, до якого належать виклики методів.
     * @return об'єкт, що відновлює попередній контекст після завершення сегмента.
     */
    static SessionBinding bind(ActionSession session) {
        return bind(session, false);
    }

    /**
     * Прив'язує контекст і визначає, чи має закриття прив'язки завершувати всю дію.
     *
     * @param session контекст, який потрібно активувати.
     * @param finishWhenClosed ознака завершення дії разом із прив'язкою.
     * @return керована прив'язка до потоку.
     */
    private static SessionBinding bind(ActionSession session, boolean finishWhenClosed) {
        return new SessionBinding(Objects.requireNonNull(session, "session"), finishWhenClosed);
    }

    /**
     * Реєструє в дереві поточної дії початок виконання методу прикладного коду.
     *
     * @param methodName коротка сигнатура методу.
     * @param arguments аргументи виклику без розгортання внутрішнього стану об'єктів.
     * @return маркер виклику для подальшого обчислення тривалості та результату.
     */
    public static MethodInvocation enterMethod(String methodName, Object[] arguments) {
        ActionSession session = CURRENT_SESSION.get();
        if (session == null) {
            return null;
        }
        return session.enterMethod(methodName, summarizeArguments(arguments));
    }

    /**
     * Формує безпечне текстове представлення аргументів без виклику {@code toString()} довільних об'єктів.
     * Прості значення залишаються видимими, а складні об'єкти подаються лише назвою свого типу.
     *
     * @param arguments аргументи прикладного методу.
     * @return список стислих представлень аргументів.
     */
    static String summarizeArguments(Object[] arguments) {
        if (arguments == null || arguments.length == 0) {
            return "[]";
        }
        List<String> summaries = new ArrayList<>(arguments.length);
        for (Object argument : arguments) {
            summaries.add(summarizeArgument(argument));
        }
        return summaries.toString();
    }

    /**
     * Перетворює один аргумент на обмежений і безпечний для журналу опис.
     *
     * @param argument значення аргументу.
     * @return просте значення або назва типу без серіалізації об'єкта.
     */
    private static String summarizeArgument(Object argument) {
        if (argument == null) {
            return "null";
        }
        if (argument instanceof String value) {
            String safeValue = value.length() > MAXIMUM_STRING_LENGTH
                    ? value.substring(0, MAXIMUM_STRING_LENGTH) + "…"
                    : value;
            return '"' + sanitizeText(safeValue) + '"';
        }
        Class<?> argumentType = argument.getClass();
        if (argumentType == Byte.class || argumentType == Short.class || argumentType == Integer.class
                || argumentType == Long.class || argumentType == Float.class || argumentType == Double.class
                || argumentType == java.math.BigInteger.class || argumentType == java.math.BigDecimal.class
                || argumentType == Boolean.class || argumentType == Character.class) {
            return sanitizeText(argument.toString());
        }
        if (argument instanceof Enum<?> enumeration) {
            return enumeration.getDeclaringClass().getSimpleName() + '.' + enumeration.name();
        }
        if (argument instanceof Class<?> type) {
            return "Class<" + type.getName() + ">";
        }
        if (argument.getClass().isArray()) {
            return argument.getClass().getComponentType().getSimpleName()
                    + "[" + java.lang.reflect.Array.getLength(argument) + "]";
        }
        String simpleName = argument.getClass().getSimpleName();
        return simpleName.isEmpty() ? argument.getClass().getName() : simpleName;
    }

    /**
     * Замінює керівні символи, які могли б розірвати формат багаторядкового журналу.
     *
     * @param value початковий текст.
     * @return однорядкове безпечне представлення.
     */
    private static String sanitizeText(String value) {
        return value.replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Вмикає доступні лічильники CPU та алокацій потоку без вимоги підтримки їх кожною JVM.
     *
     * @return розширений лічильник, якщо поточна JVM його надає.
     */
    private static ThreadMXBean resolveExtendedThreadMetrics() {
        if (!(THREAD_METRICS instanceof ThreadMXBean metrics)) {
            return null;
        }
        try {
            if (metrics.isThreadCpuTimeSupported() && !metrics.isThreadCpuTimeEnabled()) {
                metrics.setThreadCpuTimeEnabled(true);
            }
            if (metrics.isThreadAllocatedMemorySupported() && !metrics.isThreadAllocatedMemoryEnabled()) {
                metrics.setThreadAllocatedMemoryEnabled(true);
            }
            return metrics;
        } catch (SecurityException | UnsupportedOperationException exception) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Лічильники CPU або алокацій потоку недоступні: {}", exception.toString());
            }
            return metrics;
        }
    }

    /**
     * Читає додатний числовий параметр запуску або повертає типове значення.
     *
     * @return скінченне додатне значення параметра або типове значення 1 мс.
     */
    private static double readPositiveDouble() {
        final double defaultValue = 1.0;
        try {
            double value = Double.parseDouble(System.getProperty("neuronmap.trace.min-duration-ms", "1.0"));
            return Double.isFinite(value) && value > 0.0 ? value : defaultValue;
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    /**
     * Читає додатний цілочисловий параметр запуску або повертає типове значення.
     *
     * @return додатне значення параметра або типове значення 80 символів.
     */
    private static int readPositiveInteger() {
        final int defaultValue = 80;
        try {
            int value = Integer.parseInt(System.getProperty("neuronmap.trace.arguments.max-string-length", "80"));
            return value > 0 ? value : defaultValue;
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    /**
     * Повертає поточний час CPU потоку в наносекундах, якщо його підтримує JVM.
     *
     * @return час CPU або {@code -1}, якщо лічильник недоступний.
     */
    private static long currentThreadCpuTime() {
        try {
            return THREAD_METRICS.isThreadCpuTimeSupported() && THREAD_METRICS.isThreadCpuTimeEnabled()
                    ? THREAD_METRICS.getCurrentThreadCpuTime()
                    : -1L;
        } catch (RuntimeException exception) {
            return -1L;
        }
    }

    /**
     * Повертає обсяг пам'яті, виділеної поточним потоком, якщо JVM надає відповідний лічильник.
     *
     * @return обсяг алокацій у байтах або {@code -1}, якщо лічильник недоступний.
     */
    private static long currentThreadAllocatedBytes() {
        if (EXTENDED_THREAD_METRICS == null || !EXTENDED_THREAD_METRICS.isThreadAllocatedMemorySupported()
                || !EXTENDED_THREAD_METRICS.isThreadAllocatedMemoryEnabled()) {
            return -1L;
        }
        try {
            return EXTENDED_THREAD_METRICS.getThreadAllocatedBytes(Thread.currentThread().threadId());
        } catch (RuntimeException exception) {
            return -1L;
        }
    }

    /**
     * Форматує тривалість у наносекундах для зручного порівняння коротких і довгих операцій.
     *
     * @param nanoseconds тривалість у наносекундах.
     * @return текстове значення в мікросекундах або мілісекундах.
     */
    private static String formatDuration(long nanoseconds) {
        if (nanoseconds < 0L) {
            return "недоступно";
        }
        if (nanoseconds < 1_000_000L) {
            return String.format(Locale.ROOT, "%.1f мкс", nanoseconds / 1_000.0);
        }
        return String.format(Locale.ROOT, "%.2f мс", nanoseconds / 1_000_000.0);
    }

    /**
     * Форматує обсяг пам'яті без залежності від локалі системи.
     *
     * @param bytes обсяг у байтах.
     * @return компактне представлення байтів, кібібайтів або мебібайтів.
     */
    private static String formatBytes(long bytes) {
        if (bytes < 0L) {
            return "недоступно";
        }
        if (bytes < 1024L) {
            return bytes + " Б";
        }
        if (bytes < 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.1f КіБ", bytes / 1024.0);
        }
        return String.format(Locale.ROOT, "%.2f МіБ", bytes / (1024.0 * 1024.0));
    }

    /**
     * Форматує зміну обсягу пам'яті, зберігаючи знак зростання або зменшення.
     *
     * @param bytes знакова різниця у байтах.
     * @return компактне значення зі знаком або повідомлення про недоступність.
     */
    private static String formatSignedBytes(long bytes) {
        if (bytes == Long.MIN_VALUE) {
            return "недоступно";
        }
        String sign = bytes > 0L ? "+" : bytes < 0L ? "-" : "";
        return sign + formatBytes(Math.abs(bytes));
    }

    /**
     * Обчислює різницю лічильників, у яких від'ємне значення означає відсутність даних.
     *
     * @param current кінцеве значення лічильника.
     * @param previous початкове значення лічильника.
     * @return невід'ємна різниця або {@code -1}, якщо хоча б одне значення недоступне.
     */
    private static long difference(long current, long previous) {
        return current < 0L || previous < 0L ? -1L : Math.max(0L, current - previous);
    }

    /**
     * Додає доступні значення метрик, залишаючи {@code -1} як ознаку недоступного лічильника.
     *
     * @param current накопичене значення метрики.
     * @param delta нове вимірювання.
     * @return сума або {@code -1}, якщо поточне чи нове вимірювання недоступне.
     */
    private static long addMetric(long current, long delta) {
        return current < 0L || delta < 0L ? -1L : current + delta;
    }

    /** Зберігає дію, дерево викликів і знімки доступних лічильників ресурсів. */
    public static final class ActionSession {

        private final String name;
        private final String identifier;
        private final String initialDetails;
        private final boolean logStartImmediately;
        private final Instant startedAt = Instant.now();
        private final long startedAtNanos = System.nanoTime();
        private final long initialHeapBytes = MEMORY_METRICS.getHeapMemoryUsage().getUsed();
        private final Deque<MethodInvocation> callStack = new ArrayDeque<>();
        private final Map<String, MethodNode> rootMethods = new LinkedHashMap<>();
        private long activeNanos;
        private long cpuNanos;
        private long allocatedBytes;
        private long segmentStartedAt;
        private long segmentCpuStartedAt;
        private long segmentAllocatedStartedAt;
        private long segmentThreadId = -1L;
        private long eventCount;
        private String lastEventDetails;
        private String lastFailure;
        private boolean startLogged;
        private boolean finished;

        /**
         * Створює дію, фіксує початкові метрики та за потреби одразу повідомляє про її початок.
         *
         * @param name зрозуміла назва дії.
         * @param details стислий опис початкової події або аргументів.
         * @param logStartImmediately чи потрібно записати початок до вимірювання тривалості.
         */
        private ActionSession(String name, String details, boolean logStartImmediately) {
            this.name = requireNonBlank(name);
            initialDetails = details == null ? "" : details;
            lastEventDetails = initialDetails;
            this.logStartImmediately = logStartImmediately;
            identifier = "A" + Long.toString(IDENTIFIER_SEQUENCE.incrementAndGet(), 36).toUpperCase(Locale.ROOT);
            if (logStartImmediately) {
                logStart();
            }
        }

        /**
         * Повертає короткий ідентифікатор дії для кореляції рядків журналу.
         *
         * @return унікальний у межах запуску ідентифікатор.
         */
        String identifier() {
            return identifier;
        }

        /**
         * Додає відомості про подію до агрегованої дії, не створюючи запис для кожного руху.
         *
         * @param details короткі відомості про останню подію.
         */
        synchronized void addEvent(String details) {
            if (finished) {
                return;
            }
            eventCount++;
            lastEventDetails = details == null ? "" : details;
        }

        /**
         * Зберігає виняток, який виник під час обробки події поза окремим методним викликом.
         *
         * @param failure виняток, що перервав обробку.
         */
        synchronized void recordFailure(Throwable failure) {
            if (failure != null && !finished) {
                lastFailure = failure.getClass().getSimpleName() + ": " + safeFailureMessage(failure);
            }
        }

        /** Реєструє початок одного синхронного сегмента дії у поточному потоці. */
        synchronized void beginSegment() {
            segmentStartedAt = System.nanoTime();
            segmentCpuStartedAt = currentThreadCpuTime();
            segmentAllocatedStartedAt = currentThreadAllocatedBytes();
            segmentThreadId = Thread.currentThread().threadId();
        }

        /** Додає активний час, CPU та обсяг алокацій сегмента до загальних метрик дії. */
        synchronized void endSegment() {
            if (segmentStartedAt == 0L || segmentThreadId != Thread.currentThread().threadId()) {
                return;
            }
            activeNanos += Math.max(0L, System.nanoTime() - segmentStartedAt);
            cpuNanos = addMetric(cpuNanos, difference(currentThreadCpuTime(), segmentCpuStartedAt));
            allocatedBytes = addMetric(
                    allocatedBytes, difference(currentThreadAllocatedBytes(), segmentAllocatedStartedAt));
            segmentStartedAt = 0L;
            segmentThreadId = -1L;
        }

        /**
         * Додає виклик до поточного вузла дерева та починає вимірювання його власного часу.
         *
         * @param methodName сигнатура методу, який виконується.
         * @param arguments безпечне текстове представлення аргументів.
         * @return маркер виконання, який потрібно закрити після виходу з методу.
         */
        synchronized MethodInvocation enterMethod(String methodName, String arguments) {
            if (finished) {
                return null;
            }
            Map<String, MethodNode> siblings = callStack.isEmpty()
                    ? rootMethods
                    : callStack.peek().node.children;
            MethodNode node = siblings.computeIfAbsent(methodName, MethodNode::new);
            node.recordArguments(arguments);
            MethodInvocation invocation = new MethodInvocation(this, node, System.nanoTime());
            callStack.push(invocation);
            return invocation;
        }

        /**
         * Закриває вимірювання методу та зберігає інформацію про виняток, якщо він виник.
         *
         * @param invocation маркер методу, який завершується.
         * @param failure помилка виконання або {@code null}, якщо метод завершився успішно.
         */
        synchronized void exitMethod(MethodInvocation invocation, Throwable failure) {
            if (invocation == null || invocation.closed) {
                return;
            }
            if (callStack.peek() != invocation) {
                lastFailure = "Порушено порядок закриття трасованих методів";
                callStack.remove(invocation);
            } else {
                callStack.pop();
            }
            long elapsed = Math.max(0L, System.nanoTime() - invocation.startedAtNanos);
            invocation.node.recordCompletion(elapsed, failure);
            if (failure != null) {
                lastFailure = failure.getClass().getSimpleName() + ": " + safeFailureMessage(failure);
            }
            invocation.closed = true;
        }

        /** Завершує дію один раз і виводить її дерево викликів та підсумкові метрики. */
        synchronized void finish() {
            if (finished) {
                return;
            }
            endSegment();
            finished = true;
            long wallNanos = Math.max(0L, System.nanoTime() - startedAtNanos);
            boolean shouldLog = logStartImmediately || (!rootMethods.isEmpty()
                    && (wallNanos >= MINIMUM_AUTOMATIC_DURATION_MILLIS * 1_000_000.0
                    || lastFailure != null));
            if (!shouldLog) {
                return;
            }
            if (LOGGER.isInfoEnabled()) {
                long finalHeapBytes = MEMORY_METRICS.getHeapMemoryUsage().getUsed();
                long heapDelta = finalHeapBytes - initialHeapBytes;
                logStart();
                for (MethodNode method : rootMethods.values()) {
                    logMethod(method, 0);
                }
                double cpuPercent = activeNanos == 0L || cpuNanos < 0L
                        ? 0.0
                        : cpuNanos * 100.0 / activeNanos;
                StringBuilder summary = new StringBuilder()
                        .append("час=").append(formatDuration(wallNanos))
                        .append(", активний час=").append(formatDuration(activeNanos))
                        .append(", CPU потоку=").append(formatDuration(cpuNanos));
                if (cpuNanos >= 0L && activeNanos > 0L) {
                    summary.append(String.format(Locale.ROOT, " (%.1f%% активного часу)", cpuPercent));
                }
                summary.append(", алокації потоку=").append(formatBytes(allocatedBytes))
                        .append(", heap процесу=").append(formatBytes(initialHeapBytes))
                        .append(" → ").append(formatBytes(finalHeapBytes))
                        .append(" (Δ ").append(formatSignedBytes(heapDelta)).append(')');
                if (eventCount > 0L) {
                    summary.append(", подій=").append(eventCount);
                    if (!Objects.equals(initialDetails, lastEventDetails)) {
                        summary.append(", остання подія=").append(lastEventDetails);
                    }
                }
                if (lastFailure != null) {
                    summary.append(", помилка=").append(lastFailure);
                }
                LOGGER.info("КІНЕЦЬ: {} - {} | {}", name, identifier, summary);
            }
        }

        /**
         * Повертає рядки дерева викликів для перевірки його структури без читання файла журналу.
         *
         * @return незмінний список вкладених рядків дерева.
         */
        synchronized List<String> methodTraceLines() {
            List<String> lines = new ArrayList<>();
            for (MethodNode node : rootMethods.values()) {
                collectMethodLines(node, 0, lines);
            }
            return List.copyOf(lines);
        }

        /**
         * Повертає загальну кількість зареєстрованих методних викликів разом із вкладеними.
         *
         * @return кількість виконаних викликів.
         */
        synchronized long methodCallCount() {
            return rootMethods.values().stream().mapToLong(MethodNode::totalCalls).sum();
        }

        /**
         * Повертає кількість подій, об'єднаних у поточну дію.
         *
         * @return кількість отриманих подій.
         */
        synchronized long eventCount() {
            return eventCount;
        }

        /**
         * Повертає ознаку завершення дії, щоб перевірити коректність закриття UI-сеансів.
         *
         * @return {@code true}, якщо підсумковий запис уже сформовано.
         */
        synchronized boolean isFinished() {
            return finished;
        }

        /** Виводить стартовий рядок лише один раз, незалежно від способу завершення дії. */
        private void logStart() {
            if (LOGGER.isInfoEnabled()) {
                if (startLogged) {
                    return;
                }
                startLogged = true;
                LOGGER.info("СТАРТ: {} - {} | початок={} | потік={} | {}", name, identifier, startedAt,
                        Thread.currentThread().getName(), initialDetails);
            }
        }

        /** Виводить вузол дерева та всі його нащадки зі збереженням вкладеності викликів. */
        private void logMethod(MethodNode node, int depth) {
            if (LOGGER.isInfoEnabled()) {
                String indentation = "  ".repeat(depth + 1);
                StringBuilder description = new StringBuilder(indentation)
                        .append('[').append(identifier).append("] ")
                        .append(node.methodName)
                        .append(" | викликів=").append(node.calls)
                        .append(" | час сумарно=").append(formatDuration(node.totalNanos))
                        .append(" | максимум=").append(formatDuration(node.maximumNanos))
                        .append(" | аргументи=").append(node.firstArguments);
                if (!Objects.equals(node.firstArguments, node.lastArguments)) {
                    description.append(" → ").append(node.lastArguments);
                }
                if (node.failures > 0L) {
                    description.append(" | помилок=").append(node.failures);
                }
                LOGGER.info(description.toString());
                for (MethodNode child : node.children.values()) {
                    logMethod(child, depth + 1);
                }
            }
        }

        /** Створює незалежний знімок рядків дерева без звернення до логера. */
        private void collectMethodLines(MethodNode node, int depth, List<String> destination) {
            StringBuilder line = new StringBuilder("  ".repeat(depth))
                    .append(node.methodName)
                    .append(" | викликів=").append(node.calls)
                    .append(" | аргументи=").append(node.firstArguments);
            if (node.failures > 0L) {
                line.append(" | помилок=").append(node.failures);
            }
            destination.add(line.toString());
            for (MethodNode child : node.children.values()) {
                collectMethodLines(child, depth + 1, destination);
            }
        }

        /**
         * Перевіряє обов'язкову текстову назву дії.
         *
         * @param value значення, яке потрібно перевірити.
         * @return початкове значення, якщо воно не порожнє.
         */
        private static String requireNonBlank(String value) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
            return value;
        }

        /**
         * Формує безпечне коротке повідомлення винятку без обходу стека бібліотечних викликів.
         *
         * @param failure виняток, який потрібно описати.
         * @return обмежене однорядкове повідомлення.
         */
        private static String safeFailureMessage(Throwable failure) {
            String message = failure.getMessage();
            if (message == null || message.isBlank()) {
                return "без додаткового опису";
            }
            return sanitizeText(message.length() > MAXIMUM_STRING_LENGTH
                    ? message.substring(0, MAXIMUM_STRING_LENGTH) + "…"
                    : message);
        }
    }

    /** Вимірює один методний виклик та гарантує відновлення батьківського вузла дерева. */
    public static final class MethodInvocation {

        private final ActionSession session;
        private final MethodNode node;
        private final long startedAtNanos;
        private boolean closed;
        private Throwable failure;

        /**
         * Створює маркер виклику та прив'язує його до сесії, що володіє деревом методів.
         *
         * @param session контекст дії, що збирає виклики.
         * @param node вузол дерева відповідної сигнатури методу.
         * @param startedAtNanos момент початку вимірювання.
         */
        private MethodInvocation(ActionSession session, MethodNode node, long startedAtNanos) {
            this.session = session;
            this.node = node;
            this.startedAtNanos = startedAtNanos;
        }

        /**
         * Позначає виняток, який потрібно відобразити у звіті дії.
         *
         * @param throwable виняток, що виник у методі.
         */
        public void failed(Throwable throwable) {
            failure = throwable;
        }

        /** Завершує виклик і додає його вимірювання до відповідного вузла дерева. */
        public void close() {
            session.exitMethod(this, failure);
        }
    }

    /** Керує активністю контексту в потоці та завершує автоматично створену дію за потреби. */
    public static final class SessionBinding implements AutoCloseable {

        private final ActionSession session;
        private final ActionSession previousSession;
        private final boolean finishWhenClosed;
        private boolean closed;

        /**
         * Активує контекст у потоці та починає вимірювання чергового сегмента.
         *
         * @param session контекст дії, який потрібно активувати.
         * @param finishWhenClosed чи має закриття прив'язки завершити всю дію.
         */
        private SessionBinding(ActionSession session, boolean finishWhenClosed) {
            this.session = session;
            previousSession = CURRENT_SESSION.get();
            this.finishWhenClosed = finishWhenClosed;
            CURRENT_SESSION.set(session);
            session.beginSegment();
        }

        /** Закриває сегмент, відновлює попередній контекст і завершує власну дію за потреби. */
        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            session.endSegment();
            if (previousSession == null) {
                CURRENT_SESSION.remove();
            } else {
                CURRENT_SESSION.set(previousSession);
            }
            if (finishWhenClosed) {
                session.finish();
            }
        }
    }

    /** Збирає статистику повторних викликів одного методу та зберігає вкладені дочірні вузли. */
    private static final class MethodNode {

        private final String methodName;
        private final Map<String, MethodNode> children = new LinkedHashMap<>();
        private long calls;
        private long failures;
        private long totalNanos;
        private long maximumNanos;
        private String firstArguments = "[]";
        private String lastArguments = "[]";

        /**
         * Створює вузол для сигнатури методу, яка з'явилася в поточному контексті.
         *
         * @param methodName сигнатура методу, яку представляє вузол.
         */
        private MethodNode(String methodName) {
            this.methodName = methodName;
        }

        /**
         * Зберігає перший та останній набір аргументів без накопичення дублікатів для циклів.
         *
         * @param arguments безпечне представлення аргументів поточного виклику.
         */
        private void recordArguments(String arguments) {
            if (calls == 0L) {
                firstArguments = arguments;
            }
            lastArguments = arguments;
            calls++;
        }

        /**
         * Додає включну тривалість і результат одного завершеного виклику.
         *
         * @param elapsed тривалість виконання в наносекундах.
         * @param failure виняток або {@code null}, якщо виклик завершився успішно.
         */
        private void recordCompletion(long elapsed, Throwable failure) {
            totalNanos += elapsed;
            maximumNanos = Math.max(maximumNanos, elapsed);
            if (failure != null) {
                failures++;
            }
        }

        /** Повертає кількість викликів разом із дочірніми вузлами. */
        private long totalCalls() {
            return calls + children.values().stream().mapToLong(MethodNode::totalCalls).sum();
        }
    }
}
