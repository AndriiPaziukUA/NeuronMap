package com.example.neuronmap.observability;

import org.junit.jupiter.api.Test;

import java.io.Serial;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє ідентифікацію дій, побудову дерева викликів і безпечне представлення аргументів. */
class ActionTraceContextTest {

    /** Перевіряє, що явно позначена дія журналюється незалежно від її тривалості. */
    @Test
    void bindsExplicitNamedAction() {
        ActionTraceContext.SessionBinding binding = ActionTraceContext.beginNamedAction(
                "Такт симуляції", new Object[]{7});
        ActionTraceContext.ActionSession session = ActionTraceContext.currentSession();
        assertNotNull(session);
        try (binding) {
            ActionTraceContext.MethodInvocation invocation = ActionTraceContext.enterMethod(
                    "SimulationSession.tick(int)", new Object[]{7});
            assertNotNull(invocation);
            invocation.close();
        }

        assertNull(ActionTraceContext.currentSession());
        assertEquals(1L, session.methodCallCount());
        session.finish();
    }

    /** Перевіряє, що незалежні дії отримують короткі ідентифікатори та завершуються лише один раз. */
    @Test
    void assignsDistinctShortIdentifiersToActions() {
        ActionTraceContext.ActionSession first = ActionTraceContext.startEvent("Перша дія", "джерело=test");
        ActionTraceContext.ActionSession second = ActionTraceContext.startEvent("Друга дія", "джерело=test");

        assertNotEquals(first.identifier(), second.identifier());
        assertTrue(first.identifier().matches("A[0-9A-Z]+"));
        assertTrue(first.identifier().length() <= 8);

        first.finish();
        first.finish();
        second.finish();
        assertEquals(0L, first.methodCallCount());
    }

