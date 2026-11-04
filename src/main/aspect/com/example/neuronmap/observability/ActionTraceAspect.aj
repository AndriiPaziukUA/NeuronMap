package com.example.neuronmap.observability;

import org.aspectj.lang.reflect.MethodSignature;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Наскрізний аспект, який реєструє виконання прикладних методів у контексті поточної дії.
 *
 * Використовуються окремі before/after advice замість around advice: це не змінює
 * сигнатури checked exceptions методів, що трасуються, і не додає їм throws Throwable.
 */
public aspect ActionTraceAspect {

    private static final ThreadLocal<Deque<TraceFrame>> INVOCATIONS = new ThreadLocal<>();

    pointcut applicationMethodExecution():
            execution(* com.example.neuronmap..*.*(..))
                    && !execution(* com.example.neuronmap..*.main(..))
                    && !within(com.example.neuronmap.observability..*);

    /**
     * Реєструє початок методу та відкриває автоматичну дію, якщо виклик не належить
     * до вже активної дії користувача.
     */
    before(): applicationMethodExecution() {
        if (!ActionTraceContext.isEnabled()) {
            return;
        }

        MethodSignature signature = (MethodSignature) thisJoinPoint.getSignature();
        StringBuilder methodNameBuilder = new StringBuilder(signature.getDeclaringTypeName())
                .append('.')
                .append(signature.getName())
                .append('(');
        Class<?>[] parameterTypes = signature.getParameterTypes();
        for (int index = 0; index < parameterTypes.length; index++) {
            if (index > 0) {
                methodNameBuilder.append(", ");
            }
            methodNameBuilder.append(parameterTypes[index].getSimpleName());
        }

        String methodName = methodNameBuilder.append(')').toString();
        org.aspectj.lang.reflect.SourceLocation sourceLocation = thisJoinPoint.getSourceLocation();
        if (sourceLocation != null && sourceLocation.getLine() > 0) {
            methodName += " [" + sourceLocation.getFileName() + ":" + sourceLocation.getLine() + "]";
        }

        Object[] arguments = thisJoinPoint.getArgs();
        ActionTraceContext.SessionBinding automaticBinding = null;
        if (ActionTraceContext.currentSession() == null) {
            TraceAction traceAction = signature.getMethod().getAnnotation(TraceAction.class);
            automaticBinding = traceAction == null
                    ? ActionTraceContext.beginAutomatic(methodName, arguments)
                    : ActionTraceContext.beginNamedAction(traceAction.value(), arguments);
        }

        ActionTraceContext.MethodInvocation invocation =
                ActionTraceContext.enterMethod(methodName, arguments);

        Deque<TraceFrame> stack = INVOCATIONS.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            INVOCATIONS.set(stack);
        }
        stack.push(new TraceFrame(invocation, automaticBinding));
    }

    /** Завершує метод, який повернувся нормально. */
    after() returning: applicationMethodExecution() {
        completeInvocation(null);
    }

    /**
     * Завершує метод, який викинув помилку, фіксуючи її в дереві.
     * Advice не перехоплює і не змінює початковий виняток.
     */
    after() throwing(Throwable failure): applicationMethodExecution() {
        completeInvocation(failure);
    }

    private static void completeInvocation(Throwable failure) {
        Deque<TraceFrame> stack = INVOCATIONS.get();
        if (stack == null || stack.isEmpty()) {
            INVOCATIONS.remove();
            return;
        }

        TraceFrame frame = stack.pop();
        try {
            if (failure != null && frame.invocation != null) {
                frame.invocation.failed(failure);
            }
            if (frame.invocation != null) {
                frame.invocation.close();
            }
        } finally {
            try {
                if (frame.automaticBinding != null) {
                    frame.automaticBinding.close();
                }
            } finally {
                if (stack.isEmpty()) {
                    INVOCATIONS.remove();
                }
            }
        }
    }

    /** Парний стан одного method-execution join point на поточному потоці. */
    private static final class TraceFrame {

        private final ActionTraceContext.MethodInvocation invocation;
        private final ActionTraceContext.SessionBinding automaticBinding;

        private TraceFrame(
                ActionTraceContext.MethodInvocation invocation,
                ActionTraceContext.SessionBinding automaticBinding) {
            this.invocation = invocation;
            this.automaticBinding = automaticBinding;
        }
    }
}