    /** Перевіряє вкладеність методів та те, що складні об'єкти не серіалізуються через {@code toString()}. */
    @Test
    void recordsNestedCallsWithoutExpandingObjectState() {
        ActionTraceContext.ActionSession session = ActionTraceContext.startEvent("Перевірка дерева", "test");
        FakePayload payload = new FakePayload();

        try (ActionTraceContext.SessionBinding ignored = ActionTraceContext.bind(session)) {
            ActionTraceContext.MethodInvocation parent = ActionTraceContext.enterMethod(
                    "ExampleCoordinator.run(FakePayload)", new Object[]{payload});
            assertNotNull(parent);
            ActionTraceContext.MethodInvocation child = ActionTraceContext.enterMethod(
                    "ExampleService.process(FakePayload)", new Object[]{payload});
            assertNotNull(child);
            child.close();
            parent.close();
        }

        List<String> lines = session.methodTraceLines();
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).contains("ExampleCoordinator.run(FakePayload)"));
        assertTrue(lines.get(1).startsWith("  ExampleService.process(FakePayload)"));
        assertTrue(lines.get(0).contains("FakePayload"));
        assertFalse(lines.toString().contains("privateMarker"));
        assertEquals(0, payload.toStringCalls());
        assertEquals(2L, session.methodCallCount());
        session.finish();
    }

    /** Перевіряє агрегацію однакових вкладених викликів без створення дубльованих вузлів дерева. */
    @Test
    void aggregatesRepeatedCallsAtTheSameCallSite() {
        ActionTraceContext.ActionSession session = ActionTraceContext.startEvent("Повторні виклики", "test");
        try (ActionTraceContext.SessionBinding ignored = ActionTraceContext.bind(session)) {
            ActionTraceContext.MethodInvocation parent = ActionTraceContext.enterMethod("Parent.execute()", null);
            assertNotNull(parent);
            for (int index = 0; index < 3; index++) {
                ActionTraceContext.MethodInvocation child = ActionTraceContext.enterMethod(
                        "Child.calculate(int)", new Object[]{index});
                assertNotNull(child);
                child.close();
            }
            parent.close();
        }

        List<String> lines = session.methodTraceLines();
        assertEquals(2, lines.size());
        assertTrue(lines.get(1).contains("викликів=3"));
        assertEquals(4L, session.methodCallCount());
        session.finish();
    }

    /** Перевіряє, що виняток залишається видимим у дереві, а сам виклик закривається коректно. */
    @Test
    void retainsMethodFailureInTrace() {
        ActionTraceContext.ActionSession session = ActionTraceContext.startEvent("Помилкова дія", "test");
        try (ActionTraceContext.SessionBinding ignored = ActionTraceContext.bind(session)) {
            ActionTraceContext.MethodInvocation invocation = ActionTraceContext.enterMethod(
                    "ExampleService.fail()", null);
            assertNotNull(invocation);
            invocation.failed(new IllegalStateException("очікувана помилка"));
            invocation.close();
        }

        assertTrue(session.methodTraceLines().getFirst().contains("помилок=1"));
        session.finish();
    }

    /** Перевіряє вкладене тимчасове зв'язування контекстів та відновлення попередньої дії. */
    @Test
    void restoresPreviousSessionAfterNestedBinding() {
        ActionTraceContext.ActionSession outer = ActionTraceContext.startEvent("Зовнішня дія", "test");
        ActionTraceContext.ActionSession inner = ActionTraceContext.startEvent("Внутрішня дія", "test");

        try (ActionTraceContext.SessionBinding outerBinding = ActionTraceContext.bind(outer)) {
            assertNotNull(outerBinding);
            assertEquals(outer, ActionTraceContext.currentSession());
            try (ActionTraceContext.SessionBinding innerBinding = ActionTraceContext.bind(inner)) {
                assertNotNull(innerBinding);
                assertEquals(inner, ActionTraceContext.currentSession());
            }
            assertEquals(outer, ActionTraceContext.currentSession());
        }

        assertNull(ActionTraceContext.currentSession());
        outer.finish();
        inner.finish();
    }

    /** Перевіряє відхилення порожніх назв дій і підтримку простих та порожніх наборів аргументів. */
    @Test
    void validatesActionNamesAndSummarizesSimpleArguments() {
        assertThrows(IllegalArgumentException.class, () -> ActionTraceContext.startEvent(" ", "test"));
        assertEquals("[]", ActionTraceContext.summarizeArguments(null));
        assertEquals("[null, 42, true, Direction.NORTH]",
                ActionTraceContext.summarizeArguments(new Object[]{null, 42, true, Direction.NORTH}));
        FakeNumber customNumber = new FakeNumber();
        assertEquals("[FakeNumber]", ActionTraceContext.summarizeArguments(new Object[]{customNumber}));
        assertEquals(0, customNumber.toStringCalls());
        assertNotNull(ActionTraceContext.summarizeArguments(new Object[]{new FakePayload()}));
    }

    /** Невеликий об'єкт із маркером, який дозволяє виявити небажаний виклик {@code toString()}. */
    private static final class FakePayload {

        private int toStringCalls;

        /** Повертає кількість звернень до повного текстового представлення об'єкта. */
        private int toStringCalls() {
            return toStringCalls;
        }

        /** Збільшує лічильник, щоб тест міг виявити серіалізацію складного аргументу. */
        @Override
        public String toString() {
            toStringCalls++;
            return "privateMarker";
        }
    }

    /** Числовий об'єкт із нестандартним текстовим представленням для перевірки безпечного формату аргументів. */
    private static final class FakeNumber extends Number {

        @Serial
        private static final long serialVersionUID = 1L;
        private int toStringCalls;

        /** Повертає нульове ціле значення для контракту {@link Number}. */
        @Override
        public int intValue() {
            return 0;
        }

        /** Повертає нульове довге значення для контракту {@link Number}. */
        @Override
        public long longValue() {
            return 0L;
        }

        /** Повертає нульове число з плаваючою точкою для контракту {@link Number}. */
        @Override
        public float floatValue() {
            return 0.0f;
        }

        /** Повертає нульове число подвійної точності для контракту {@link Number}. */
        @Override
        public double doubleValue() {
            return 0.0;
        }

        /** Фіксує виклик текстового представлення, щоб тест виявив розкриття об'єкта. */
        @Override
        public String toString() {
            toStringCalls++;
            return "private numeric state";
        }

        /** Повертає кількість небажаних запитів текстового представлення. */
        private int toStringCalls() {
            return toStringCalls;
        }
    }

    /** Значення переліку для перевірки компактного представлення аргументів. */
    private enum Direction {
        NORTH
    }
}
